import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path

import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
actual fun isExecutable(path: Path): Boolean = run {
    val mode = memScoped {
        val statBuf = alloc<stat>()
        // todo: throw on -1 ?
        stat(path.toString(), statBuf.ptr)
        statBuf.st_mode
    }

    (mode and 0b001_001_001u) > 0u
}