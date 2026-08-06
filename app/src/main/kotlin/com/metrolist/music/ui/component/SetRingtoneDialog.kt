/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.RingtoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SetRingtoneDialog(
    visible: Boolean,
    mediaMetadata: MediaMetadata?,
    onDismiss: () -> Unit,
) {
    if (!visible || mediaMetadata == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current

    val totalDurationSec = (mediaMetadata.duration.takeIf { it > 0 } ?: 180).toFloat()
    var startPositionSec by remember { mutableFloatStateOf(0f) }
    val clipDurationSec = 30f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ringtone_trim_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val startMin = (startPositionSec / 60).toInt()
                    val startSec = (startPositionSec % 60).toInt()
                    val endSecTotal = (startPositionSec + clipDurationSec).coerceAtMost(totalDurationSec)
                    val endMin = (endSecTotal / 60).toInt()
                    val endSec = (endSecTotal % 60).toInt()

                    Text(
                        text = String.format("%d:%02d - %d:%02d", startMin, startSec, endMin, endSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = startPositionSec,
                    onValueChange = { startPositionSec = it },
                    valueRange = 0f..(totalDurationSec - 10f).coerceAtLeast(0f),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Ringtone Length: ${clipDurationSec.roundToInt()}s",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val localFile = File(context.cacheDir, "ringtone_source_${mediaMetadata.id}.mp3")
                        if (!localFile.exists() || localFile.length() <= 0) {
                            try {
                                val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                val playbackData = com.metrolist.music.utils.YTPlayerUtils.playerResponseForPlayback(
                                    videoId = mediaMetadata.id,
                                    audioQuality = com.metrolist.music.constants.AudioQuality.HIGH,
                                    connectivityManager = connectivityManager
                                ).getOrNull()
                                val streamUrl = playbackData?.streamUrl
                                if (!streamUrl.isNullOrBlank()) {
                                    val request = okhttp3.Request.Builder().url(streamUrl).build()
                                    val client = okhttp3.OkHttpClient()
                                    client.newCall(request).execute().use { response ->
                                        response.body?.byteStream()?.use { input ->
                                            localFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to fetch track audio for ringtone")
                            }
                        }

                        val startMs = (startPositionSec * 1000).toLong()
                        val durationMs = (clipDurationSec * 1000).toLong()

                        val success = RingtoneUtils.setAsRingtone(
                            context = context,
                            sourceFile = localFile,
                            title = mediaMetadata.title,
                            startMs = startMs,
                            durationMs = durationMs
                        )

                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, R.string.ringtone_set_success, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                if (!RingtoneUtils.canWriteSettings(context)) {
                                    Toast.makeText(context, R.string.ringtone_permission_required, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, R.string.ringtone_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.set_ringtone))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
