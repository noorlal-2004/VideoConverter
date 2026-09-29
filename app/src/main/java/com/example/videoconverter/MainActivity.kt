package com.example.videoconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.media3.common.util.UnstableApi
import com.example.videoconverter.ui.ConverterScreen
import com.example.videoconverter.ui.theme.VideoConverterTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoConverterTheme {
                Surface { ConverterScreen() }
            }
        }
    }
}