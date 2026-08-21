plugins {
    alias(libs.plugins.android.application)
}

val appNamespace = "com.raydrivers.starguide"

val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
val androidApiLevel = AndroidVersions.minSdk(project)
val zigJniLibsDirectory = layout.buildDirectory.dir("generated/zig/jniLibs")
val zigJniLibsPath = zigJniLibsDirectory.get().asFile.path

val javaVersion = AndroidVersions.java(project)

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
    }

    jvmToolchain(javaVersion)
}

android {
    namespace = appNamespace

    compileSdk = AndroidVersions.compileSdk(project)

    ndkVersion = AndroidVersions.ndk(project)

    defaultConfig {
        applicationId = appNamespace

        minSdk = androidApiLevel
        targetSdk = AndroidVersions.targetSdk(project)

        versionCode = AndroidVersions.appVersionCode(project)
        versionName = AndroidVersions.appVersionName(project)
    }

    compileOptions {
        val jvm = JavaVersion.toVersion(javaVersion)
        sourceCompatibility = jvm
        targetCompatibility = jvm
    }
    
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        error += "NewApi"
    }

    sourceSets.getByName("main").jniLibs.directories.clear()
    sourceSets.getByName("main").jniLibs.directories.add(zigJniLibsPath)
}

registerZigCoreBuild(
    androidNdkDirectory = androidComponents.sdkComponents.ndkDirectory,
    androidSdkDirectory = androidComponents.sdkComponents.sdkDirectory,
    jniLibsDirectory = zigJniLibsDirectory,
    androidApiLevel = androidApiLevel,
)
