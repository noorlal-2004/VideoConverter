package com.example.videoconverter.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoconverter.converter.ConversionPlan
import com.example.videoconverter.model.*
import com.example.videoconverter.viewmodel.ConverterViewModel

@OptIn(UnstableApi::class)
@Composable
fun ConverterScreen(viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val running = state.status is ConversionStatus.Running

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onVideoPicked(uri) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.convert() }

    val startConversion = {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.convert()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Video Converter", style = MaterialTheme.typography.headlineMedium)

        Button(
            enabled = !running,
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        ) {
            Text(if (state.videoUri == null) "Pick a video" else "Pick another video")
        }

        state.pickError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val previewUri = (state.status as? ConversionStatus.Done)?.outputUri ?: state.videoUri
        previewUri?.let { VideoPlayer(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }

        val info = state.info
        if (info != null) InfoCard(info, context)

        if (state.videoUri != null && info != null) {
            val settings = state.settings
            val audioOnly = settings.mode == OutputMode.AUDIO

            OptionRow(
                "Output", OutputMode.entries, settings.mode,
                label = { it.label }, enabled = { !running }
            ) { m -> viewModel.updateSettings { it.copy(mode = m) } }

            if (!audioOnly) {
                OptionRow(
                    "Preset", Preset.entries,
                    Preset.entries.firstOrNull { it.matches(settings) },
                    label = { it.label }, enabled = { !running }
                ) { p -> viewModel.updateSettings { p.applyTo(it) } }

                OptionRow(
                    "Resolution", Resolution.entries, settings.resolution,
                    label = { it.label }, enabled = { !running }
                ) { r -> viewModel.updateSettings { it.copy(resolution = r) } }

                OptionRow(
                    "Quality", Quality.entries, settings.quality,
                    label = { it.label }, enabled = { !running }
                ) { q -> viewModel.updateSettings { it.copy(quality = q) } }

                OptionRow(
                    "Codec", Codec.entries, settings.codec,
                    label = { it.label },
                    enabled = { !running && (it != Codec.H265 || viewModel.h265Supported) }
                ) { c -> viewModel.updateSettings { it.copy(codec = c) } }
                if (!viewModel.h265Supported) {
                    Text("H.265 isn't available on this phone.", style = MaterialTheme.typography.bodySmall)
                }

                OptionRow<Int?>(
                    "Target size (max)", listOf(null, 10, 25, 50, 100), settings.targetSizeMb,
                    label = { mb -> mb?.let { "$it MB" } ?: "Off" },
                    enabled = { !running }
                ) { mb -> viewModel.updateSettings { it.copy(targetSizeMb = mb) } }
            }

            TrimSection(
                info = info,
                settings = settings,
                enabled = !running,
                onChange = { start, end -> viewModel.updateTrim(start, end) }
            )

            val plan = remember(info, settings) { ConversionPlan.create(info, settings) }
            Text(
                "Estimated size: about ${Formatter.formatShortFileSize(context, plan.estimatedBytes())}",
                style = MaterialTheme.typography.titleSmall
            )
            if (!audioOnly && plan.lowQualityWarning) {
                Text(
                    "This target is very small for this video, so quality will be low. " +
                            "Try a larger target, a lower resolution, or a shorter trim.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (settings.targetSizeMb != null && !audioOnly) {
                Text(
                    "Encoders can overshoot a little, so the result may slightly exceed the target.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        when (val s = state.status) {
            is ConversionStatus.Idle, is ConversionStatus.Failed -> {
                if (s is ConversionStatus.Failed) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                if (state.videoUri != null) {
                    Button(onClick = { startConversion() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s is ConversionStatus.Failed) "Try again" else "Convert")
                    }
                }
            }
            is ConversionStatus.Running -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("Converting… ${(s.progress * 100).toInt()}%")
                Text(
                    "You can leave the app; conversion continues in the background.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
            }
            is ConversionStatus.Done -> {
                val where = if (s.audioOnly) "Music/VideoConverter" else "Movies/VideoConverter"
                Text("Saved to $where", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Size: ${Formatter.formatShortFileSize(context, s.inputSize)} → " +
                            Formatter.formatShortFileSize(context, s.outputSize)
                )
                Button(onClick = { shareMedia(context, s.outputUri, s.audioOnly) }) { Text("Share") }
                if (state.videoUri != null) {
                    OutlinedButton(onClick = { startConversion() }) { Text("Convert again") }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimSection(
    info: VideoInfo,
    settings: ConversionSettings,
    enabled: Boolean,
    onChange: (Long, Long) -> Unit
) {
    val start = settings.trimStartMs
    val end = settings.effectiveEndMs(info)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trim", style = MaterialTheme.typography.titleSmall)
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { range -> onChange(range.start.toLong(), range.endInclusive.toLong()) },
            valueRange = 0f..info.durationMs.toFloat(),
            enabled = enabled
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(start), style = MaterialTheme.typography.bodySmall)
            Text("Length ${formatTime(end - start)}", style = MaterialTheme.typography.bodySmall)
            Text(formatTime(end), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(UnstableApi::class)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${info.width} × ${info.height}")
            Text("Duration: ${formatTime(info.durationMs)}")
            Text("Size: ${Formatter.formatShortFileSize(context, info.sizeBytes)}")
            if (!info.hasAudio) Text("No audio track")
        }
    }
}

@Composable
private fun <T> OptionRow(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled(option),
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

private fun shareMedia(context: Context, uri: Uri, audioOnly: Boolean) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (audioOnly) "audio/mp4" else "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}