import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class ZigCoreBuildAndroidTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    abstract val zigProjectDirectory: DirectoryProperty

    @get:Internal
    abstract val androidNdkDirectory: DirectoryProperty

    @get:Internal
    abstract val androidSdkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty

    @get:Input
    abstract val androidApiLevel: Property<Int>

    init {
        // Do a clean rebuild every time
        // Checking zig build cache is too much overhead
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        val ndkDir = androidNdkDirectory.get().asFile
        val sdkDir = androidSdkDirectory.get().asFile
        val zigDir = zigProjectDirectory.get().asFile
        val jniLibsDir = jniLibsDirectory.get().asFile

        // We don't touch zig cache,
        // but force zig to rebuild artifacts
        zigDir.resolve("zig-out").deleteRecursively()
        jniLibsDir.deleteRecursively()
        jniLibsDir.mkdirs()

        logger.lifecycle(
            "zig build --prefix {} -Dandroid-ndk={} -Dandroid-api={}",
            jniLibsDir.absolutePath,
            ndkDir.absolutePath,
            androidApiLevel.get(),
        )

        execOperations.exec {
            workingDir = zigDir
            executable = "zig"

            args(
                "build",
                "--prefix", jniLibsDir.absolutePath,
                "-Dandroid-ndk=${ndkDir.absolutePath}",
                "-Dandroid-api=${androidApiLevel.get()}",
            )

            environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
            environment("ANDROID_HOME", sdkDir.absolutePath)
            environment("ANDROID_SDK_ROOT", sdkDir.absolutePath)
            standardOutput = System.out
            errorOutput = System.err
        }
    }
}
