package com.example.androidmusicplayer

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

import com.example.androidmusicplayer.data.AppDatabase
import com.example.androidmusicplayer.data.Song
import java.io.File

@Composable
fun DownloadScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a URL") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).musicDao() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Text(
            text = "Download a song",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
            .padding(bottom = 25.dp)
        )

        val canDownload = !busy && url.isNotBlank()
        val borderColor = if (canDownload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

        Row(
            modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,

        ) {
            TextField(
                value = url,
                onValueChange = { url = it },
                placeholder = {
                    Text(
                        text = "Paste a YouTube URL",
                        color = Color.Gray
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            VerticalDivider(color = borderColor)

            Box(
               modifier = Modifier
                   .fillMaxHeight()
                   .width(110.dp)
                   .background(
                       if (canDownload) MaterialTheme.colorScheme.primary
                       else Color.Transparent
                   )
                   .clickable(enabled = canDownload) {
                       busy = true
                       progress = 0f
                       status = "Downloading..."
                       scope.launch {
                           try {
                               withContext(Dispatchers.IO) {
                                   val dir =
                                       context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
                                   //                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                   val info = YoutubeDL.getInstance().getInfo(url.trim())

                                   val videoId = info.id
                                   val videoTitle = info.title
                                   if (videoId == null || videoTitle == null) {
                                       withContext(Dispatchers.Main) {
                                           status = "Error: couldn't read video info"
                                       }
                                       return@withContext
                                   }

                                   if (dao.songExists(videoId)) {
                                       withContext(Dispatchers.Main) {
                                           status = "Already downloaded"
                                       }
                                       return@withContext
                                   }

                                   val request = YoutubeDLRequest(url.trim()).apply {
                                       addOption("-f", "bestaudio[ext=m4a]")
                                       addOption("--write-thumbnail")
                                       addOption("--convert-thumbnails", "jpg")
                                       addOption("-o", "${dir.absolutePath}/%(id)s.%(ext)s")
                                   }
                                   YoutubeDL.getInstance().execute(request) { p, _, _ ->
                                       progress = p / 100f
                                   }

                                   val audioFile = File(dir, "${info.id}.m4a")
                                   val thumbFile = File(dir, "${info.id}.jpg")

                                   if (audioFile.exists()) {
                                       dao.insertSong(
                                           Song(
                                               videoId = videoId,
                                               title = videoTitle,
                                               uploader = info.uploader ?: "",
                                               durationSec = (info.duration ?: 0).toLong(),
                                               filePath = audioFile.absolutePath,
                                               thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath,
                                               sourceUrl = url.trim()
                                           )
                                       )
                                       withContext(Dispatchers.Main) { status = "Done" }
                                   } else {
                                       val found =
                                           dir.listFiles()?.joinToString { it.name } ?: "none"
                                       Log.e(
                                           "Download",
                                           "Expected: ${audioFile.absolutePath}\nFound in dir: $found"
                                       )
                                       withContext(Dispatchers.Main) {
                                           status = "Not found: ${audioFile.name}\nFound: $found"
                                       }
                                   }
                               }
                           } catch (e: Exception) {
                               status = "Error: ${e.message}"
                           } finally {
                               busy = false
                           }
                       }
                   },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Download",
                    color = if (canDownload) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        // status field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}