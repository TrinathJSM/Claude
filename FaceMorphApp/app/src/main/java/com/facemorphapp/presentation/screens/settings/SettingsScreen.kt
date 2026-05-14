package com.facemorphapp.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facemorphapp.BuildConfig
import com.facemorphapp.R
import com.facemorphapp.domain.model.OutputQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCrashLog: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var aboutTapCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // GPU Acceleration
            SettingSwitch(
                title = stringResource(R.string.gpu_acceleration),
                subtitle = "Use GPU for faster morphing on capable devices",
                checked = settings.gpuAccelerationEnabled,
                onCheckedChange = viewModel::setGpuAcceleration
            )

            HorizontalDivider()

            // Output Quality
            Text(
                text = stringResource(R.string.output_quality),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            OutputQuality.values().forEach { quality ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = settings.outputQuality == quality,
                        onClick = { viewModel.setOutputQuality(quality) }
                    )
                    Text(quality.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }

            HorizontalDivider()

            // Save original
            SettingSwitch(
                title = stringResource(R.string.save_original),
                subtitle = "Also keep a copy of your unedited source photo",
                checked = settings.saveOriginal,
                onCheckedChange = viewModel::setSaveOriginal
            )

            HorizontalDivider()

            // About section (tap 7× to reveal crash log)
            ListItem(
                headlineContent = { Text(stringResource(R.string.about)) },
                supportingContent = {
                    Text("${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}")
                },
                modifier = Modifier.clickable {
                    aboutTapCount++
                    if (aboutTapCount >= 7) {
                        aboutTapCount = 0
                        onCrashLog()
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

