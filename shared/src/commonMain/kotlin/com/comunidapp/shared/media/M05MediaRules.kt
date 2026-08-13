package com.comunidapp.shared.media

/**
 * Reglas M05 para LOST_FOUND_MEDIA (migración 024 + FilePurposeSpec Android).
 */
object M05LostFoundMediaRules {
    const val PURPOSE = "LOST_FOUND_MEDIA"
    const val RESOURCE_TYPE = "LOST_FOUND_CASE"
    const val OWNER_KIND = "USER"
    const val VISIBILITY = "PUBLIC"
    const val MAX_BYTES = 8_388_608L
    val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")

    fun validate(content: FileContent): Result<Unit> {
        if (content.bytes.isEmpty() || content.sizeBytes <= 0L) {
            return Result.failure(IllegalArgumentException("MEDIA_FILE_EMPTY"))
        }
        if (content.sizeBytes > MAX_BYTES || content.bytes.size.toLong() > MAX_BYTES) {
            return Result.failure(IllegalArgumentException("MEDIA_FILE_TOO_LARGE"))
        }
        val mime = content.mimeType.trim().lowercase()
        if (mime !in ALLOWED_MIME) {
            return Result.failure(IllegalArgumentException("MEDIA_MIME_REJECTED"))
        }
        return Result.success(Unit)
    }

    fun sanitizeFilename(originalFilename: String): Result<String> {
        var name = originalFilename.trim()
        if (name.isEmpty()) return Result.failure(IllegalArgumentException("FILENAME_REQUIRED"))
        if (name.contains("..")) return Result.failure(IllegalArgumentException("FILENAME_TRAVERSAL"))
        name = name.substringAfterLast('/').substringAfterLast('\\')
        if (name.contains("..")) return Result.failure(IllegalArgumentException("FILENAME_TRAVERSAL"))
        name = Regex("\\s+").replace(name, " ").trim()
        if (name.isEmpty()) return Result.failure(IllegalArgumentException("FILENAME_EMPTY"))
        val dangerous = setOf(
            "exe", "apk", "bat", "cmd", "com", "msi", "jar", "js",
            "html", "htm", "svg", "sh", "ps1", "dll", "scr"
        )
        val ext = extensionOf(name)
        if (ext != null && ext in dangerous) {
            return Result.failure(IllegalArgumentException("FILENAME_DANGEROUS_EXT"))
        }
        if (name.length > 120) {
            val e = ext?.let { ".$it" }.orEmpty()
            val base = name.dropLast(e.length).take(120 - e.length)
            name = base + e
        }
        val safe = buildString {
            name.forEach { c ->
                when {
                    c.isLetterOrDigit() || c == '.' || c == '-' || c == '_' -> append(c)
                    c == ' ' -> append('_')
                    else -> append('_')
                }
            }
        }.trim('_')
        if (safe.isEmpty() || safe == "." || safe.startsWith(".")) {
            return Result.failure(IllegalArgumentException("FILENAME_INVALID"))
        }
        return Result.success(safe)
    }

    private fun extensionOf(filename: String): String? {
        val base = filename.substringAfterLast('/').substringAfterLast('\\')
        val idx = base.lastIndexOf('.')
        if (idx <= 0 || idx == base.lastIndex) return null
        return base.substring(idx + 1).lowercase()
    }
}
