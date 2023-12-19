import okio.FileSystem
import okio.Path

actual fun makeExecutable(path: Path) {
    path.toFile().setExecutable(true)
}

actual fun isExecutable(path: Path): Boolean =
    path.toFile().canExecute()
