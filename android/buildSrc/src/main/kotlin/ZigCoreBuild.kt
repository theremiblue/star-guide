import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

fun Project.registerZigCoreBuild(
    androidNdkDirectory: Provider<Directory>,
    androidSdkDirectory: Provider<Directory>,
    jniLibsDirectory: Provider<Directory>,
    androidApiLevel: Int,
): TaskProvider<ZigCoreBuildAndroidTask> {
    val zigCoreBuild = tasks.register<ZigCoreBuildAndroidTask>("zigCoreBuild") {
        group = "zig"
        description = "Build Zig native library for Android"

        this.androidNdkDirectory.set(androidNdkDirectory)
        this.androidSdkDirectory.set(androidSdkDirectory)
        this.jniLibsDirectory.set(jniLibsDirectory)
        zigProjectDirectory.set(
            project.rootProject.layout.projectDirectory.dir("../core"),
        )
        this.androidApiLevel.set(androidApiLevel)
    }

    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(zigCoreBuild)
    }

    return zigCoreBuild
}
