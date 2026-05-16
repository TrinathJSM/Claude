package com.facemorphapp.presentation.screens.result

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            TopAppBar(title = { Text(stringResource(R.string.result_title), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step 3 banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.step_3_of_3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.step_3_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Drag instruction
            Text(
                text = stringResource(R.string.drag_to_compare),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

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
                SuggestionChip(onClick = {}, label = { Text(durationMs.formatDuration()) })
                SuggestionChip(onClick = {}, label = { Text("$landmarkCount pts") })
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
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share))
                }
                Button(
                    onClick = onTryAnother,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.try_another))
                }
            }
        }
    }
}

@Composable
private fun BeforeAfterSlider(
    beforeUri: Uri,
    afterUri: Uri,
    sliderPosition: Float,
    onSliderChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var layoutWidth by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current

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
        // After image (full width, behind)
        AsyncImage(
            model = afterUri,
            contentDescription = stringResource(R.string.after),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Before image (clipped to left portion by slider)
        Box(
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

        // Divider handle
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (layoutWidth * sliderPosition / this.density).dp })
                .background(MaterialTheme.colorScheme.primary)
        )

        // Corner labels
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.before), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.after), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
}
