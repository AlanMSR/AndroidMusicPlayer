package com.example.androidmusicplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidmusicplayer.data.AppDatabase
import com.example.androidmusicplayer.data.PlaylistSongCrossRef
import com.example.androidmusicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val playlist by dao.getPlaylist(playlistId).collectAsState(initial = null)
    val songs by dao.getSongsInPlaylist(playlistId).collectAsState(initial = emptyList())
    val controller = rememberController()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    val focusRequester = remember { FocusRequester() }

    val visible = songs.filter {
        it.title.contains(query, ignoreCase = true) ||
                it.uploader.contains(query, ignoreCase = true)
    }

    fun commitName() {
        val newName = draft.trim()
        if (newName.isNotBlank() && newName != playlist?.name) {
            scope.launch(Dispatchers.IO) { dao.renamePlaylist(playlistId, newName) }
        }
        editing = false
    }

    fun play(shuffle: Boolean) {
        if (songs.isEmpty()) return
        controller?.apply {
            shuffleModeEnabled = shuffle
            val start = if (shuffle) songs.indices.random() else 0
            setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
            prepare()
            play()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Back button
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("←", style = MaterialTheme.typography.headlineMedium)
        }

        // Name (tap to edit) + delete button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 25) draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
            } else {
                Text(
                    text = playlist?.name ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            draft = playlist?.name ?: ""
                            editing = true
                        }
                )
            }
            IconButton(onClick = { showDelete = true }) { Text("🗑") }
        }

        Text(
            text = "${songs.size} songs",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Play all + random
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(onClick = { play(shuffle = false) }) { Text("▶") }
            FilledTonalIconButton(onClick = { play(shuffle = true) }) { Text("🎲") }
        }

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Scrollable songs
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visible, key = { it.id }) { song ->
                SongCard(
                    song = song,
                    onClick = { onOpenPlayer(song.id) }
                ) { close ->
                    DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            close()
                            scope.launch(Dispatchers.IO) {
                                dao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, song.id))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = { close(); playlistTarget = song }
                    )
                }
            }
        }
    }

    playlistTarget?.let { song ->
        AddToPlaylistDialog(song = song, onDismiss = { playlistTarget = null })
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            text = {
                Column {
                    Text(
                        text = "Are you sure?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("This operation can not be undone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    playlist?.let { p -> scope.launch(Dispatchers.IO) { dao.deletePlaylist(p) } }
                    onBack()
                }) { Text("Delete") }
            }
        )
    }
}