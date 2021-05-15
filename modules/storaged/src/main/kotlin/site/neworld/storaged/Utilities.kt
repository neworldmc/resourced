package site.neworld.storaged

import site.neworld.utils.AResource
import java.io.IOException
import java.nio.ByteBuffer

fun notFound(base: Any, node: Long): Nothing =
    throw IOException("Node $node from base $base is not found")

fun notAllowed(): Nothing =
    throw IllegalStateException("Operation not allowed with current setting")

internal abstract class ATable : AResource(), Table {
    abstract suspend fun init()
}

internal abstract class AFile: AResource(), File

internal abstract class AReadOnlyFile : AFile() {
    override suspend fun write(offset: Int, buffer: ByteBuffer) = notAllowed()
    override suspend fun flush() = notAllowed()
}