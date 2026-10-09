package com.example.androidmusicplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onAddSongs: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenPlayer: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val title = if (pagerState.currentPage == 0) "Songs" else "Playlists"

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Menu, contentDescription = "Settings")
            }
        }

        HorizontalDivider()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> LibraryScreen(onOpenPlayer = onOpenPlayer, onAddSongs = onAddSongs)
                else -> PlaylistsScreen(onOpenPlaylist = onOpenPlaylist)
            }
        }
    }
}