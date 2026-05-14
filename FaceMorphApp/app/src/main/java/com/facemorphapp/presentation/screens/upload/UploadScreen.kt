package com.facemorphapp.presentation.screens.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.facemorphapp.R
import com.facemorphapp.domain.model.FaceDetectionResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    targetUri: Uri,
    onMorphNow: (sourceUri: Uri, sourceFace: FaceDetectionResult) -> Unit,
    onBack: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    // Snackbar for error messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Multiple faces bottom sheet
    if (uiState.multipleFacesDialogVisible) {
        MultipleFacesSheet(
            faces = uiState.detectedFaces,
            onFaceSelected = viewModel::onFaceSelected,
            onDismiss = viewModel::dismissMultipleFacesDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_source)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source image preview with landmark overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.sourceBitmap != null) {
                    AsyncImage(
                        model = uiState.sourceUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Bounding box + landmark overlay
                    uiState.selectedFace?.let { face ->
                        FaceOverlayCanvas(face = face, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Tap to select your photo")
                    }
                }

                if (uiState.isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            // Low confidence warning
            if (uiState.lowConfidenceWarning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = stringResource(R.string.error_low_confidence),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Change Photo") }

                Button(
                    onClick = {
                        val uri = uiState.sourceUri ?: return@Button
                        val face = uiState.selectedFace ?: return@Button
                        onMorphNow(uri, face)
                    },
                    enabled = uiState.selectedFace != null && !uiState.isDetecting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.morph_now)) }
            }
        }
    }
}

@Composable
private fun FaceOverlayCanvas(face: FaceDetectionResult, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / face.boundingBox.width()
        val scaleY = size.height / face.boundingBox.height()

        // Draw bounding box
        drawRect(
            color = Color(0xFF00FF88),
            topLeft = Offset(face.boundingBox.left * scaleX, face.boundingBox.top * scaleY),
            size = Size(face.boundingBox.width() * scaleX, face.boundingBox.height() * scaleY),
            style = Stroke(width = 3f)
        )
        // Draw landmark points
        face.landmarks.forEach { pt ->
            drawCircle(
                color = Color(0xFF00FF88),
                radius = 3f,
                center = Offset(pt.x * scaleX, pt.y * scaleY)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultipleFacesSheet(
    faces: List<FaceDetectionResult>,
    onFaceSelected: (FaceDetectionResult) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.multiple_faces_prompt),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            faces.forEachIndexed { index, face ->
                TextButton(
                    onClick = { onFaceSelected(face) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Face ${index + 1} (confidence: ${"%.0f".format(face.confidence * 100)}%)") }
            }
        }
    }
}
