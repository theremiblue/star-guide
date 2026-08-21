/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

object AndroidVersions {
    fun compileSdk(project: Project): Int =
        project.androidVersion("compileSdk").toInt()

    fun minSdk(project: Project): Int =
        project.androidVersion("minSdk").toInt()

    fun targetSdk(project: Project): Int =
        project.androidVersion("targetSdk").toInt()

    fun ndk(project: Project): String =
        project.androidVersion("ndk")

    fun java(project: Project): Int =
        project.androidVersion("java").toInt()

    fun appVersionCode(project: Project): Int =
        project.androidVersion("appVersionCode").toInt()

    fun appVersionName(project: Project): String =
        project.androidVersion("appVersionName")
}

private fun Project.androidVersion(name: String): String {
    val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)

    return catalogs
        .named("androidVersions")
        .findVersion(name)
        .get()
        .requiredVersion
}
