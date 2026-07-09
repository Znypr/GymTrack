package com.example.gymtrack.feature.notebookimport

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.notebookimport.NotebookExerciseMatcher
import com.example.gymtrack.domain.notebookimport.NotebookImportBatchDraft
import com.example.gymtrack.domain.notebookimport.NotebookImportBatchState
import com.example.gymtrack.domain.notebookimport.NotebookImportBatchStatus
import com.example.gymtrack.domain.notebookimport.NotebookImportReviewQueueBuilder
import com.example.gymtrack.domain.notebookimport.NotebookImportSessionSummary
import com.example.gymtrack.domain.notebookimport.NotebookImportSessionSummaryBuilder
import com.example.gymtrack.domain.notebookimport.NotebookPageIntake
import com.example.gymtrack.domain.notebookimport.NotebookPageProcessingState
import com.example.gymtrack.domain.notebookimport.NotebookPageProcessingStatus
import com.example.gymtrack.domain.notebookimport.NotebookPageSource
import com.example.gymtrack.domain.notebookimport.NotebookRecognitionOutput
import com.example.gymtrack.domain.notebookimport.NotebookRecognitionRequest
import com.example.gymtrack.domain.notebookimport.NotebookReviewQueue
import com.example.gymtrack.domain.notebookimport.NotebookTextInterpreter
import com.example.gymtrack.domain.notebookimport.NotebookWorkoutDuplicateDetector
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface NotebookImportUiState {
    data object Idle : NotebookImportUiState
    data object Processing : NotebookImportUiState
    data class Result(
        val summary: NotebookImportSessionSummary,
        val recognitionOutput: NotebookRecognitionOutput,
        val reviewQueue: NotebookReviewQueue,
        val warnings: List<String>,
    ) : NotebookImportUiState
    data class Error(val message: String) : NotebookImportUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookImportScreen(
    onBack: () -> Unit,
    exerciseCatalog: List<Exercise> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state: NotebookImportUiState by remember { mutableStateOf(NotebookImportUiState.Idle) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun processUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            state = NotebookImportUiState.Processing
            state = runCatching {
                processNotebookImages(
                    context = context.applicationContext,
                    uris = uris,
                    exerciseCatalog = exerciseCatalog,
                )
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    NotebookImportUiState.Error(error.localizedMessage ?: error::class.java.simpleName)
                },
            )
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> processUris(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            processUris(listOf(uri))
        } else if (!success) {
            Toast.makeText(context, "Camera capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Notebook import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Import handwritten workout pages",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    text = "Pick notebook photos from your gallery or capture one with the camera. OCR runs on device. Nothing is written to workout history from this screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { galleryLauncher.launch(arrayOf("image/*")) },
                        enabled = state !is NotebookImportUiState.Processing,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Text("Gallery")
                    }
                    OutlinedButton(
                        onClick = {
                            val uri = createNotebookCaptureUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        enabled = state !is NotebookImportUiState.Processing,
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text("Camera")
                    }
                }
            }

            when (val current = state) {
                NotebookImportUiState.Idle -> item {
                    StatusCard(
                        title = "No pages selected",
                        body = "Use this as a review-first intake step. Recognized rows remain draft data until a later review UI confirms them.",
                    )
                }
                NotebookImportUiState.Processing -> item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            CircularProgressIndicator()
                            Text("Running OCR and building review draft…")
                        }
                    }
                }
                is NotebookImportUiState.Error -> item {
                    StatusCard(
                        title = "Import failed",
                        body = current.message,
                        isError = true,
                    )
                }
                is NotebookImportUiState.Result -> {
                    item { SummaryCard(current.summary) }
                    if (current.reviewQueue.items.isNotEmpty()) {
                        item {
                            Text(
                                text = "Review queue",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(current.reviewQueue.items) { reviewItem ->
                            StatusCard(
                                title = reviewItem.label,
                                body = buildString {
                                    reviewItem.currentValue?.let { append("Value: $it\n") }
                                    reviewItem.confidence?.let { append("Confidence: ${"%.0f".format(it.value * 100)}%\n") }
                                    reviewItem.pageId?.let { pageId ->
                                        append("Source: $pageId")
                                        reviewItem.lineNumber?.let { append(":$it") }
                                    }
                                    if (isBlank()) {
                                        append("Needs explicit confirmation before import.")
                                    }
                                },
                            )
                        }
                    }
                    if (current.warnings.isNotEmpty()) {
                        item {
                            StatusCard(
                                title = "Warnings",
                                body = current.warnings.joinToString(separator = "\n"),
                            )
                        }
                    }
                    item {
                        StatusCard(
                            title = "Recognized text",
                            body = current.recognitionOutput.recognizedPages.joinToString("\n\n") { page ->
                                buildString {
                                    append("Page ${page.pageId}")
                                    page.lines.forEach { line -> append("\n${line.lineNumber}. ${line.text}") }
                                }
                            }.ifBlank { "No text lines recognized." },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: NotebookImportSessionSummary) {
    StatusCard(
        title = "Draft summary",
        body = listOf(
            "Pages: ${summary.processedPageCount}/${summary.pageCount} processed",
            "Workouts: ${summary.workoutCount}",
            "Exercises: ${summary.exerciseCount}",
            "Sets: ${summary.setCount}",
            "Review items: ${summary.reviewItemCount}",
            "Draft duplicates: ${summary.draftDuplicateCount}",
            "Ready to commit: ${if (summary.canCommitAfterPreflight) "yes" else "no"}",
        ).joinToString("\n"),
    )
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    isError: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private suspend fun processNotebookImages(
    context: Context,
    uris: List<Uri>,
    exerciseCatalog: List<Exercise>,
): NotebookImportUiState.Result {
    val sources = withContext(Dispatchers.IO) {
        uris.mapIndexed { index, uri ->
            NotebookPageSource(
                id = "page-${index + 1}",
                sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("Unable to read image bytes for page ${index + 1}"),
                sourceUri = uri.toString(),
                capturedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }
    val pages = NotebookPageIntake.createPageDrafts(sources)
    val batch = NotebookImportBatchDraft(
        id = "notebook-import-${System.currentTimeMillis()}",
        pages = pages,
    )
    val request = NotebookRecognitionRequest(batch = batch)
    val ocrOutput = MlKitNotebookOcrRecognizer(context).recognize(
        request = request,
        pageUrisById = pages.zip(uris).associate { (page, uri) -> page.id to uri },
    )
    val interpretation = NotebookTextInterpreter.interpret(request, ocrOutput)
    val matching = NotebookExerciseMatcher.matchExercises(
        batch = interpretation.batch,
        exerciseCatalog = exerciseCatalog,
    )
    val draftDuplicates = NotebookWorkoutDuplicateDetector.detectWithinBatch(matching.batch)
    val reviewQueue = NotebookImportReviewQueueBuilder.build(matching.batch)
    val summary = NotebookImportSessionSummaryBuilder.build(
        state = NotebookImportBatchState(
            batch = matching.batch,
            pageStates = pages.map { page ->
                NotebookPageProcessingState(
                    pageId = page.id,
                    status = NotebookPageProcessingStatus.PROCESSED,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            },
            status = NotebookImportBatchStatus.AWAITING_REVIEW,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
        ),
        reviewQueue = reviewQueue,
        draftDuplicateReport = draftDuplicates,
    )
    return NotebookImportUiState.Result(
        summary = summary,
        recognitionOutput = ocrOutput,
        reviewQueue = reviewQueue,
        warnings = ocrOutput.warnings + interpretation.warnings + matching.warnings,
    )
}

private fun createNotebookCaptureUri(context: Context): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "gymtrack-notebook-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GymTrack")
        }
    }
    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Unable to create camera image destination")
}
