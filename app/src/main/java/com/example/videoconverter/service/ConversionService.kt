package com.example.videoconverter.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.example.videoconverter.converter.ConversionErrors
import com.example.videoconverter.converter.QueueManager
import com.example.videoconverter.converter.VideoConverter
import com.example.videoconverter.model.*
import com.example.videoconverter.util.MediaStoreSaver
import kotlinx.coroutines.*

@OptIn(UnstableApi::class)
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var queueJob: Job? = null
    private var itemJob: Job? = null
    private var canceledAll = false
    private lateinit var converter: VideoConverter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        converter = VideoConverter(this)
        ConversionNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startQueue()
            ACTION_CANCEL_CURRENT -> itemJob?.cancel()
            ACTION_CANCEL -> {
                canceledAll = true
                queueJob?.cancel()
            }
        }
        // A cancel command can arrive when nothing is running; don't leave an idle service behind
        if (queueJob?.isActive != true) stopSelf()
        return START_NOT_STICKY
    }

    private fun startQueue() {
        // Must call startForeground quickly after startForegroundService()
        ServiceCompat.startForeground(
            this, ConversionNotifications.ID_PROGRESS,
            ConversionNotifications.progress(this, "Converting video", 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        // Already running: the loop below will pick up any newly added items
        if (queueJob?.isActive == true) return

        canceledAll = false
        ConversionNotifications.clearResult(this)

        queueJob = scope.launch {
            val processed = mutableSetOf<Long>()
            try {
                while (true) {
                    val item = QueueManager.nextPending() ?: break
                    val index = processed.size + 1
                    val total = processed.size + QueueManager.pendingCount()
                    processed += item.id

                    val job = launch { processItem(item, index, total) }
                    itemJob = job
                    job.join()
                }
            } catch (e: CancellationException) {
                QueueManager.cancelPending()
                throw e
            } finally {
                itemJob = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (!canceledAll) showSummary(processed)
                stopSelf()
            }
        }
    }

    private suspend fun processItem(item: QueueItem, index: Int, total: Int) {
        val audioOnly = item.settings.mode == OutputMode.AUDIO
        val title = if (total > 1) "Converting $index of $total" else "Converting video"
        var lastPercent = -1
        try {
            QueueManager.setStatus(item.id, ConversionStatus.Running(0f))
            ConversionNotifications.updateProgress(this, title, 0)

            val file = converter.convert(item.uri, item.info, item.settings) { p ->
                QueueManager.setStatus(item.id, ConversionStatus.Running(p))
                val percent = (p * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    ConversionNotifications.updateProgress(this, title, percent)
                }
            }

            val outputSize = file.length()
            val savedUri = withContext(Dispatchers.IO) {
                MediaStoreSaver.save(this@ConversionService, file, audioOnly)
                    .also { file.delete() }
            }
            QueueManager.setStatus(
                item.id,
                ConversionStatus.Done(savedUri, item.info.sizeBytes, outputSize, audioOnly)
            )
        } catch (e: CancellationException) {
            QueueManager.setStatus(item.id, ConversionStatus.Failed(QueueManager.CANCELED))
            throw e
        } catch (e: Exception) {
            QueueManager.setStatus(item.id, ConversionStatus.Failed(ConversionErrors.toMessage(e)))
        }
    }

    private fun showSummary(processed: Set<Long>) {
        val results = QueueManager.items.value.filter { it.id in processed }
        if (results.isEmpty()) return

        if (results.size == 1) {
            when (val s = results[0].status) {
                is ConversionStatus.Done -> ConversionNotifications.showResult(
                    this, "Conversion complete",
                    if (s.audioOnly) "Saved to Music/VideoConverter" else "Saved to Movies/VideoConverter"
                )
                is ConversionStatus.Failed ->
                    if (s.message != QueueManager.CANCELED) {
                        ConversionNotifications.showResult(this, "Conversion failed", s.message)
                    }
                else -> Unit
            }
        } else {
            val done = results.count { it.status is ConversionStatus.Done }
            val failed = results.count {
                val s = it.status
                s is ConversionStatus.Failed && s.message != QueueManager.CANCELED
            }
            ConversionNotifications.showResult(this, "Queue finished", "$done converted, $failed failed")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.videoconverter.START"
        const val ACTION_CANCEL = "com.example.videoconverter.CANCEL"
        const val ACTION_CANCEL_CURRENT = "com.example.videoconverter.CANCEL_CURRENT"

        fun start(context: Context) {
            val intent = Intent(context, ConversionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelAll(context: Context) {
            context.startService(
                Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL)
            )
        }

        fun cancelCurrent(context: Context) {
            context.startService(
                Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL_CURRENT)
            )
        }
    }
}