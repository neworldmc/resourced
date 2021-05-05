package site.neworld.storaged

import site.neworld.utils.AsyncClosable
import java.nio.ByteBuffer

interface File: AsyncClosable {
    suspend fun read(offset: Int, buffer: ByteBuffer): ByteBuffer
    suspend fun write(offset: Int, buffer: ByteBuffer): ByteBuffer
    suspend fun read(offset: Int, size: Int) = read(offset, ByteBuffer.allocate(size))
}