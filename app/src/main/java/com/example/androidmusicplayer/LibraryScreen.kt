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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.example.androidmusicplayer.data.PlaylistSongCrossRef

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onAddSongs: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val songs by dao.getAllSongs().collectAsState(initial = emptyList())
    val controller = rememberController()
    val scope = rememberCoroutineScope()
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTarget by remember { mutableStateOf<Song?>(null) }

    // Track what's playing so cards can show play/pause
    var currentId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // search states
    var query by remember { mutableStateOf("") }
    val visible = songs.filter { it.title.contains(query, ignoreCase = true) || it.uploader.contains(query, ignoreCase = true) }

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

    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = "Search songs",
                        color = Color.Gray
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onAddSongs) {
//                Icon(Icons.Default.Add, contentDescription = "Download a song")
                Text("*")
            }
        }

        HorizontalDivider()

//        Song list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(visible, key = { _, song -> song.id }) { index, song ->
                SongCard(
                    song = song,
                    onClick = { onOpenPlayer(song.id) },
                    ) { close ->
                    DropdownMenuItem(
                        text = { Text("Delete song") },
                        onClick = { close(); deleteTarget = song }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = { close(); playlistTarget = song }
                    )
                }
            }
        }
        playlistTarget?.let { song ->
            AddToPlaylistDialog(song = song, onDismiss = { playlistTarget = null })
        }

        deleteTarget?.let { song ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                text = { Text("Are you sure?", style = MaterialTheme.typography.titleLarge) },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        controller?.let { c ->
                            for (i in c.mediaItemCount - 1 downTo 0) {
                                if (c.getMediaItemAt(i).mediaId == song.id.toString()) c.removeMediaItem(i)
                            }
                        }
                        scope.launch(Dispatchers.IO) {
                            dao.deleteSong(song)
                            File(song.filePath).delete()
                            song.thumbnailPath?.let { File(it).delete() }
                        }
                    }) { Text("Delete") }
                }
            )
        }
    }
}

@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    menuItems: @Composable (closeMenu: () -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Text("•••", style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    Box(Modifier.widthIn(min = 180.dp)) {
                        IconButton(
                            onClick = { showMenu = false },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) { Text("X") }
                    }
                    menuItems { showMenu = false }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val playlists by dao.getAllPlaylists().collectAsState(initial = emptyList())
    val memberIds by dao.getPlaylistIdsForSong(song.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    fun toggle(playlistId: Long, isChecked: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (isChecked) {
                dao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, song.id))
            } else {
                dao.addSongToPlaylist(playlistId, song.id)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { Text("X") }
                }

                Spacer(Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text("No playlists yet", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            val checked = playlist.id in memberIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle(playlist.id, checked) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { toggle(playlist.id, checked) }
                                )
                                Text(
                                    text = playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}