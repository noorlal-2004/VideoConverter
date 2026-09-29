package com.example.videoconverter.converter

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.videoconverter.model.ConversionSettings
import com.example.videoconverter.model.VideoInfo
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@UnstableApi
class VideoConverter(private val context: Context) {

    /** Must be called from the main thread (viewModelScope does this). */
    suspend fun convert(
        input: Uri,
        info: VideoInfo,
        settings: ConversionSettings,
        onProgress: (Float) -> Unit
    ): File = suspendCancellableCoroutine { cont ->

        val output = File(context.cacheDir, "converted_${System.currentTimeMillis()}.mp4")

        // Work out the output size and bitrate
        val outHeight = minOf(settings.resolution.height ?: info.height, info.height)
        val outWidth = info.width * outHeight / info.height
        val bitrate = (outWidth * outHeight * 30 * settings.quality.bitsPerPixel)
            .toInt().coerceAtLeast(500_000)

        val videoEffects = buildList<Effect> {
            if (outHeight != info.height) add(Presentation.createForHeight(outHeight))
        }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(bitrate).build()
            )
            .build()

        val mainHandler = Handler(Looper.getMainLooper())

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(settings.codec.mime)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    output.delete()
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        // Poll progress every 250 ms until finished or cancelled
        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (!cont.isActive) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                mainHandler.postDelayed(this, 250)
            }
        }

        cont.invokeOnCancellation {
            mainHandler.post {
                transformer.cancel()
                output.delete()
            }
        }

        transformer.start(editedItem, output.absolutePath)
        mainHandler.post(poll)
    }
}