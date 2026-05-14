package com.facemorphapp.presentation.screens.processing

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.facemorphapp.R
import com.facemorphapp.domain.model.FaceDetectionResult

@Composable
fun ProcessingScreen(
    sourceUri: Uri,
    targetUri: Uri,
    sourceFace: FaceDetectionResult,
    targetFace: FaceDetectionResult,
    onComplete: (Uri) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startMorph(sourceUri, targetUri, sourceFace, targetFace)
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) uiState.resultUri?.let { onComplete(it) }
    }

    // Lottie animation from bundled asset — never loaded from a URL
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("animations/morphing.json"))
    val animationProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.error != null) {
            ErrorContent(error = uiState.error!!, onRetry = onCancel)
        } else {
            LottieAnimation(
                composition = composition,
                progress = { animationProgress },
                modifier = Modifier.size(200.dp)
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.processing_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = uiState.stepLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "${(uiState.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
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
