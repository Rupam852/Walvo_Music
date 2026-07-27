/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.extensions.zipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object SettingsFileBackupManager {

    private const val BACKUP_MAGIC_HEADER = "WALVO_SETTINGS_BACKUP_V2"

    private val KNOWN_BOOLEAN_KEYS = setOf(
        "enableHighRefreshRate", "enableLandscapeScaling", "dynamicTheme", "pureBlack", "pureBlackMiniPlayer",
        "miniPlayerOutline", "slimNavBar", "squigglySlider", "SwipeToSong", "SwipeToRemoveSong",
        "useNewPlayerDesign", "useNewMiniPlayerDesign", "hidePlayerThumbnail", "cropAlbumArt", "seekExtraSeconds",
        "pauseOnMute", "resumeOnBluetoothConnect", "keepScreenOn", "alarmEnabled", "alarmRandomSong",
        "developerMode", "autoSyncAppSettings", "enableKugou", "enableLrclib", "enableBetterLyrics",
        "enablePaxsenix", "enableLyricsPlus", "hideExplicit", "hideVideoSongs", "hideYoutubeShorts",
        "showArtistDescription", "showArtistSubscriberCount", "showMonthlyListeners", "proxyEnabled",
        "ytmSync", "checkForUpdates", "updateNotifications", "enableOffload", "audioTrackPlaybackParams",
        "varispeed", "persistentQueue", "persistentShuffleAcrossQueues", "rememberShuffleAndRepeat",
        "shuffleMode", "skipSilence", "skipSilenceInstant", "audioNormalization", "autoLoadMore",
        "autoRadioQueue", "disableLoadMoreWhenRepeatAll", "autoDownloadOnLike", "similarContent",
        "autoSkipNextOnError", "autoplay", "stopMusicOnTaskClear", "shufflePlaylistFirst",
        "preventDuplicateTracksInQueue", "crossfadeEnabled", "crossfadeGapless", "enableSongCache",
        "pauseListenHistory", "pauseSearchHistory", "disableScreenshot", "streamSourceWebRemix",
        "streamSourceTVHTML5", "streamSourceAndroidVR", "streamSourceVisionOS", "streamSourceIOS",
        "streamSourceWebCreator", "streamSourceAndroidCreator", "enableDynamicIcon", "discordRPCEnable",
        "discordInfoDismissed", "discordAdvancedMode", "discordButton1Enabled", "discordButton2Enabled",
        "enableGoogleCast"
    )

    private val KNOWN_FLOAT_KEYS = setOf(
        "density_scale_factor", "custom_density_scale_value", "crossfadeDurationFloat",
        "scrobbleDelayPercent", "historyDuration", "lyricsTextSize", "lyricsLineSpacing",
        "playerVolume", "sleepTimerDefault", "swipeSensitivity"
    )

    private val KNOWN_INT_KEYS = setOf(
        "selectedThemeColor", "alarmHour", "alarmMinute", "maxImageCacheSize", "maxSongCacheSize",
        "scrobbleMinSongDuration", "scrobbleDelaySeconds", "repeatMode"
    )

    private val KNOWN_LONG_KEYS = setOf(
        "alarmNextTriggerAt", "lastUpdateCheckTime", "listenTogetherSessionTimestamp",
        "last_like_song_sync", "last_library_song_sync", "last_album_sync", "last_artist_sync",
        "last_playlist_sync", "last_full_sync", "last_weekly_most_playlist_sync", "last_monthly_most_playlist_sync"
    )

    private val KNOWN_SET_KEYS = setOf(
        "preferredMusicLanguages"
    )

    enum class Result {
        SUCCESS,
        INVALID_FILE,
        ERROR
    }

    suspend fun exportSettingsToFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.dataStore.data.first()
            val prefsMap = prefs.asMap()

            val json = JSONObject().apply {
                put("header", BACKUP_MAGIC_HEADER)
                put("backupTimestamp", System.currentTimeMillis())

                val settingsObject = JSONObject()
                prefsMap.forEach { (key, value) ->
                    when (value) {
                        is Set<*> -> settingsObject.put(key.name, JSONArray(value.toList()))
                        is Float -> settingsObject.put(key.name, value.toDouble())
                        else -> settingsObject.put(key.name, value)
                    }
                }
                put("settings", settingsObject)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toString(4).toByteArray(Charsets.UTF_8))
            } ?: return@withContext false

            Timber.d("[SettingsFileBackup] Successfully exported ${prefsMap.size} preference keys to file")
            true
        } catch (e: Throwable) {
            Timber.e(e, "[SettingsFileBackup] Error exporting settings to file")
            false
        }
    }

    suspend fun importSettingsFromFile(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val fileContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return@withContext Result.ERROR

            // Try JSON format first
            val jsonResult = tryImportJson(context, fileContent)
            if (jsonResult != Result.INVALID_FILE) {
                return@withContext jsonResult
            }

            // Try ZIP/Binary backup format second
            return@withContext tryImportZipBackup(context, uri)
        } catch (e: Throwable) {
            Timber.e(e, "[SettingsFileBackup] Error importing settings from file")
            Result.ERROR
        }
    }

    private suspend fun tryImportJson(context: Context, bytes: ByteArray): Result {
        val jsonStr = try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            return Result.INVALID_FILE
        }

        val json = try {
            JSONObject(jsonStr)
        } catch (e: Throwable) {
            return Result.INVALID_FILE
        }

        val settingsObject = when {
            json.has("settings") -> json.getJSONObject("settings")
            json.has("contentLanguage") || json.has("preferredMusicLanguages") || json.has("audioQuality") -> json
            else -> null
        } ?: return Result.INVALID_FILE

        var restoredCount = 0
        context.safeDataStoreEdit { mutablePrefs ->
            val keys = settingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                if (keyName == "header" || keyName == "backupTimestamp") continue

                val value = settingsObject.get(keyName)
                if (value == JSONObject.NULL) continue

                // Purge any legacy/corrupted type variants of keyName before setting new type
                mutablePrefs.remove(booleanPreferencesKey(keyName))
                mutablePrefs.remove(floatPreferencesKey(keyName))
                mutablePrefs.remove(intPreferencesKey(keyName))
                mutablePrefs.remove(longPreferencesKey(keyName))
                mutablePrefs.remove(stringPreferencesKey(keyName))
                mutablePrefs.remove(stringSetPreferencesKey(keyName))

                when {
                    keyName in KNOWN_BOOLEAN_KEYS || value is Boolean -> {
                        val boolVal = when (value) {
                            is Boolean -> value
                            is String -> value.toBoolean()
                            is Number -> value.toInt() != 0
                            else -> false
                        }
                        mutablePrefs[booleanPreferencesKey(keyName)] = boolVal
                        restoredCount++
                    }
                    keyName in KNOWN_FLOAT_KEYS -> {
                        val floatVal = (value as? Number)?.toFloat() ?: value.toString().toFloatOrNull() ?: 0f
                        mutablePrefs[floatPreferencesKey(keyName)] = floatVal
                        restoredCount++
                    }
                    keyName in KNOWN_INT_KEYS -> {
                        val intVal = (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: 0
                        mutablePrefs[intPreferencesKey(keyName)] = intVal
                        restoredCount++
                    }
                    keyName in KNOWN_LONG_KEYS -> {
                        val longVal = (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: 0L
                        mutablePrefs[longPreferencesKey(keyName)] = longVal
                        restoredCount++
                    }
                    keyName in KNOWN_SET_KEYS || value is JSONArray -> {
                        val set = mutableSetOf<String>()
                        if (value is JSONArray) {
                            for (i in 0 until value.length()) {
                                set.add(value.getString(i))
                            }
                        } else if (value is String) {
                            set.addAll(value.split(",").filter { it.isNotBlank() })
                        }
                        mutablePrefs[stringSetPreferencesKey(keyName)] = set
                        restoredCount++
                    }
                    else -> {
                        mutablePrefs[stringPreferencesKey(keyName)] = value.toString()
                        restoredCount++
                    }
                }
            }
        }

        Timber.d("[SettingsFileBackup] Successfully restored $restoredCount preference keys from JSON")
        return Result.SUCCESS
    }

    private fun tryImportZipBackup(context: Context, uri: Uri): Result {
        val targetSettings = File(context.filesDir, "datastore/settings.preferences_pb")
        var restored = false

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                raw.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        if (entry.name == "settings.preferences_pb" || entry.name.endsWith(".preferences_pb")) {
                            targetSettings.parentFile?.mkdirs()
                            FileOutputStream(targetSettings).use { out ->
                                inputStream.copyTo(out)
                            }
                            restored = true
                            break
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            }
        }

        return if (restored) Result.SUCCESS else Result.INVALID_FILE
    }
}
