package com.comunidapp.app.data.remote.storage

import android.net.Uri

interface ImageStorageService {
    suspend fun uploadImage(path: String, uri: Uri): Result<String>
}
