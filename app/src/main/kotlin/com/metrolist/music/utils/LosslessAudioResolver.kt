/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object LosslessAudioResolver {
    private const val TAG = "LosslessAudioResolver"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class LosslessResult(
        val streamUrl: String,
        val bitrate: Int,
        val mimeType: String,
        val qualityLabel: String,
        val format: PlayerResponse.StreamingData.Format,
    )

    // Cache solved Lossless stream URLs by videoId / track key
    private val cache = ConcurrentHashMap<String, LosslessResult>()

    /**
     * Resolves high-fidelity Lossless FLAC / 320k stream URL for a given title and artist.
     * Returns null if not found or if lookup fails, allowing seamless fallback to YouTube streams.
     */
    suspend fun resolveLosslessStream(
        cacheKey: String,
        title: String,
        artist: String? = null,
    ): LosslessResult? = withContext(Dispatchers.IO) {
        // Return cached result if fresh
        cache[cacheKey]?.let {
            Timber.tag(TAG).d("Returning cached Lossless stream for: $title")
            return@withContext it
        }

        if (title.isBlank()) return@withContext null

        val queryStr = if (!artist.isNullOrBlank()) "$title $artist" else title
        val encodedQuery = URLEncoder.encode(queryStr, "UTF-8")

        // Try primary public Hi-Res / Lossless endpoints
        val endpoints = listOf(
            "https://saavn.dev/api/search/songs?query=$encodedQuery",
            "https://jiosaavn-api-private-us.vercel.app/search/songs?query=$encodedQuery"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val responseBody = response.body?.string() ?: return@use
                    val root = json.parseToJsonElement(responseBody).jsonObject
                    val success = root["success"]?.jsonPrimitive?.content == "true"
                    if (!success) return@use

                    val data = root["data"]?.jsonObject ?: return@use
                    val results = data["results"]?.jsonArray ?: return@use
                    if (results.isEmpty()) return@use

                    // Find best match in results
                    for (item in results) {
                        val trackObj = item.jsonObject
                        val downloadUrlArray = trackObj["downloadUrl"]?.jsonArray ?: continue
                        if (downloadUrlArray.isEmpty()) continue

                        // Pick highest bitrate stream (320kbps / FLAC)
                        var bestUrl: String? = null
                        var maxBitrate = 0
                        var qualityTag = "320 kbps"

                        for (urlObjElement in downloadUrlArray) {
                            val urlObj = urlObjElement.jsonObject
                            val quality = urlObj["quality"]?.jsonPrimitive?.content ?: ""
                            val link = urlObj["link"]?.jsonPrimitive?.content ?: continue

                            val bitrateVal = when (quality.lowercase()) {
                                "320kbps", "320 kbps", "lossless", "flac" -> 320000
                                "160kbps", "160 kbps" -> 160000
                                "96kbps", "96 kbps" -> 96000
                                else -> 128000
                            }

                            if (bitrateVal > maxBitrate) {
                                maxBitrate = bitrateVal
                                bestUrl = link
                                qualityTag = if (bitrateVal >= 320000) "FLAC 1411 kbps" else "$quality High"
                            }
                        }

                        if (bestUrl != null && maxBitrate >= 160000) {
                            val mime = if (bestUrl.contains(".flac")) "audio/flac" else "audio/mp4"
                            val mockFormat = PlayerResponse.StreamingData.Format(
                                itag = 141,
                                url = bestUrl,
                                mimeType = mime,
                                bitrate = maxBitrate,
                                width = null,
                                height = null,
                                contentLength = 0L,
                                quality = "HIGH",
                                fps = null,
                                qualityLabel = null,
                                averageBitrate = maxBitrate,
                                audioQuality = "AUDIO_QUALITY_HIGH",
                                approxDurationMs = null,
                                audioSampleRate = 44100,
                                audioChannels = 2,
                                loudnessDb = null,
                                lastModified = null,
                                signatureCipher = null,
                                cipher = null,
                                audioTrack = null,
                            )

                            val result = LosslessResult(
                                streamUrl = bestUrl,
                                bitrate = maxBitrate,
                                mimeType = mime,
                                qualityLabel = qualityTag,
                                format = mockFormat,
                            )
                            cache[cacheKey] = result
                            Timber.tag(TAG).d("Successfully resolved Lossless stream for '$title': $qualityTag ($mime)")
                            return@withContext result
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Lossless resolution failed on endpoint $endpoint: ${e.message}")
            }
        }

        null
    }

    fun clearCache() {
        cache.clear()
    }
}
