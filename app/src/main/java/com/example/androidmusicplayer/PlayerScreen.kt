package com.example.androidmusicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.example.androidmusicplayer.data.AppDatabase
import com.example.androidmusicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var repeatOne by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val isCurrent = playingId == shownId.toString()
    val showPlaying = isCurrent && isPlaying

    // to not cover the text field
    val imeVisible = WindowInsets.isImeVisible

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            playingId = c.currentMediaItem?.mediaId
            isPlaying = c.isPlaying
            repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
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

    val scope = rememberCoroutineScope()

    fun startShownSong() {
        scope.launch { QueueManager.play(queuePlaylistId, shownId, shuffle = false) }
    }

    fun save(updated: Song, write: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { write() }
            // Refresh the notification / queue metadata if this song is loaded
            controller?.let { c ->
                for (i in 0 until c.mediaItemCount) {
                    if (c.getMediaItemAt(i).mediaId == updated.id.toString()) {
                        c.replaceMediaItem(i, updated.toMediaItem(c.getMediaItemAt(i).queuePos))
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        Spacer(Modifier.weight(1f))
        if (!imeVisible) {
            AsyncImage(
                model = song?.thumbnailPath?.let { File(it) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.height(24.dp))
        }

        song?.let { s ->
            EditableText(
                text = s.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                onCommit = { v -> save(s.copy(title = v)) { dao.updateTitle(s.id, v) } }
            )
            Spacer(Modifier.height(4.dp))
            EditableText(
                text = s.uploader,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                onCommit = { v -> save(s.copy(uploader = v)) { dao.updateUploader(s.id, v) } }
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(shownPosition), style = MaterialTheme.typography.bodySmall)

            val activeColor = MaterialTheme.colorScheme.onSurface
            val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

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
                    .padding(horizontal = 8.dp),
                thumb = {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(
                                if (isCurrent) activeColor else activeColor.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                },
                track = { sliderState ->
                    val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                    val fraction = ((sliderState.value - sliderState.valueRange.start) / range)
                        .coerceIn(0f, 1f)

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(inactiveColor, RoundedCornerShape(50))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(activeColor, RoundedCornerShape(50))
                        )
                    }
                }
            )

            Text(formatTime(totalMs), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                enabled = isCurrent,
                onClick = {
                    controller?.repeatMode =
                        if (repeatOne) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                val active = repeatOne && isCurrent
                Icon(
                    imageVector = if (active) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (active) Color(0xFF2196F3)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isCurrent) controller?.seekToPrevious()
                    else songs.getOrNull(index - 1)?.let { shownId = it.id }
                }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp)
                    )
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
                    Icon(
                        imageVector = if (showPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (showPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = {
                    if (isCurrent) controller?.seekToNext()
                    else songs.getOrNull(index + 1)?.let { shownId = it.id }
                }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditableText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    onCommit: (String) -> Unit
) {
    var editing by remember(text) { mutableStateOf(false) }
    var draft by remember(text) { mutableStateOf(text) }
    val focusRequester = remember { FocusRequester() }

    if (editing) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = style.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val value = draft.trim()
                if (value.isNotBlank() && value != text) onCommit(value)
                editing = false
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    } else {
        Text(
            text = text,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = true }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}