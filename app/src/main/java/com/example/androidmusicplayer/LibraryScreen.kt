package com.example.androidmusicplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidmusicplayer.data.AppDatabase

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, onAddSongs: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }
    val songs by dao.getAllSongs().collectAsState(initial = emptyList())
    val controller = rememberController()

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                controller?.stop()
                controller?.clearMediaItems()
            }) { Text("Stop") }

            Button(onClick = onAddSongs) { Text("Add songs") }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(songs) { index, song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            controller?.apply {
                                setMediaItems(songs.map { it.toMediaItem() }, index, 0L)
                                prepare()
                                play()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge)
                    Text(song.uploader, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}