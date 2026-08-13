package com.comunidapp.shared.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createMediaHttpClient(): HttpClient = HttpClient(Darwin)
