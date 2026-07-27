/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.EnableHighRefreshRateKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.PreferredMusicLanguagesKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.constants.PureBlackMiniPlayerKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.SelectedThemeColorKey
import com.metrolist.music.constants.ShowArtistDescriptionKey
import com.metrolist.music.constants.ShowArtistSubscriberCountKey
import com.metrolist.music.constants.ShowMonthlyListenersKey
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.sql.DriverManager
import java.util.Properties

object CloudSettingsSyncManager {

    private const val NEON_JDBC_URL =
        "jdbc:postgresql://ep-billowing-moon-azjx44np-pooler.c-3.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require&user=neondb_owner&password=npg_XYRETgsA1Bj8"

    @Volatile
    private var tableChecked = false

    @Volatile
    private var isSyncing = false

    private fun getConnection(): java.sql.Connection? {
        return runCatching {
            Class.forName("org.postgresql.Driver")
            val props = Properties().apply {
                setProperty("user", "neondb_owner")
                setProperty("password", "npg_XYRETgsA1Bj8")
                setProperty("sslmode", "require")
                setProperty("connectTimeout", "5")
                setProperty("socketTimeout", "5")
            }
            DriverManager.getConnection(NEON_JDBC_URL, props)
        }.onFailure { e ->
            Timber.w("[CloudSync] Failed to establish Neon PostgreSQL connection: ${e.message}")
        }.getOrNull()
    }

    private fun ensureTableExists(conn: java.sql.Connection) {
        if (tableChecked) return
        try {
            val sql = """
                CREATE TABLE IF NOT EXISTS user_app_settings (
                    user_id VARCHAR(255) PRIMARY KEY,
                    account_email VARCHAR(255),
                    account_name VARCHAR(255),
                    settings_json TEXT NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            tableChecked = true
            Timber.d("[CloudSync] Neon PostgreSQL table user_app_settings verified")
        } catch (e: Throwable) {
            Timber.e(e, "[CloudSync] Error creating user_app_settings table")
        }
    }

    private fun extractUserId(cookie: String, email: String): String? {
        return runCatching {
            val parsed = parseCookieString(cookie)
            val sapisid = parsed["SAPISID"] ?: parsed["__Secure-3PAPISID"]
            if (!sapisid.isNullOrBlank()) return sapisid.trim()
            if (email.isNotBlank()) return email.trim().lowercase()
            null
        }.getOrNull()
    }

    suspend fun syncLocalSettingsToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isSyncing) return@withContext false
        isSyncing = true
        try {
            val prefs = context.dataStore.data.first()
            val cookie = prefs[InnerTubeCookieKey].orEmpty()
            val email = prefs[AccountEmailKey].orEmpty()
            val name = prefs[AccountNameKey].orEmpty()

            val userId = extractUserId(cookie, email) ?: return@withContext false

            val json = JSONObject().apply {
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

            val conn = getConnection() ?: return@withContext false
            conn.use { c ->
                ensureTableExists(c)
                val sql = """
                    INSERT INTO user_app_settings (user_id, account_email, account_name, settings_json, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id) DO UPDATE SET
                        account_email = EXCLUDED.account_email,
                        account_name = EXCLUDED.account_name,
                        settings_json = EXCLUDED.settings_json,
                        updated_at = CURRENT_TIMESTAMP;
                """.trimIndent()
                c.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, email)
                    stmt.setString(3, name)
                    stmt.setString(4, json.toString())
                    stmt.executeUpdate()
                }
                Timber.d("[CloudSync] Successfully backed up settings for user: $userId")
                true
            }
        } catch (e: Throwable) {
            Timber.e(e, "[CloudSync] Throwable in syncLocalSettingsToCloud")
            false
        } finally {
            isSyncing = false
        }
    }

    suspend fun restoreSettingsFromCloudIfAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isSyncing) return@withContext false
        isSyncing = true
        try {
            val prefs = context.dataStore.data.first()
            val cookie = prefs[InnerTubeCookieKey].orEmpty()
            val email = prefs[AccountEmailKey].orEmpty()

            val userId = extractUserId(cookie, email) ?: return@withContext false

            val conn = getConnection() ?: return@withContext false
            conn.use { c ->
                ensureTableExists(c)
                val sql = "SELECT settings_json FROM user_app_settings WHERE user_id = ?"
                var settingsJsonStr: String? = null
                c.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            settingsJsonStr = rs.getString("settings_json")
                        }
                    }
                }

                if (settingsJsonStr.isNullOrBlank()) {
                    Timber.d("[CloudSync] No cloud backup found for user: $userId.")
                    return@use false
                }

                val json = JSONObject(settingsJsonStr!!)
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
                Timber.d("[CloudSync] Successfully restored settings for user: $userId")
                true
            }
        } catch (e: Throwable) {
            Timber.e(e, "[CloudSync] Throwable in restoreSettingsFromCloudIfAvailable")
            false
        } finally {
            isSyncing = false
        }
    }
}
