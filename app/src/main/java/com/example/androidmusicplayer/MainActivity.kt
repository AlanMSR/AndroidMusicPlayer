package com.example.androidmusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.androidmusicplayer.ui.theme.AndroidMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidMusicPlayerTheme {
                var showDownload by remember { mutableStateOf(false) }

                BackHandler(enabled = showDownload) { showDownload = false }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showDownload) {
                        DownloadScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { showDownload = false }
                        )
                    } else {
                        LibraryScreen(
                            modifier = Modifier.padding(innerPadding),
                            onAddSongs = { showDownload = true }
                        )
                    }
                }
            }
        }
    }
}
