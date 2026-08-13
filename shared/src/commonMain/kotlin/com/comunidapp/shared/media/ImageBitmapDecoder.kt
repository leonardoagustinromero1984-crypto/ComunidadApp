package com.comunidapp.shared.media

import androidx.compose.ui.graphics.ImageBitmap

internal expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
