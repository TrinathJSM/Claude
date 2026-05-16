package com.facemorphapp.presentation.screens.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.facemorphapp.R
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.FaceValidationResult

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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Step banner ───────────────────────────────────────────────────
            StepBanner(
                step = stringResource(R.string.step_2_of_3),
                instruction = stringResource(R.string.step_2_instruction)
            )

            // ── Image preview / upload zone ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.sourceBitmap != null) {
                    // Photo selected — show with landmark overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = uiState.sourceUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                        uiState.selectedFace?.let { face ->
                            FaceOverlayCanvas(
                                face = face,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Tap-to-change overlay chip
                        AssistChip(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            label = { Text(stringResource(R.string.change_photo)) },
                            leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        )
                    }
                } else {
                    // Empty upload zone
                    UploadZone(
                        onTap = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }

                if (uiState.isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            // ── Validation feedback ───────────────────────────────────────────
            uiState.validationResult?.let { result ->
                ValidationBanner(result = result, landmarkCount = uiState.selectedFace?.landmarks?.size ?: 0)
            }

            // ── Morph button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    val uri = uiState.sourceUri ?: return@Button
                    val face = uiState.selectedFace ?: return@Button
                    onMorphNow(uri, face)
                },
                enabled = uiState.selectedFace != null && !uiState.isDetecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.morph_now),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepBanner(step: String, instruction: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun UploadZone(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.upload_zone_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onTap) {
                Text("Select Photo")
            }
        }
    }
}

@Composable
private fun ValidationBanner(result: FaceValidationResult, landmarkCount: Int) {
    when (result) {
        is FaceValidationResult.Valid -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.face_detected, landmarkCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        is FaceValidationResult.LowConfidence -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.face_detected_low_conf, landmarkCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun FaceOverlayCanvas(face: FaceDetectionResult, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Compute scale from image coords to canvas coords
        val scaleX = size.width  / (face.boundingBox.right  + face.boundingBox.left).coerceAtLeast(1f)
        val scaleY = size.height / (face.boundingBox.bottom + face.boundingBox.top ).coerceAtLeast(1f)
        // A better scale: landmark coords are in original bitmap space, canvas is the view size.
        // Use face bounding box to infer image dimensions.
        val imgW = face.boundingBox.right * 1.2f
        val imgH = face.boundingBox.bottom * 1.2f
        val sx = size.width  / imgW.coerceAtLeast(1f)
        val sy = size.height / imgH.coerceAtLeast(1f)
        val s = minOf(sx, sy)
        val offX = (size.width  - imgW * s) / 2f
        val offY = (size.height - imgH * s) / 2f

        // Bounding box
        drawRect(
            color = Color(0xFF00E676),
            topLeft = Offset(face.boundingBox.left * s + offX, face.boundingBox.top * s + offY),
            size = Size(face.boundingBox.width() * s, face.boundingBox.height() * s),
            style = Stroke(width = 2.5f)
        )

        // All detected contour / landmark points
        face.landmarks.forEach { pt ->
            drawCircle(
                color = Color(0xFF00E676),
                radius = 2.5f,
                center = Offset(pt.x * s + offX, pt.y * s + offY)
            )
        }

        // Face outline in a contrasting color for clarity
        if (face.faceOutline.size >= 3) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(face.faceOutline[0].x * s + offX, face.faceOutline[0].y * s + offY)
            face.faceOutline.drop(1).forEach { pt ->
                path.lineTo(pt.x * s + offX, pt.y * s + offY)
            }
            path.close()
            drawPath(path = path, color = Color(0xFFFFEB3B), style = Stroke(width = 1.5f))
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
                ) {
                    Text("Face ${index + 1} · ${face.landmarks.size} landmarks · ${"%.0f".format(face.confidence * 100)}% confidence")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
