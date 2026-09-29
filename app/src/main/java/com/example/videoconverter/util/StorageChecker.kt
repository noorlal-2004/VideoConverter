package com.example.videoconverter.util

import android.content.Context
import android.os.StatFs

object StorageChecker {
    fun availableBytes(context: Context): Long =
        StatFs(context.cacheDir.absolutePath).availableBytes
}