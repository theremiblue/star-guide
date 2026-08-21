plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
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
