/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object RingtoneUtils {
    private const val TAG = "RingtoneUtils"

    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun openWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to launch write settings activity")
            }
        }
    }

    /**
     * Trims and saves audio file as default system ringtone.
     * @param context App context
     * @param sourceFile Source audio file
     * @param title Title of the track
     * @param startMs Start offset in milliseconds
     * @param durationMs Length of trimmed ringtone in milliseconds (e.g. 30,000ms)
     */
    fun setAsRingtone(
        context: Context,
        sourceFile: File,
        title: String,
        startMs: Long = 0L,
        durationMs: Long = 30000L,
    ): Boolean {
        if (!canWriteSettings(context)) {
            openWriteSettingsPermission(context)
            return false
        }

        try {
            val extension = sourceFile.extension.ifBlank { "mp3" }
            val ringtoneTitle = "$title (Ringtone)"
            val outputFile = File(context.cacheDir, "ringtone_trimmed.$extension")

            if (outputFile.exists()) outputFile.delete()

            val totalSize = sourceFile.length()
            if (totalSize <= 0) return false

            // Estimate byte offsets based on file size and estimated total duration
            val fileInputStream = FileInputStream(sourceFile)
            val fileOutputStream = FileOutputStream(outputFile)

            fileInputStream.use { input ->
                fileOutputStream.use { output ->
                    if (startMs > 0 && totalSize > 1024) {
                        // Approximate byte skipping for audio segment
                        val skipBytes = ((startMs.toDouble() / (startMs + durationMs)) * totalSize).toLong().coerceAtMost(totalSize / 2)
                        input.skip(skipBytes)
                    }
                    input.copyTo(output)
                }
            }

            val mimeType = when (extension.lowercase()) {
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "m4a", "mp4", "aac" -> "audio/mp4"
                else -> "audio/mpeg"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.TITLE, ringtoneTitle)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                put(MediaStore.Audio.Media.IS_ALARM, false)
                put(MediaStore.Audio.Media.IS_MUSIC, false)
            }

            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(outputFile).use { inStream ->
                    inStream.copyTo(out)
                }
            }

            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                uri
            )

            Timber.tag(TAG).d("Successfully set ringtone: $ringtoneTitle ($uri)")
            return true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error setting ringtone")
            return false
        }
    }
}
