package com.example.androidmusicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Add
//import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.example.androidmusicplayer.data.AppDatabase
import com.example.androidmusicplayer.data.Song
import java.io.File

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onOpenPlayer: (Long) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val songs by dao.getAllSongs().collectAsState(initial = emptyList())
    val controller = rememberController()

    // Track what's playing so cards can show play/pause
    var currentId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            currentId = c.currentMediaItem?.mediaId
            isPlaying = c.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        sync()
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    fun playFrom(index: Int) {
        controller?.apply {
            setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
            prepare()
            play()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        // Search and filters (fixed). Empty placeholder for now.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        HorizontalDivider()

//        Song list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isCurrent = currentId == song.id.toString()
                SongCard(
                    song = song,
                    isPlaying = isCurrent && isPlaying,
                    onClick = {
                        onOpenPlayer(song.id)
                    },
                    onPlayPause = {
                        if (isCurrent) {
                            if (isPlaying) controller?.pause() else controller?.play()
                        } else {
                            playFrom(index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnailPath?.let { File(it) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledIconButton(onClick = onPlayPause) {
                    Text(if (isPlaying) "⏸" else "▶")
                }
            }
        }
    }
}