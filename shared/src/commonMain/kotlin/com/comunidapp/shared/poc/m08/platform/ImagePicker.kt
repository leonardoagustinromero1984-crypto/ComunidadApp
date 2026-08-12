package com.comunidapp.shared.poc.m08.platform

import com.comunidapp.shared.poc.m08.model.ImagePickResult

/**
 * Common contract: request an image without knowing Android/iOS APIs.
 */
interface ImagePicker {
    suspend fun pickImage(): ImagePickResult
}
