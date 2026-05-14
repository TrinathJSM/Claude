package com.facemorphapp.presentation.screens.result

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.facemorphapp.R
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    sourceUri: Uri,
    resultUri: Uri,
    durationMs: Long,
    landmarkCount: Int,
    morphMode: MorphMode,
    onTryAnother: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Your Morph", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Before/After slider
            BeforeAfterSlider(
                beforeUri = sourceUri,
                afterUri = resultUri,
                sliderPosition = uiState.sliderPosition,
                onSliderChanged = viewModel::onSliderChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Metadata chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(durationMs.formatDuration()) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("$landmarkCount landmarks") }
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(if (morphMode == MorphMode.GPU)
                            stringResource(R.string.morph_mode_gpu)
                        else stringResource(R.string.morph_mode_cpu))
                    }
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.shareResult(resultUri) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }

                Button(
                    onClick = onTryAnother,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.try_another))
                }
            }
        }
    }
}

/**
 * Custom Before/After reveal slider composable.
 * Drag horizontally to reveal before (source) on the left and after (result) on the right.
 */
@Composable
private fun BeforeAfterSlider(
    beforeUri: Uri,
    afterUri: Uri,
    sliderPosition: Float,
    onSliderChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var layoutWidth by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val newPos = (sliderPosition + dragAmount / layoutWidth).coerceIn(0f, 1f)
                    onSliderChanged(newPos)
                }
            }
    ) {
        // "After" image (full width, behind)
        AsyncImage(
            model = afterUri,
            contentDescription = stringResource(R.string.after),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // "Before" image (clipped to left portion by slider)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sliderPosition)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
        ) {
            AsyncImage(
                model = beforeUri,
                contentDescription = stringResource(R.string.before),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Divider line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .align(Alignment.CenterStart)
                .offset(x = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (layoutWidth * sliderPosition / density).dp
                })
                .background(MaterialTheme.colorScheme.primary)
        )

        // Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.before)) })
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.after)) })
        }
    }
}
