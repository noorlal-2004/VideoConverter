package com.example.videoconverter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.videoconverter.MainActivity

object ConversionNotifications {
    private const val CHANNEL_ID = "conversion"
    const val ID_PROGRESS = 1
    private const val ID_RESULT = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Video conversion", NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun progress(context: Context, title: String, percent: Int): Notification {
        val cancel = PendingIntent.getService(
            context, 1,
            Intent(context, ConversionService::class.java).setAction(ConversionService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Cancel all", cancel)
            .build()
    }

    fun updateProgress(context: Context, title: String, percent: Int) {
        context.getSystemService(NotificationManager::class.java)
            .notify(ID_PROGRESS, progress(context, title, percent))
    }

    fun showResult(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_RESULT, notification)
    }

    fun clearResult(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_RESULT)
    }
}