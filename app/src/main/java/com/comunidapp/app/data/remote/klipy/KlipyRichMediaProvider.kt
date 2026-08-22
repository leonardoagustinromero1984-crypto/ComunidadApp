package com.comunidapp.app.data.remote.klipy

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.social.DisabledRichMediaProvider
import com.comunidapp.app.domain.social.KlipyConfig
import com.comunidapp.app.domain.social.RichMediaItem
import com.comunidapp.app.domain.social.RichMediaKind
import com.comunidapp.app.domain.social.RichMediaPage
import com.comunidapp.app.domain.social.RichMediaQuery
import com.comunidapp.app.domain.social.RichMediaResult
import com.comunidapp.app.domain.social.RichMediaStickerProvider
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KlipyRichMediaProvider(
    private val http: HttpClient = HttpClient(Android) { expectSuccess = false },
    private val enabledFlag: Boolean = BuildConfig.KLIPY_ENABLED
) : RichMediaStickerProvider {
    override val id: String = KlipyConfig.PROVIDER_ID
    private val json = Json { ignoreUnknownKeys = true }

    override fun isConfigured(): Boolean = enabledFlag

    override suspend fun search(query: RichMediaQuery): RichMediaResult {
        if (!enabledFlag) return RichMediaResult.Disabled("KLIPY_DISABLED")
        val q = query.query?.trim().orEmpty()
        if (q.isBlank()) return featured(query)
        return invoke("search", query, q)
    }

    override suspend fun featured(query: RichMediaQuery): RichMediaResult {
        if (!enabledFlag) return RichMediaResult.Disabled("KLIPY_DISABLED")
        return invoke("trending", query, null)
    }

    private suspend fun invoke(action: String, query: RichMediaQuery, q: String?): RichMediaResult {
        val token = runCatching { supabase.auth.currentSessionOrNull()?.accessToken }.getOrNull()
        val base = AppConfigProvider.get().supabaseUrl?.trimEnd('/') ?: BuildConfig.SUPABASE_URL
        val url = "$base/functions/v1/${KlipyConfig.EDGE_FUNCTION}" +
            "?action=$action" +
            "&kind=${query.kind.name.lowercase()}" +
            "&page=${query.page}" +
            "&per_page=${query.perPage}" +
            "&locale=${query.locale}" +
            "&rating=${query.safeFilter}" +
            (q?.takeIf { it.isNotBlank() }?.let { "&q=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
        return try {
            val response = http.request(url) {
                method = HttpMethod.Get
                header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            }
            val body = response.bodyAsText()
            when (response.status.value) {
                401, 403, 404, 501, 503 -> RichMediaResult.Disabled("KLIPY_KEY_REQUIRED")
                in 200..299 -> parse(body, query)
                else -> RichMediaResult.Failure("KLIPY_HTTP_${response.status.value}", "No pudimos cargar GIFs ahora.")
            }
        } catch (t: Throwable) {
            val code = when {
                t.message?.contains("Unable to resolve host", true) == true -> "NETWORK_UNAVAILABLE"
                t.message?.contains("timeout", true) == true -> "TIMEOUT"
                else -> "KLIPY_FAILURE"
            }
            RichMediaResult.Failure(code, "No pudimos cargar GIFs ahora.")
        }
    }

    private fun parse(raw: String, query: RichMediaQuery): RichMediaResult {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            return RichMediaResult.Failure("KLIPY_PARSE", "No pudimos leer el catálogo externo.")
        }
        if (root["disabled"]?.jsonPrimitive?.content == "true") {
            return RichMediaResult.Disabled(root["reason"]?.jsonPrimitive?.content ?: "KLIPY_KEY_REQUIRED")
        }
        val data = root["data"]?.jsonArray
            ?: root["result"]?.jsonArray
            ?: return RichMediaResult.Ok(RichMediaPage(emptyList(), null, query.kind))
        val items = data.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj.string("id") ?: obj.string("slug") ?: return@mapNotNull null
            val files = obj["file"]?.jsonObject ?: obj["files"]?.jsonObject
            val hd = files?.get("hd")?.jsonObject ?: files?.get("gif")?.jsonObject ?: files
            val url = hd?.string("url") ?: obj.string("url") ?: return@mapNotNull null
            val preview = hd?.string("preview") ?: url
            RichMediaItem(
                id = id,
                kind = query.kind,
                previewUrl = preview,
                contentUrl = url,
                title = obj.string("title")
            )
        }
        val current = query.page
        val next = if (items.size >= query.perPage) current + 1 else null
        return RichMediaResult.Ok(RichMediaPage(items, next, query.kind))
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}

object RichMediaProviders {
    fun active(): RichMediaStickerProvider =
        if (BuildConfig.KLIPY_ENABLED) KlipyRichMediaProvider() else DisabledRichMediaProvider
}
