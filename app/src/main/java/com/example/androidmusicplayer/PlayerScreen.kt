package com.example.androidmusicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.example.androidmusicplayer.data.AppDatabase
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerScreen(
    songId: Long,
    queuePlaylistId: Long?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val songsFlow = remember(queuePlaylistId) {
        if (queuePlaylistId == null) dao.getAllSongs()
        else dao.getSongsInPlaylist(queuePlaylistId)
    }
    val songs by songsFlow.collectAsState(initial = emptyList())
    val controller = rememberController()

    var shownId by remember { mutableLongStateOf(songId) }
    val index = songs.indexOfFirst { it.id == shownId }
    val song = songs.getOrNull(index)

    var playingId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val isCurrent = playingId == shownId.toString()
    val showPlaying = isCurrent && isPlaying

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            playingId = c.currentMediaItem?.mediaId
            isPlaying = c.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()

            // When playback moves to another song, the screen follows it
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { shownId = it }
            }
        }
        sync()
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    // Progress updates (only meaningful for the playing song)
    LaunchedEffect(controller, isCurrent) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            if (isCurrent) {
                if (!dragging) position = c.currentPosition
                duration = c.duration.coerceAtLeast(0L)
            } else {
                position = 0L
                duration = 0L
            }
            delay(500)
        }
    }

    val totalMs = if (isCurrent && duration > 0) duration else (song?.durationSec ?: 0L) * 1000
    val progress = if (isCurrent && totalMs > 0) position.toFloat() / totalMs else 0f
    val shownPosition = if (dragging) (dragValue * totalMs).toLong() else position

    fun startShownSong() {
        controller?.apply {
            setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
            prepare()
            play()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) {
                Text("←", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        AsyncImage(
            model = song?.thumbnailPath?.let { File(it) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = song?.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = song?.uploader ?: "",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(shownPosition), style = MaterialTheme.typography.bodySmall)

            Slider(
                value = if (dragging) dragValue else progress,
                enabled = isCurrent,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    val target = (dragValue * totalMs).toLong()
                    controller?.seekTo(target)
                    position = target
                    dragging = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Text(formatTime(totalMs), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (isCurrent) controller?.seekToPrevious()
                else songs.getOrNull(index - 1)?.let { shownId = it.id }
            }) {
                Text("⏮", style = MaterialTheme.typography.headlineMedium)
            }

            FilledIconButton(
                onClick = {
                    when {
                        !isCurrent -> startShownSong()
                        isPlaying -> controller?.pause()
                        else -> controller?.play()
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                Text(if (showPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineMedium)
            }

            IconButton(onClick = {
                if (isCurrent) controller?.seekToNext()
                else songs.getOrNull(index + 1)?.let { shownId = it.id }
            }) {
                Text("⏭", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}