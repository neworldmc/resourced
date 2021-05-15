@file:Suppress("BlockingMethodInNonBlockingContext")
package site.neworld.storaged

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import site.neworld.utils.AResource
import site.neworld.utils.AsyncClosable
import site.neworld.utils.multiClose
import site.neworld.utils.safeInit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

@Suppress("unused")
fun tableRegistry(path: String): Table = RegistryTable(path)

private val NONE = byteArrayOf()
private const val REGISTRY_HEADER_SIZE = 16

private class RegistryTable(path: String) : ATable() {
    private val content = Registry(path)
    private val opened = HashMap<Long, Entry>()

    private class TableEntryFinalization : Throwable()

    inner class Entry(private val node: Long) : AsyncClosable {
        private var bytes = AtomicReference(NONE)
        private val refCount = AtomicInteger(0)
        private val writeLock = Mutex()
        private val flagInit: Deferred<Unit> = GlobalScope.async(Dispatchers.Unconfined) {
            val fetch = content.get(node)
            if (fetch != null) bytes.set(fetch)
        }
        private val flagFinal = CompletableDeferred<Unit>()

        private fun borrow() {
            if (refCount.getAndIncrement() < 0) throw TableEntryFinalization()
        }

        private fun release() {
            if (refCount.decrementAndGet() == 0) {
                GlobalScope.launch {
                    delay(1000)
                    if (refCount.compareAndSet(0, Int.MIN_VALUE)) {
                        relNode(node)
                        flagFinal.complete(Unit)
                    }
                }
            }
        }

        suspend fun stat(): FileStat? {
            borrow()
            flagInit.await()
            return when (val arr = bytes.get()) {
                NONE -> null
                else -> DataInputStream(ByteArrayInputStream(arr)).let {
                    FileStat((arr.size - REGISTRY_HEADER_SIZE).toLong(), it.readLong(), it.readLong())
                }
            }.also { release() }
        }

        suspend fun delete(timeout: Long) {
            try {
                borrow()
                flagInit.await()
                withTimeout(timeout) { writeLock.lock(this) }
                bytes.set(NONE)
                content.remove(node)
            } finally {
                if (writeLock.holdsLock(this)) {
                    writeLock.unlock(this)
                }
                release()
            }
        }

        suspend fun openRead(): File {
            borrow()
            flagInit.await()
            return when (val arr = bytes.get()) {
                NONE -> notFound(this@RegistryTable, node)
                else -> {
                    object : AReadOnlyFile() {
                        override suspend fun read(offset: Int, buffer: ByteBuffer) = buffer.put(
                            arr, offset + REGISTRY_HEADER_SIZE,
                            min(buffer.remaining(), arr.size - REGISTRY_HEADER_SIZE)
                        )

                        override suspend fun closeAsync() = release()
                    }
                }
            }
        }

        suspend fun openWrite(timeout: Long): File {
            borrow()
            flagInit.await()
            val obj = object : AFile() {
                private var arr = NONE

                suspend fun acquire() = this.also {
                    writeLock.lock(this)
                    val host = bytes.get()
                    if (host === NONE) {
                        arr = ByteArrayOutputStream(REGISTRY_HEADER_SIZE).also {
                            DataOutputStream(it).apply {
                                writeLong(System.currentTimeMillis())
                                writeLong(System.currentTimeMillis())
                            }
                        }.toByteArray()
                        flush()
                    } else arr = host.clone()
                }

                override suspend fun read(offset: Int, buffer: ByteBuffer) = buffer.put(
                    arr, offset + REGISTRY_HEADER_SIZE,
                    min(buffer.remaining(), arr.size - REGISTRY_HEADER_SIZE)
                )

                override suspend fun write(offset: Int, buffer: ByteBuffer): ByteBuffer {
                    ByteBuffer.wrap(arr).also {
                        it.putLong(1, System.currentTimeMillis())
                        it.position(offset + REGISTRY_HEADER_SIZE).put(buffer)
                    }
                    return buffer
                }

                override suspend fun flush() {
                    if (bytes.getAndSet(arr) !== arr) {
                        content.set(node, arr)
                    }
                }

                override suspend fun closeAsync() {
                    if (writeLock.holdsLock(this)) {
                        flush()
                        writeLock.unlock(this)
                    }
                    release()
                }
            }
            return obj.safeInit { withTimeout(timeout) { acquire() } }
        }

        override suspend fun closeAsync() = flagFinal.await()
    }

    private fun getNode(node: Long) = synchronized(opened) { opened.getOrPut(node) { Entry(node) } }

    private fun relNode(node: Long) = synchronized(opened) { opened.remove(node) }

    override suspend fun init() {}

    private inline fun <T> nullOnEntryFinalization(block: () -> T) = try {
        block()
    } catch (e: TableEntryFinalization) {
        null
    }

    override tailrec suspend fun stat(node: Long): FileStat? {
        return nullOnEntryFinalization { getNode(node).stat() } ?: stat(node)
    }

    override tailrec suspend fun open(node: Long, write: Boolean, timeout: Long): File {
        return nullOnEntryFinalization {
            val entry = getNode(node)
            if (write) entry.openWrite(timeout) else entry.openRead()
        } ?: open(node, write, timeout)
    }

    override tailrec suspend fun delete(node: Long, timeout: Long) {
        return nullOnEntryFinalization { getNode(node).delete(timeout) } ?: delete(node, timeout)
    }

    override suspend fun closeAsync() {
        multiClose(*synchronized(opened) { opened.values.toTypedArray().also { opened.clear() } })
        content.close()
    }
}