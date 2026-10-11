package com.example.androidmusicplayer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.androidmusicplayer.ui.theme.AndroidMusicPlayerTheme

enum class Screen { Library, Download, Player, Playlist }

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        askNotificationPermission()
        setContent {
            AndroidMusicPlayerTheme {
                var screen by remember { mutableStateOf(Screen.Library) }
                var selectedSongId by remember { mutableLongStateOf(0L) }
                var queuePlaylistId by remember { mutableStateOf<Long?>(null) }
                var selectedPlaylistId by remember { mutableLongStateOf(0L) }
                val pagerState = rememberPagerState(pageCount = { 2 })

                BackHandler(enabled = screen != Screen.Library) {
                    screen = when {
                        screen == Screen.Player && queuePlaylistId != null -> Screen.Playlist
                        else -> Screen.Library
                    }
                }

                LaunchedEffect(sharedUrl) {
                    if (sharedUrl != null) screen = Screen.Download
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val pad = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.Library -> HomeScreen(
                            pagerState = pagerState,
                            modifier = pad,
                            onAddSongs = { screen = Screen.Download },
                            onOpenPlayer = { id ->
                                selectedSongId = id
                                queuePlaylistId = null
                                screen = Screen.Player
                            },
                            onOpenPlaylist = { id -> selectedPlaylistId = id; screen = Screen.Playlist }
                        )

                        Screen.Download -> DownloadScreen(
                            modifier = pad,
                            initialUrl = sharedUrl,
                            onUrlConsumed = { sharedUrl = null },
                            onBack = { screen = Screen.Library }
                        )

                        Screen.Player -> PlayerScreen(
                            songId = selectedSongId,
                            queuePlaylistId = queuePlaylistId,
                            modifier = pad,
                            onBack = { screen = if (queuePlaylistId != null) Screen.Playlist else Screen.Library }
                        )

                        Screen.Playlist -> PlaylistDetailScreen(
                            playlistId = selectedPlaylistId,
                            modifier = pad,
                            onBack = { screen = Screen.Library },
                            onOpenPlayer = {
                                id -> selectedSongId = id;
                                queuePlaylistId = selectedPlaylistId
                                screen = Screen.Player
                            }
                        )

                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            sharedUrl = Regex("https?://\\S+").find(text)?.value
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

