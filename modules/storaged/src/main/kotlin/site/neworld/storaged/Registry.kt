package site.neworld.storaged

import org.rocksdb.Options
import org.rocksdb.RocksDB
import site.neworld.utils.AResource
import site.neworld.utils.multiClose
import site.neworld.utils.safeStart

internal class Registry(path: String) : AResource() {
    private val options: Options
    private val database: RocksDB

    private fun encodeKey(k: Long) = byteArrayOf(
        (k ushr 56).toByte(), (k ushr 48).toByte(), (k ushr 40).toByte(), (k ushr 32).toByte(),
        (k ushr 24).toByte(), (k ushr 16).toByte(), (k ushr 8).toByte(), k.toByte()
    )

    fun get(key: Long): ByteArray? {
        return database.get(encodeKey(key))
    }

    fun set(key: Long, data: ByteArray) {
        database.put(encodeKey(key), data)
    }

    fun remove(key: Long) {
        database.delete(encodeKey(key))
    }

    override fun close() {
        multiClose(database, options)
    }

    init {
        safeStart {
            options = use(Options().setCreateIfMissing(true))
            database = use(RocksDB.open(options, path))
        }
    }
}