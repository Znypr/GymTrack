package com.example.gymtrack.feature.notebookimport

import android.content.Context
import android.net.Uri
import com.example.gymtrack.domain.notebookimport.NotebookLineProvenance
import com.example.gymtrack.domain.notebookimport.NotebookRecognitionOutput
import com.example.gymtrack.domain.notebookimport.NotebookRecognitionProviderDescriptor
import com.example.gymtrack.domain.notebookimport.NotebookRecognitionRequest
import com.example.gymtrack.domain.notebookimport.ProcessingLocation
import com.example.gymtrack.domain.notebookimport.RecognizedNotebookLine
import com.example.gymtrack.domain.notebookimport.RecognizedNotebookPage
import com.example.gymtrack.domain.notebookimport.RecognitionConfidence
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitNotebookOcrRecognizer(
    private val context: Context,
) {
    private val descriptor = NotebookRecognitionProviderDescriptor(
        id = "mlkit-text-recognition-latin",
        displayName = "ML Kit Text Recognition",
        processingLocation = ProcessingLocation.ON_DEVICE,
    )

    suspend fun recognize(
        request: NotebookRecognitionRequest,
        pageUrisById: Map<String, Uri>,
    ): NotebookRecognitionOutput {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val warnings = mutableListOf<String>()
        val recognizedPages = request.batch.pages.mapNotNull { page ->
            val uri = pageUrisById[page.id]
            if (uri == null) {
                warnings += "No image URI provided for page ${page.id}"
                null
            } else {
                runCatching {
                    val image = InputImage.fromFilePath(context, uri)
                    val text = recognizer.process(image).await()
                    text.toRecognizedPage(page.id)
                }.getOrElse { error ->
                    warnings += "OCR failed for page ${page.id}: ${error.localizedMessage ?: error::class.java.simpleName}"
                    null
                }
            }
        }
        recognizer.close()
        return NotebookRecognitionOutput(
            provider = descriptor,
            recognizedPages = recognizedPages,
            warnings = warnings,
        )
    }

    private fun Text.toRecognizedPage(pageId: String): RecognizedNotebookPage {
        val lines = textBlocks
            .flatMap { block -> block.lines }
            .mapIndexedNotNull { index, line ->
                val text = line.text.trim()
                if (text.isBlank()) {
                    null
                } else {
                    RecognizedNotebookLine(
                        id = "$pageId-ocr-line-${index + 1}",
                        pageId = pageId,
                        lineNumber = index + 1,
                        text = text,
                        confidence = RecognitionConfidence(0.80),
                    )
                }
            }
        return RecognizedNotebookPage(
            pageId = pageId,
            lines = lines,
            confidence = if (lines.isEmpty()) RecognitionConfidence(0.0) else RecognitionConfidence(0.80),
        )
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
