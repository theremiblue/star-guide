/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class AppLicenseAssetsTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFile
    abstract val copyingFile: RegularFileProperty

    @get:InputDirectory
    abstract val resourcesDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val outputDir = outputDirectory.get().asFile

        outputDir.deleteRecursively()

        fileSystemOperations.copy {
            from(copyingFile)
            into(outputDir.resolve("licenses"))
        }

        fileSystemOperations.copy {
            from(resourcesDirectory)
            into(outputDir.resolve("licenses"))
        }
    }
}
