import okio.Path

// bug: the extension function causes issues on posix - the stat returns -1 for the file
// expect fun Path.isExecutable(): Boolean

expect fun isExecutable(path: Path): Boolean