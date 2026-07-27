/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.EnableHighRefreshRateKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.PreferredMusicLanguagesKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.constants.PureBlackMiniPlayerKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.SelectedThemeColorKey
import com.metrolist.music.constants.ShowArtistDescriptionKey
import com.metrolist.music.constants.ShowArtistSubscriberCountKey
import com.metrolist.music.constants.ShowMonthlyListenersKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

object SettingsFileBackupManager {

    private const val BACKUP_MAGIC_HEADER = "WALVO_SETTINGS_BACKUP_V1"

    enum class Result {
        SUCCESS,
        INVALID_FILE,
        ERROR
    }

    suspend fun exportSettingsToFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.dataStore.data.first()
            val json = JSONObject().apply {
                put("header", BACKUP_MAGIC_HEADER)
                put("contentLanguage", prefs[ContentLanguageKey] ?: SYSTEM_DEFAULT)
                put("contentCountry", prefs[ContentCountryKey] ?: SYSTEM_DEFAULT)
                put("appLanguage", prefs[AppLanguageKey] ?: SYSTEM_DEFAULT)
                put("preferredMusicLanguages", prefs[PreferredMusicLanguagesKey]?.joinToString(",") ?: "")
                put("hideExplicit", prefs[HideExplicitKey] ?: false)
                put("hideVideoSongs", prefs[HideVideoSongsKey] ?: false)
                put("hideYoutubeShorts", prefs[HideYoutubeShortsKey] ?: false)
                put("darkMode", prefs[DarkModeKey] ?: "AUTO")
                put("dynamicTheme", prefs[DynamicThemeKey] ?: true)
                put("pureBlack", prefs[PureBlackKey] ?: false)
                put("pureBlackMiniPlayer", prefs[PureBlackMiniPlayerKey] ?: false)
                put("selectedThemeColor", prefs[SelectedThemeColorKey] ?: 0)
                put("enableHighRefreshRate", prefs[EnableHighRefreshRateKey] ?: true)
                put("showArtistDescription", prefs[ShowArtistDescriptionKey] ?: true)
                put("showArtistSubscriberCount", prefs[ShowArtistSubscriberCountKey] ?: true)
                put("showMonthlyListeners", prefs[ShowMonthlyListenersKey] ?: true)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toString(4).toByteArray(Charsets.UTF_8))
            } ?: return@withContext false

            true
        } catch (e: Throwable) {
            Timber.e(e, "[SettingsFileBackup] Error exporting settings to file")
            false
        }
    }

    suspend fun importSettingsFromFile(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext Result.ERROR

            val json = try {
                JSONObject(jsonStr)
            } catch (e: Throwable) {
                return@withContext Result.INVALID_FILE
            }

            val header = json.optString("header", "")
            val isWalvoBackup = header == BACKUP_MAGIC_HEADER ||
                    json.has("contentLanguage") ||
                    json.has("preferredMusicLanguages") ||
                    json.has("appLanguage")

            if (!isWalvoBackup) {
                return@withContext Result.INVALID_FILE
            }

            context.safeDataStoreEdit { mutablePrefs ->
                if (json.has("contentLanguage")) mutablePrefs[ContentLanguageKey] = json.getString("contentLanguage")
                if (json.has("contentCountry")) mutablePrefs[ContentCountryKey] = json.getString("contentCountry")
                if (json.has("appLanguage")) mutablePrefs[AppLanguageKey] = json.getString("appLanguage")
                if (json.has("preferredMusicLanguages")) {
                    val langStr = json.getString("preferredMusicLanguages")
                    mutablePrefs[PreferredMusicLanguagesKey] = langStr.split(",").filter { it.isNotBlank() }.toSet()
                }
                if (json.has("hideExplicit")) mutablePrefs[HideExplicitKey] = json.getBoolean("hideExplicit")
                if (json.has("hideVideoSongs")) mutablePrefs[HideVideoSongsKey] = json.getBoolean("hideVideoSongs")
                if (json.has("hideYoutubeShorts")) mutablePrefs[HideYoutubeShortsKey] = json.getBoolean("hideYoutubeShorts")
                if (json.has("darkMode")) mutablePrefs[DarkModeKey] = json.getString("darkMode")
                if (json.has("dynamicTheme")) mutablePrefs[DynamicThemeKey] = json.getBoolean("dynamicTheme")
                if (json.has("pureBlack")) mutablePrefs[PureBlackKey] = json.getBoolean("pureBlack")
                if (json.has("pureBlackMiniPlayer")) mutablePrefs[PureBlackMiniPlayerKey] = json.getBoolean("pureBlackMiniPlayer")
                if (json.has("selectedThemeColor")) mutablePrefs[SelectedThemeColorKey] = json.getInt("selectedThemeColor")
                if (json.has("enableHighRefreshRate")) mutablePrefs[EnableHighRefreshRateKey] = json.getBoolean("enableHighRefreshRate")
                if (json.has("showArtistDescription")) mutablePrefs[ShowArtistDescriptionKey] = json.getBoolean("showArtistDescription")
                if (json.has("showArtistSubscriberCount")) mutablePrefs[ShowArtistSubscriberCountKey] = json.getBoolean("showArtistSubscriberCount")
                if (json.has("showMonthlyListeners")) mutablePrefs[ShowMonthlyListenersKey] = json.getBoolean("showMonthlyListeners")
            }

            Result.SUCCESS
        } catch (e: Throwable) {
            Timber.e(e, "[SettingsFileBackup] Error importing settings from file")
            Result.ERROR
        }
    }
}
