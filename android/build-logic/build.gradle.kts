import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

val javaVersion = extensions.getByType<VersionCatalogsExtension>()
    .named("androidVersions")
    .findVersion("java")
    .get()
    .requiredVersion
    .toInt()

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    implementation(libs.android.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        register("zigAndroid") {
            id = "com.raydrivers.zig-android"
            implementationClass = "ZigAndroidPlugin"
        }
    }
}
