package com.example.androidmusicplayer

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidmusicplayer.ui.theme.AndroidMusicPlayerTheme
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidMusicPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloadScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(modifier: Modifier = Modifier) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a URL") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !busy && url.isNotBlank(),
            onClick = {
                busy = true
                progress = 0f
                status = "Downloading..."
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
//                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val request = YoutubeDLRequest(url.trim()).apply {
                                addOption("-f", "bestaudio[ext=m4a]")
                                addOption("-o", "${dir.absolutePath}/%(title)s.%(ext)s")
                            }
                            YoutubeDL.getInstance().execute(request) { p, _, _ ->
                                progress = p / 100f
                            }
                        }
                        status = "Done"
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text("Download") }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(status)
    }
}

//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    AndroidMusicPlayerTheme {
//        Greeting("Android")
//    }
//}