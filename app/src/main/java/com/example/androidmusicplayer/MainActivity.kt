package com.example.androidmusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.androidmusicplayer.ui.theme.AndroidMusicPlayerTheme

enum class Screen { Library, Download, Player, Playlist }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidMusicPlayerTheme {
                var screen by remember { mutableStateOf(Screen.Library) }
                var selectedSongId by remember { mutableLongStateOf(0L) }
                var selectedPlaylistId by remember { mutableLongStateOf(0L) }
                val pagerState = rememberPagerState(pageCount = { 2 })

                BackHandler(enabled = screen != Screen.Library) { screen = Screen.Library }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val pad = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.Library -> HomeScreen(
                            pagerState = pagerState,
                            modifier = pad,
                            onAddSongs = { screen = Screen.Download },
                            onOpenPlayer = { id ->
                                selectedSongId = id
                                screen = Screen.Player
                            },
                            onOpenPlaylist = { id -> selectedPlaylistId = id; screen = Screen.Playlist }
                        )

                        Screen.Download -> DownloadScreen(
                            modifier = pad,
                            onBack = { screen = Screen.Library }
                        )

                        Screen.Player -> PlayerScreen(
                            songId = selectedSongId,
                            modifier = pad,
                            onBack = { screen = Screen.Library }
                        )

                        Screen.Playlist -> PlaylistDetailScreen(
                            playlistId = selectedPlaylistId,
                            modifier = pad,
                            onBack = { screen = Screen.Library },
                            onOpenPlayer = { id -> selectedSongId = id; screen = Screen.Player }
                        )

                    }
                }
            }
        }
    }
}

