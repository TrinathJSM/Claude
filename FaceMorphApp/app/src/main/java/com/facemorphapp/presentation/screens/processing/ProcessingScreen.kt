package com.facemorphapp.presentation.screens.processing

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.facemorphapp.R
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.domain.model.MorphStep

private val PIPELINE_STEPS = listOf(
    MorphStep.SEGMENTATION   to "Segment",
    MorphStep.ALIGNMENT      to "Align",
    MorphStep.WARP           to "Warp",
    MorphStep.BLEND          to "Blend",
    MorphStep.COLOR_CORRECTION to "Color",
    MorphStep.POSTPROCESS    to "Smooth"
)

@Composable
fun ProcessingScreen(
    sourceUri: Uri,
    targetUri: Uri,
    sourceFace: FaceDetectionResult,
    targetFace: FaceDetectionResult,
    onComplete: (resultUri: Uri, durationMs: Long, landmarkCount: Int, morphMode: MorphMode) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startMorph(sourceUri, targetUri, sourceFace, targetFace)
    }
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) uiState.resultUri?.let {
            onComplete(it, uiState.durationMs, uiState.landmarkCount, uiState.morphMode)
        }
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("animations/morphing.json"))
    val animProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.error != null) {
            ErrorContent(error = uiState.error!!, onRetry = onCancel)
        } else {
            // ── Animation ─────────────────────────────────────────────────────
            LottieAnimation(
                composition = composition,
                progress = { animProgress },
                modifier = Modifier.size(160.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.processing_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = uiState.stepLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // ── Progress bar ──────────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(uiState.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── Pipeline diagram ──────────────────────────────────────────────
            PipelineDiagram(
                currentStep = uiState.currentStep,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            OutlinedButton(onClick = {
                viewModel.cancel()
                onCancel()
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun PipelineDiagram(currentStep: MorphStep?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.pipeline_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PIPELINE_STEPS.forEachIndexed { idx, (step, label) ->
                val isActive = step == currentStep
                val isDone = currentStep != null && step.ordinal < currentStep.ordinal

                PipelineNode(
                    label = label,
                    number = "${idx + 1}",
                    isActive = isActive,
                    isDone = isDone
                )

                if (idx < PIPELINE_STEPS.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (isDone) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineNode(label: String, number: String, isActive: Boolean, isDone: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isDone   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else     -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isDone) "✓" else number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive || isDone) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Go Back") }
    }
}
