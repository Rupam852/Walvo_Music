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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object SettingsFileBackupManager {

    private const val BACKUP_MAGIC_HEADER = "WALVO_SETTINGS_BACKUP_V2"

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
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext Result.ERROR

            val json = try {
                JSONObject(jsonStr)
            } catch (e: Throwable) {
                return@withContext Result.INVALID_FILE
            }

            val header = json.optString("header", "")
            val settingsObject = when {
                json.has("settings") -> json.getJSONObject("settings")
                json.has("contentLanguage") || json.has("preferredMusicLanguages") -> json
                else -> null
            }

            if (settingsObject == null) {
                Timber.w("[SettingsFileBackup] File does not contain valid Walvo settings")
                return@withContext Result.INVALID_FILE
            }

            var restoredCount = 0
            context.safeDataStoreEdit { mutablePrefs ->
                val keys = settingsObject.keys()
                while (keys.hasNext()) {
                    val keyName = keys.next()
                    if (keyName == "header" || keyName == "backupTimestamp") continue

                    val value = settingsObject.get(keyName)
                    when (value) {
                        is Boolean -> {
                            mutablePrefs[booleanPreferencesKey(keyName)] = value
                            restoredCount++
                        }
                        is Int -> {
                            mutablePrefs[intPreferencesKey(keyName)] = value
                            restoredCount++
                        }
                        is Long -> {
                            mutablePrefs[longPreferencesKey(keyName)] = value
                            restoredCount++
                        }
                        is Double -> {
                            // Check if it's a float or double
                            mutablePrefs[floatPreferencesKey(keyName)] = value.toFloat()
                            restoredCount++
                        }
                        is String -> {
                            mutablePrefs[stringPreferencesKey(keyName)] = value
                            restoredCount++
                        }
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until value.length()) {
                                set.add(value.getString(i))
                            }
                            mutablePrefs[stringSetPreferencesKey(keyName)] = set
                            restoredCount++
                        }
                    }
                }
            }

            Timber.d("[SettingsFileBackup] Successfully restored $restoredCount preference keys from file")
            Result.SUCCESS
        } catch (e: Throwable) {
            Timber.e(e, "[SettingsFileBackup] Error importing settings from file")
            Result.ERROR
        }
    }
}
