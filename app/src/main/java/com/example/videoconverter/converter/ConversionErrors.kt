package com.example.videoconverter.converter

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import java.io.IOException

class InsufficientStorageException(message: String) : IOException(message)

@OptIn(UnstableApi::class)
object ConversionErrors {
    fun toMessage(e: Throwable): String = when {
        e is InsufficientStorageException -> e.message ?: "Not enough storage space."

        e is ExportException -> when (e.errorCode) {
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ExportException.ERROR_CODE_IO_NO_PERMISSION ->
                "Couldn't open this video. Try picking it again."

            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FAILED ->
                "This video's format isn't supported on this phone."

            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_ENCODING_FAILED ->
                "This phone couldn't encode with these settings. Try H.264 or a lower resolution."

            ExportException.ERROR_CODE_MUXING_FAILED ->
                "Couldn't write the converted file. Check your free storage."

            else -> "Conversion failed (code ${e.errorCode}). Try different settings."
        }

        e is IOException && (e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                e.message?.contains("No space left", ignoreCase = true) == true) ->
            "Your phone ran out of storage during conversion."

        else -> e.message ?: "Something went wrong during conversion."
    }
}