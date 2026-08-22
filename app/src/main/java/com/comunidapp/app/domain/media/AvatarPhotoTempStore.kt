package com.comunidapp.app.domain.media

import java.io.File

object AvatarPhotoTempStore {
    const val DIR_NAME = "avatar-edit"
    const val FILE_PREFIX = "avatar_"
    const val FILE_SUFFIX = ".jpg"
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun directory(cacheDir: File): File = File(cacheDir, DIR_NAME).apply { mkdirs() }

    fun newFile(cacheDir: File): File =
        File(directory(cacheDir), "$FILE_PREFIX${System.currentTimeMillis()}$FILE_SUFFIX")

    fun cleanup(cacheDir: File, keep: File? = null, nowMs: Long = System.currentTimeMillis()) {
        val dir = File(cacheDir, DIR_NAME)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            if (!file.name.startsWith(FILE_PREFIX)) return@forEach
            if (nowMs - file.lastModified() >= MAX_AGE_MS || keep != null) {
                file.delete()
            }
        }
    }

    fun delete(path: String?) {
        val value = path?.trim().orEmpty()
        if (value.isEmpty()) return
        runCatching { File(value).takeIf { it.exists() }?.delete() }
    }
}
