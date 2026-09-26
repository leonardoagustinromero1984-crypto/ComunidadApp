package com.comunidapp.app.domain.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object SocialPostMedia {
    const val MAX_IMAGES = 10

    fun isVideoMedia(type: com.comunidapp.app.data.model.PostType, mediaMime: String?, url: String?): Boolean {
        if (mediaMime?.startsWith("video/", ignoreCase = true) == true) return true
        if (type == com.comunidapp.app.data.model.PostType.REEL) return true
        return url?.contains(".mp4", ignoreCase = true) == true
    }

    fun displayUrls(imageUrl: String?, imageUrls: List<String>): List<String> {
        val primary = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val extras = imageUrls.map { it.trim() }.filter { it.isNotEmpty() }
        if (primary == null) return extras.distinct()
        return (listOf(primary) + extras.filter { it != primary }).distinct()
    }

    fun encode(
        locationLabel: String? = null,
        extraMediaAssetIds: List<String> = emptyList(),
        postType: String? = null,
        petIds: List<String> = emptyList()
    ): String = buildJsonObject {
        locationLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("location_label", it) }
        val extras = extraMediaAssetIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (extras.isNotEmpty()) {
            put("extra_media_asset_ids", JsonArray(extras.map { JsonPrimitive(it) }))
        }
        val pets = petIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (pets.isNotEmpty()) {
            put("pet_ids", JsonArray(pets.map { JsonPrimitive(it) }))
        }
        postType?.trim()?.takeIf { it.isNotEmpty() }?.let { put("post_type", it) }
    }.toString()

    fun withPetIds(compositionJson: String?, petIds: List<String>): String {
        val pets = petIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val root = parseObject(compositionJson)?.toMutableMap() ?: mutableMapOf()
        if (pets.isEmpty()) {
            root.remove("pet_ids")
        } else {
            root["pet_ids"] = JsonArray(pets.map { JsonPrimitive(it) })
        }
        return JsonObject(root).toString()
    }

    fun petIds(compositionJson: String?): List<String> {
        val root = parseObject(compositionJson) ?: return emptyList()
        val array = root["pet_ids"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { id -> id.isNotEmpty() } }
    }

    fun locationLabel(compositionJson: String?): String? =
        stringField(compositionJson, "location_label")

    fun postType(compositionJson: String?): String? =
        stringField(compositionJson, "post_type")

    fun extraMediaAssetIds(compositionJson: String?): List<String> {
        val root = parseObject(compositionJson) ?: return emptyList()
        val array = root["extra_media_asset_ids"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { id -> id.isNotEmpty() } }
    }

    fun extraMediaUrls(compositionJson: String?): List<String> {
        val root = parseObject(compositionJson) ?: return emptyList()
        val array = root["media_urls"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { url -> url.isNotEmpty() } }
    }

    private fun stringField(compositionJson: String?, key: String): String? {
        val root = parseObject(compositionJson) ?: return null
        return root[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseObject(raw: String?): JsonObject? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "{}") return null
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }
}
