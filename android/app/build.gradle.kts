/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.zig.android)
}

val appNamespace = "com.raydrivers.starguide"

val generatedLicenseAssetsDir = layout.buildDirectory.dir("generated/licenseAssets/main")
val syncLicenseAssets = tasks.register<AppLicenseAssetsTask>("syncLicenseAssets") {
    copyingFile.set(layout.projectDirectory.file("../../COPYING"))
    resourcesDirectory.set(layout.projectDirectory.dir("resources"))
    outputDirectory.set(generatedLicenseAssetsDir)
}

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

androidComponents {
    onVariants { variant ->
        val assets = variant.sources.assets
            ?: error("variant.sources.assets is not available")

        assets.addGeneratedSourceDirectory(
            syncLicenseAssets,
            AppLicenseAssetsTask::outputDirectory,
        )
    }
}
