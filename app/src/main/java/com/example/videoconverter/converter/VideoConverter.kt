package com.example.videoconverter.converter

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.example.videoconverter.model.OutputMode
import com.example.videoconverter.model.VideoInfo
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(UnstableApi::class)
class VideoConverter(private val context: Context) {

    /** Must be called from the main thread. */
    suspend fun convert(
        input: Uri,
        info: VideoInfo,
        settings: ConversionSettings,
        onProgress: (Float) -> Unit
    ): File = suspendCancellableCoroutine { cont ->

        val audioOnly = settings.mode == OutputMode.AUDIO
        val plan = ConversionPlan.create(info, settings)
        val extension = if (audioOnly) "m4a" else "mp4"
        val output = File(context.cacheDir, "converted_${System.currentTimeMillis()}.$extension")

        // Input, with optional trim
        val mediaItemBuilder = MediaItem.Builder().setUri(input)
        if (settings.isTrimmed(info)) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(settings.trimStartMs)
                    .setEndPositionMs(settings.effectiveEndMs(info))
                    .build()
            )
        }
        val mediaItem = mediaItemBuilder.build()

        // Edits: drop video for audio-only, otherwise scale if needed
        val editedBuilder = EditedMediaItem.Builder(mediaItem)
        if (audioOnly) {
            editedBuilder.setRemoveVideo(true)
        } else {
            val videoEffects = buildList<Effect> {
                if (plan.outHeight != info.height) add(Presentation.createForHeight(plan.outHeight))
            }
            editedBuilder.setEffects(Effects(emptyList(), videoEffects))
        }
        val editedItem = editedBuilder.build()

        // Encoders
        val transformerBuilder = Transformer.Builder(context)
        if (audioOnly) {
            transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
        } else {
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build()
                )
                .build()
            transformerBuilder
                .setVideoMimeType(settings.codec.mime)
                .setEncoderFactory(encoderFactory)
        }

        val mainHandler = Handler(Looper.getMainLooper())

        val transformer = transformerBuilder
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