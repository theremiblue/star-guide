plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.zig.android)
}

val appNamespace = "com.raydrivers.starguide"

val androidApiLevel = AndroidVersions.minSdk(project)
val javaVersion = AndroidVersions.java(project)

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
    }

    jvmToolchain(javaVersion)
}

zig {
    projectDir("../core")
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
}
