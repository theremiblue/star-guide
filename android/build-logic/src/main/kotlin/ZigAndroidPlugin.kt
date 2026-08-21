/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

class ZigAndroidPlugin : Plugin<Project> {
    private fun validateExtension(
        target: Project,
        extension: ZigExtension,
    ) {
        val projectDir = extension.projectDir
        check(!projectDir.isNullOrBlank()) {
            "zig.projectDir(...) must be set"
        }
        check(extension.executable.isNotBlank()) {
            "zig.executable must not be blank"
        }
        check(extension.extraArgs.none(String::isBlank)) {
            "zig.extraArgs must not contain blank values"
        }

        val projectDirectory =
            target.rootProject.layout.projectDirectory.dir(projectDir).asFile
        check(projectDirectory.isDirectory) {
            "zig.projectDir(...) must point to an existing directory: $projectDirectory"
        }
    }

    override fun apply(target: Project) {
        val extension = target.extensions.create("zig", ZigExtension::class.java)

        target.plugins.withId("com.android.application") {
            configureAndroid(
                target,
                extension,
                target.extensions.getByType<ApplicationAndroidComponentsExtension>(),
            )
        }
        target.plugins.withId("com.android.library") {
            configureAndroid(
                target,
                extension,
                target.extensions.getByType<LibraryAndroidComponentsExtension>(),
            )
        }
    }

    private fun configureAndroid(
        target: Project,
        extension: ZigExtension,
        androidComponentsExtension: AndroidComponentsExtension<*, *, *>,
    ) = androidComponentsExtension.onVariants { variant ->
        validateExtension(target, extension)

        val androidConfig = createAndroidConfig(androidComponentsExtension)
        val zigConfig = createZigConfig(target, extension)
        val variantConfig = createVariantConfig(target, variant)

        val zigBuildTask = registerBuildTask(
            target = target,
            zigConfig = zigConfig,
            androidConfig = androidConfig,
            variantConfig = variantConfig,
        )

        addGeneratedJniLibs(variant, zigBuildTask)
    }

    private fun createAndroidConfig(
        androidComponentsExtension: AndroidComponentsExtension<*, *, *>,
    ): AndroidConfig {
        val sdkComponents = androidComponentsExtension.sdkComponents

        return AndroidConfig(
            androidNdkDirectory = sdkComponents.ndkDirectory.get().asFile,
            androidSdkDirectory = sdkComponents.sdkDirectory.get().asFile,
        )
    }

    private fun createZigConfig(
        target: Project,
        extension: ZigExtension,
    ): ZigConfig {
        val projectDir = extension.projectDir!!

        return ZigConfig(
            projectDirectory = target.rootProject.layout.projectDirectory
                .dir(projectDir)
                .asFile,
            executable = extension.executable,
            extraArgs = extension.extraArgs,
        )
    }

    private fun createVariantConfig(
        target: Project,
        variant: Variant,
    ): AndroidVariantConfig {
        val variantName = variant.name

        return AndroidVariantConfig(
            name = variantName,
            androidApiLevel = variant.minSdk.apiLevel,
            jniLibsDirectory = target.layout
                .buildDirectory
                .dir("generated/zig/$variantName/jniLibs")
                .get()
                .asFile,
        )
    }

    private fun addGeneratedJniLibs(
        variant: Variant,
        zigBuildTask: TaskProvider<ZigCoreBuildAndroidTask>,
    ) {
        val jniLibs = variant.sources.jniLibs
            ?: error("variant.sources.jniLibs is not available")

        jniLibs.addGeneratedSourceDirectory(
            zigBuildTask,
            ZigCoreBuildAndroidTask::jniLibsDirectory,
        )
    }

    private fun registerBuildTask(
        target: Project,
        zigConfig: ZigConfig,
        androidConfig: AndroidConfig,
        variantConfig: AndroidVariantConfig,
    ): TaskProvider<ZigCoreBuildAndroidTask> {
        val taskName = "zigBuild${variantConfig.name.replaceFirstChar(Char::titlecase)}"

        return target.tasks.register<ZigCoreBuildAndroidTask>(taskName) {
            group = "zig"
            description = "Build Zig native library for ${variantConfig.name}"

            initialize(zigConfig, androidConfig, variantConfig)
        }
    }
}
