package com.example.androidmusicplayer

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            YoutubeDL.getInstance().init(this@App)
            FFmpeg.getInstance().init(this@App)
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this@App, YoutubeDL.UpdateChannel._NIGHTLY)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}