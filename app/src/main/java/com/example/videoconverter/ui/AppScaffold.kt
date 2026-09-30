@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.videoconverter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videoconverter.model.ConversionStatus
import com.example.videoconverter.viewmodel.ConverterViewModel
import androidx.compose.foundation.layout.padding
enum class Tab(val label: String, val icon: ImageVector) {
    CONVERT("Convert", Icons.Rounded.VideoLibrary),
    QUEUE("Queue", Icons.Rounded.Queue)
}

@Composable
fun AppScaffold(viewModel: ConverterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.CONVERT) }
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onVideosPicked(uris)
            tab = Tab.CONVERT
        }
    }
    val onPick = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    // Ask for notification permission (Android 13+), then convert either way
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.convert() }

    val onConvert = {
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

    // "Added to queue" snackbar with a shortcut to the queue
    LaunchedEffect(Unit) {
        viewModel.events.collect { text ->
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = "View queue",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) tab = Tab.QUEUE
        }
    }

    val activeCount = state.queue.count {
        it.status is ConversionStatus.Running || it.status is ConversionStatus.Idle
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Video Converter", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.QUEUE && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text("$activeCount") } }) {
                                    Icon(t.icon, contentDescription = null)
                                }
                            } else {
                                Icon(t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.CONVERT -> ConvertScreen(state, viewModel, onPick, onConvert, Modifier.padding(padding))
            Tab.QUEUE -> QueueScreen(state, viewModel, onPick, Modifier.padding(padding))
        }
    }
}