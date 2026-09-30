@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.videoconverter.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videoconverter.converter.ConversionPlan
import com.example.videoconverter.model.*
import com.example.videoconverter.viewmodel.ConverterViewModel
import com.example.videoconverter.viewmodel.PickedVideo
import com.example.videoconverter.viewmodel.UiState

@Composable
fun ConvertScreen(
    state: UiState,
    viewModel: ConverterViewModel,
    onPick: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.picked.isEmpty()) {
        EmptyPick(state.message, viewModel::dismissMessage, onPick, modifier)
    } else {
        PickedContent(state, viewModel, onPick, onConvert, modifier)
    }
}

@Composable
private fun EmptyPick(
    message: String?,
    onDismiss: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier
) {
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            message?.let { MessageBanner(it, onDismiss) }
            EmptyState(
                icon = Icons.Rounded.VideoLibrary,
                title = "Convert your videos",
                message = "Compress, trim, or pull the audio out of any video. " +
                        "Everything happens on your phone."
            ) {
                Button(onClick = onPick) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose videos")
                }
            }
        }
    }
}

@Composable
private fun PickedContent(
    state: UiState,
    viewModel: ConverterViewModel,
    onPick: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val picked = state.picked
    val settings = state.settings
    val single = picked.singleOrNull()
    val audioOnly = settings.mode == OutputMode.AUDIO

    val plans = remember(picked, settings) {
        picked.map { ConversionPlan.create(it.info, settings) }
    }
    val totalBytes = plans.sumOf { it.estimatedBytes() }

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.message?.let { MessageBanner(it, viewModel::dismissMessage) }

            if (single != null) {
                SingleVideoCard(single, context, onPick, viewModel::clearSelection)
            } else {
                MultiVideoCard(picked, context, onPick, viewModel::clearSelection)
            }

            OutputToggle(settings.mode) { mode ->
                viewModel.updateSettings { it.copy(mode = mode) }
            }

            if (audioOnly) {
                Text(
                    "Saved as an M4A audio file (AAC).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                PresetSection(picked, settings, context) { preset ->
                    viewModel.updateSettings { preset.applyTo(it) }
                }
            }

            if (single != null) {
                TrimCard(single.info, settings) { start, end -> viewModel.updateTrim(start, end) }
            }

            if (!audioOnly) {
                AdvancedSection(settings, viewModel)
            }
        }

        ActionBar(
            count = picked.size,
            totalBytes = totalBytes,
            showWarning = !audioOnly && plans.any { it.lowQualityWarning },
            context = context,
            onConvert = onConvert
        )
    }
}

@Composable
private fun SingleVideoCard(
    video: PickedVideo,
    context: Context,
    onChange: () -> Unit,
    onClear: () -> Unit
) {
    val info = video.info
    val ratio = (info.width.toFloat() / info.height).coerceIn(0.75f, 1.78f)

    Card(Modifier.fillMaxWidth()) {
        VideoPlayer(video.uri, Modifier.fillMaxWidth().aspectRatio(ratio))
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    video.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${info.width}×${info.height} · ${formatTime(info.durationMs)} · " +
                            Formatter.formatShortFileSize(context, info.sizeBytes) +
                            if (!info.hasAudio) " · no audio" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
            }
        }
    }
}

@Composable
private fun MultiVideoCard(
    picked: List<PickedVideo>,
    context: Context,
    onChange: () -> Unit,
    onClear: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${picked.size} videos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onChange) { Text("Change") }
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                }
            }
            picked.take(4).forEach { v ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Movie, contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            v.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatTime(v.info.durationMs)} · " +
                                    Formatter.formatShortFileSize(context, v.info.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (picked.size > 4) {
                Text("+ ${picked.size - 4} more", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Trim is available when you choose a single video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OutputToggle(selected: OutputMode, onSelect: (OutputMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        OutputMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, OutputMode.entries.size)
            ) {
                Text(mode.label)
            }
        }
    }
}

@Composable
private fun PresetSection(
    picked: List<PickedVideo>,
    settings: ConversionSettings,
    context: Context,
    onSelect: (Preset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose a quality", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Preset.entries.forEach { preset ->
                val estimate = picked.sumOf {
                    ConversionPlan.create(it.info, preset.applyTo(settings)).estimatedBytes()
                }
                PresetCard(
                    preset = preset,
                    selected = preset.matches(settings),
                    estimate = Formatter.formatShortFileSize(context, estimate),
                    onClick = { onSelect(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    estimate: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                minLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text("≈ $estimate", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}

@Composable
private fun TrimCard(
    info: VideoInfo,
    settings: ConversionSettings,
    onChange: (Long, Long) -> Unit
) {
    val start = settings.trimStartMs
    val end = settings.effectiveEndMs(info)

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trim", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Length ${formatTime(end - start)}", style = MaterialTheme.typography.bodySmall)
            }
            RangeSlider(
                value = start.toFloat()..end.toFloat(),
                onValueChange = { range -> onChange(range.start.toLong(), range.endInclusive.toLong()) },
                valueRange = 0f..info.durationMs.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(start), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(end), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AdvancedSection(settings: ConversionSettings, viewModel: ConverterViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Advanced options",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                        Text(
                            "H.265 isn't available on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OptionRow<Int?>(
                        "Target size (max, per video)",
                        listOf(null, 10, 25, 50, 100),
                        settings.targetSizeMb,
                        label = { mb -> mb?.let { "$it MB" } ?: "Off" }
                    ) { mb -> viewModel.updateSettings { it.copy(targetSizeMb = mb) } }
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    count: Int,
    totalBytes: Long,
    showWarning: Boolean,
    context: Context,
    onConvert: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showWarning) {
                Text(
                    "Very small target: quality will be low. Try a larger size or a lower resolution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (count > 1) "Estimated total" else "Estimated size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "≈ ${Formatter.formatShortFileSize(context, totalBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(onClick = onConvert) {
                    Text(if (count == 1) "Convert" else "Convert $count videos")
                }
            }
        }
    }
}