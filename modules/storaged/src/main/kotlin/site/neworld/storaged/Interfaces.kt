package site.neworld.storaged

import site.neworld.utils.AsyncClosable
import java.nio.ByteBuffer

data class FileStat(
    val size: Long,
    val creation: Long,
    val modification: Long
)

sealed interface File : AsyncClosable {
    suspend fun read(offset: Int, buffer: ByteBuffer): ByteBuffer
    suspend fun read(offset: Int, size: Int) = read(offset, ByteBuffer.allocate(size))
    suspend fun write(offset: Int, buffer: ByteBuffer): ByteBuffer
    suspend fun flush()
}

sealed interface Table : AsyncClosable {
    suspend fun stat(node: Long): FileStat?
    suspend fun open(node: Long, write: Boolean = false, timeout: Long = Long.MAX_VALUE): File
    suspend fun delete(node: Long, timeout: Long)
}
