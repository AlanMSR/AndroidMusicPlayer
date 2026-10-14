package com.example.androidmusicplayer

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.androidmusicplayer.data.Song
import java.io.File

@Composable
fun rememberController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                if (!future.isCancelled) {
                    try {
                        controller = future.get()
                    } catch (e: Exception) {
                        // connection failed or was cancelled; leave controller null
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

val MediaItem.queuePos: Int
    get() = mediaMetadata.extras?.getInt("pos", -1) ?: -1

fun Song.toMediaItem(pos: Int = -1): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.fromFile(File(filePath)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(uploader)
                .setArtworkUri(thumbnailPath?.let { Uri.fromFile(File(it)) })
                .setExtras(Bundle().apply { putInt("pos", pos) })
                .build()
        )
        .build()