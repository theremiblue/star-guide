import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

data class ZigConfig(
    val projectDirectory: File,
    val executable: String,
    val extraArgs: List<String>,
)

data class AndroidConfig(
    val androidNdkDirectory: File,
    val androidSdkDirectory: File,
)

data class AndroidVariantConfig(
    val name: String,
    val jniLibsDirectory: File,
    val androidApiLevel: Int,
)

abstract class ZigCoreBuildAndroidTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    lateinit var zigProjectDirectory: File

    @get:Input
    lateinit var zigExecutable: String

    @get:Input
    var extraArgs: List<String> = emptyList()

    @get:Internal
    lateinit var androidNdkDirectory: File

    @get:Internal
    lateinit var androidSdkDirectory: File

    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty

    @get:Input
    var androidApiLevel: Int = 0

    fun initialize(
        zigConfig: ZigConfig,
        androidConfig: AndroidConfig,
        variantConfig: AndroidVariantConfig,
    ) {
        zigProjectDirectory = zigConfig.projectDirectory
        zigExecutable = zigConfig.executable
        extraArgs = zigConfig.extraArgs

        androidNdkDirectory = androidConfig.androidNdkDirectory
        androidSdkDirectory = androidConfig.androidSdkDirectory

        jniLibsDirectory.set(variantConfig.jniLibsDirectory)
        androidApiLevel = variantConfig.androidApiLevel
    }

    @TaskAction
    fun build() {
        val zigDir = zigProjectDirectory
        val ndkDir = androidNdkDirectory
        val sdkDir = androidSdkDirectory
        val jniLibsDir = jniLibsDirectory.get().asFile

        val args = mutableListOf(
            "build",
            "--prefix", jniLibsDir.absolutePath,
            "-Dandroid-ndk=${ndkDir.absolutePath}",
            "-Dandroid-api=$androidApiLevel",
        )
        args += extraArgs

        zigDir.resolve("zig-out").deleteRecursively()
        jniLibsDir.deleteRecursively()
        jniLibsDir.mkdirs()

        logger.lifecycle(
            "{} {}",
            zigExecutable,
            args.joinToString(" "),
        )

        execOperations.exec {
            workingDir = zigDir
            executable = zigExecutable
            args(args)
            environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
            environment("ANDROID_HOME", sdkDir.absolutePath)
            environment("ANDROID_SDK_ROOT", sdkDir.absolutePath)
            standardOutput = System.out
            errorOutput = System.err
        }
    }
}
