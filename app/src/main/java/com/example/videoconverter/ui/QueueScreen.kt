package com.example.videoconverter.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videoconverter.model.*
import com.example.videoconverter.viewmodel.ConverterViewModel
import com.example.videoconverter.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun QueueScreen(
    state: UiState,
    viewModel: ConverterViewModel,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue = state.queue
    val context = LocalContext.current

    if (queue.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Queue,
                title = "Nothing in the queue",
                message = "Videos you convert show up here with their progress and results."
            ) {
                FilledTonalButton(onClick = onPick) { Text("Choose videos") }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryCard(
                queue = queue,
                onCancelAll = viewModel::cancelAll,
                onClearFinished = viewModel::clearFinished
            )
        }
        items(queue, key = { it.id }) { item ->
            QueueItemCard(
                item = item,
                context = context,
                onSkip = viewModel::cancelCurrent,
                onRemove = { viewModel.removeItem(item.id) }
            )
        }
    }
}

@Composable
private fun SummaryCard(
    queue: List<QueueItem>,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit
) {
    val running = queue.count { it.status is ConversionStatus.Running }
    val waiting = queue.count { it.status is ConversionStatus.Idle }
    val done = queue.count { it.status is ConversionStatus.Done }
    val failed = queue.count { it.status is ConversionStatus.Failed }

    val summary = listOfNotNull(
        if (running > 0) "$running converting" else null,
        if (waiting > 0) "$waiting waiting" else null,
        if (done > 0) "$done done" else null,
        if (failed > 0) "$failed not converted" else null
    ).joinToString(" · ")

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Queue", style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running + waiting > 0) {
                    OutlinedButton(onClick = onCancelAll) { Text("Cancel all") }
                }
                if (done + failed > 0) {
                    TextButton(onClick = onClearFinished) { Text("Clear finished") }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    context: Context,
    onSkip: () -> Unit,
    onRemove: () -> Unit
) {
    val audioOnly = item.settings.mode == OutputMode.AUDIO

    ElevatedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (audioOnly) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        describe(item.settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusIcon(item.status)
            }

            when (val s = item.status) {
                is ConversionStatus.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Waiting",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onRemove) { Text("Remove") }
                    }
                }
                is ConversionStatus.Running -> {
                    val animated by animateFloatAsState(targetValue = s.progress, label = "progress")
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Converting… ${(s.progress * 100).toInt()}%",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                }
                is ConversionStatus.Done -> {
                    val saved = if (!s.audioOnly && s.inputSize > 0 && s.outputSize < s.inputSize) {
                        " · saved ${((1 - s.outputSize.toDouble() / s.inputSize) * 100).roundToInt()}%"
                    } else ""
                    Text(
                        "${Formatter.formatShortFileSize(context, s.inputSize)} → " +
                                Formatter.formatShortFileSize(context, s.outputSize) + saved,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { openMedia(context, s.outputUri, s.audioOnly) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Open")
                        }
                        IconButton(onClick = { shareMedia(context, s.outputUri, s.audioOnly) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from list")
                        }
                    }
                }
                is ConversionStatus.Failed -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: ConversionStatus) {
    when (status) {
        is ConversionStatus.Idle -> Icon(
            Icons.Rounded.Schedule, contentDescription = "Waiting",
            tint = MaterialTheme.colorScheme.outline
        )
        is ConversionStatus.Running -> Unit
        is ConversionStatus.Done -> Icon(
            Icons.Rounded.CheckCircle, contentDescription = "Done",
            tint = MaterialTheme.colorScheme.primary
        )
        is ConversionStatus.Failed -> Icon(
            Icons.Rounded.Error, contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error
        )
    }
}