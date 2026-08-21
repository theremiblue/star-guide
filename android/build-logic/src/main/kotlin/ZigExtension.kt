// TODO: note that ABI selection is handled by zig
//       for now it generates all supported ones.
// TODO: too verbose with properties, investigate how to make more readable
//       in other layers too...
open class ZigExtension {
    var projectDir: String? = null
    var executable: String = "zig"
    var extraArgs: List<String> = emptyList()

    fun projectDir(path: String) {
        projectDir = path
    }
}
