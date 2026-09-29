package com.example.videoconverter.ui


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoconverter.model.*
import com.example.videoconverter.viewmodel.ConversionStatus
import com.example.videoconverter.viewmodel.ConverterViewModel

@UnstableApi
@Composable
fun ConverterScreen(viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onVideoPicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Video Converter", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }) {
            Text(if (state.videoUri == null) "Pick a video" else "Pick another video")
        }

        // Preview: show the converted video once done, otherwise the original
        val previewUri = (state.status as? ConversionStatus.Done)?.outputUri ?: state.videoUri
        previewUri?.let { VideoPlayer(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }

        state.info?.let { InfoCard(it, context) }

        if (state.videoUri != null) {
            OptionRow("Resolution", Resolution.entries, state.settings.resolution, { it.label }) { r ->
                viewModel.updateSettings { it.copy(resolution = r) }
            }
            OptionRow("Quality", Quality.entries, state.settings.quality, { it.label }) { q ->
                viewModel.updateSettings { it.copy(quality = q) }
            }
            OptionRow("Codec", Codec.entries, state.settings.codec, { it.label }) { c ->
                viewModel.updateSettings { it.copy(codec = c) }
            }

            when (val s = state.status) {
                is ConversionStatus.Idle, is ConversionStatus.Failed -> {
                    if (s is ConversionStatus.Failed) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { viewModel.convert() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Convert")
                    }
                }
                is ConversionStatus.Running -> {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Converting… ${(s.progress * 100).toInt()}%")
                    OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                }
                is ConversionStatus.Done -> {
                    Text("Saved to Movies/VideoConverter", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Size: ${Formatter.formatShortFileSize(context, s.inputSize)} → " +
                                Formatter.formatShortFileSize(context, s.outputSize)
                    )
                    Button(onClick = { shareVideo(context, s.outputUri) }) { Text("Share") }
                    OutlinedButton(onClick = { viewModel.convert() }) { Text("Convert again") }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player }
    )
}

@Composable
private fun InfoCard(info: VideoInfo, context: Context) {
    val seconds = info.durationMs / 1000
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${info.width} × ${info.height}")
            Text("Duration: %d:%02d".format(seconds / 60, seconds % 60))
            Text("Size: ${Formatter.formatShortFileSize(context, info.sizeBytes)}")
        }
    }
}

@Composable
private fun <T> OptionRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

private fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}