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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.videoconverter.viewmodel.PickedVideo

@OptIn(UnstableApi::class)
@Composable
fun ConverterScreen(viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.onVideosPicked(uris) }

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

    val picked = state.picked
    val settings = state.settings
    val queue = state.queue
    val audioOnly = settings.mode == OutputMode.AUDIO
    val queueActive = queue.any {
        it.status is ConversionStatus.Running || it.status is ConversionStatus.Idle
    }
    val queueFinished = queue.any {
        it.status is ConversionStatus.Done || it.status is ConversionStatus.Failed
    }

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
            Text(if (picked.isEmpty()) "Pick videos" else "Pick different videos")
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (picked.isNotEmpty()) {
            val single = picked.singleOrNull()
            if (single != null) {
                VideoPlayer(single.uri, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                InfoCard(single.info, context)
            } else {
                SelectedList(picked, context)
            }

            OptionRow(
                "Output", OutputMode.entries, settings.mode,
                label = { it.label }
            ) { m -> viewModel.updateSettings { it.copy(mode = m) } }

            if (!audioOnly) {
                OptionRow(
                    "Preset", Preset.entries,
                    Preset.entries.firstOrNull { it.matches(settings) },
                    label = { it.label }
                ) { p -> viewModel.updateSettings { p.applyTo(it) } }

                OptionRow(
                    "Resolution", Resolution.entries, settings.resolution,
                    label = { it.label }
                ) { r -> viewModel.updateSettings { it.copy(resolution = r) } }

                OptionRow(
                    "Quality", Quality.entries, settings.quality,
                    label = { it.label }
                ) { q -> viewModel.updateSettings { it.copy(quality = q) } }

                OptionRow(
                    "Codec", Codec.entries, settings.codec,
                    label = { it.label },
                    enabled = { it != Codec.H265 || viewModel.h265Supported }
                ) { c -> viewModel.updateSettings { it.copy(codec = c) } }
                if (!viewModel.h265Supported) {
                    Text("H.265 isn't available on this phone.", style = MaterialTheme.typography.bodySmall)
                }

                OptionRow<Int?>(
                    "Target size (max, per video)", listOf(null, 10, 25, 50, 100), settings.targetSizeMb,
                    label = { mb -> mb?.let { "$it MB" } ?: "Off" }
                ) { mb -> viewModel.updateSettings { it.copy(targetSizeMb = mb) } }
            }

            if (single != null) {
                TrimSection(
                    info = single.info,
                    settings = settings,
                    enabled = true,
                    onChange = { start, end -> viewModel.updateTrim(start, end) }
                )
            } else {
                Text(
                    "Trim and preview are available when you pick a single video.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val plans = remember(picked, settings) {
                picked.map { ConversionPlan.create(it.info, settings) }
            }
            val totalBytes = plans.sumOf { it.estimatedBytes() }
            Text(
                "Estimated output: about ${Formatter.formatShortFileSize(context, totalBytes)}" +
                        if (picked.size > 1) " in total" else "",
                style = MaterialTheme.typography.titleSmall
            )
            if (!audioOnly && plans.any { it.lowQualityWarning }) {
                Text(
                    "This target is very small for at least one video, so quality will be low. " +
                            "Try a larger target or a lower resolution.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(onClick = { startConversion() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picked.size == 1) "Convert" else "Convert ${picked.size} videos")
            }
        }

        if (queue.isNotEmpty()) {
            Text("Queue", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (queueActive) {
                    OutlinedButton(onClick = { viewModel.cancelAll() }) { Text("Cancel all") }
                }
                if (queueFinished) {
                    OutlinedButton(onClick = { viewModel.clearFinished() }) { Text("Clear finished") }
                }
            }
            queue.forEach { item ->
                key(item.id) {
                    QueueItemRow(
                        item = item,
                        context = context,
                        onSkip = { viewModel.cancelCurrent() },
                        onRemove = { viewModel.removeItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    item: QueueItem,
    context: Context,
    onSkip: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(describe(item.settings), style = MaterialTheme.typography.bodySmall)

            when (val s = item.status) {
                is ConversionStatus.Idle -> {
                    Text("Waiting…")
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
                is ConversionStatus.Running -> {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Converting… ${(s.progress * 100).toInt()}%")
                    TextButton(onClick = onSkip) { Text("Skip this one") }
                }
                is ConversionStatus.Done -> {
                    Text(
                        "Done: ${Formatter.formatShortFileSize(context, s.inputSize)} → " +
                                Formatter.formatShortFileSize(context, s.outputSize)
                    )
                    Row {
                        TextButton(onClick = { openMedia(context, s.outputUri, s.audioOnly) }) { Text("Open") }
                        TextButton(onClick = { shareMedia(context, s.outputUri, s.audioOnly) }) { Text("Share") }
                        TextButton(onClick = onRemove) { Text("Remove") }
                    }
                }
                is ConversionStatus.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

private fun describe(s: ConversionSettings): String =
    if (s.mode == OutputMode.AUDIO) {
        "Audio only (M4A)"
    } else {
        listOfNotNull(
            s.resolution.label,
            s.quality.label,
            s.codec.label,
            s.targetSizeMb?.let { "max $it MB" }
        ).joinToString(" · ")
    }

@Composable
private fun SelectedList(picked: List<PickedVideo>, context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${picked.size} videos selected", style = MaterialTheme.typography.titleSmall)
            picked.forEach { v ->
                Text(
                    "${v.name}  ·  ${formatTime(v.info.durationMs)}  ·  " +
                            Formatter.formatShortFileSize(context, v.info.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

private fun openMedia(context: Context, uri: Uri, audioOnly: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (audioOnly) "audio/mp4" else "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}