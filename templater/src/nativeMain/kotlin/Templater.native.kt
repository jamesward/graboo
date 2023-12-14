import okio.FileSystem
import okio.Path

actual suspend fun writeFilesToDir(files: Map<Path, FileContents>, dir: Path) {
    files.forEach { (path, fileContents) ->
        val filePath = dir / path

        filePath.parent?.let {
            FileSystem.SYSTEM.createDirectories(it)
        }

        FileSystem.SYSTEM.write(filePath) {
            writeUtf8(fileContents.s)
        }

        if (fileContents.setExec) {
            makeExecutable(filePath)
        }
    }
}