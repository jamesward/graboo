import okio.Path

import platform.posix.chmod

actual fun makeExecutable(path: Path) {
    val mode = 0b111_111_111u
    chmod(path.toString(), mode)
}