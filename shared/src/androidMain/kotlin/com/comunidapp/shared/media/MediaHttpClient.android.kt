package com.comunidapp.shared.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android

internal actual fun createMediaHttpClient(): HttpClient = HttpClient(Android)
