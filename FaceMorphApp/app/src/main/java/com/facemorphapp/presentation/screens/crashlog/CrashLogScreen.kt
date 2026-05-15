package com.facemorphapp.presentation.screens.crashlog

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facemorphapp.R
import com.facemorphapp.util.LocalCrashLogger
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

@HiltViewModel
class CrashLogViewModel @Inject constructor(
    private val crashLogger: LocalCrashLogger
) : ViewModel() {
    var logContent by mutableStateOf(crashLogger.readLog())
        private set

    fun clearLog() {
        crashLogger.clearLog()
        logContent = ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(
    onBack: () -> Unit,
    viewModel: CrashLogViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearLog) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.crash_log_clear))
                    }
                    IconButton(onClick = {
                        // User-initiated share via email — not automatic
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "FaceMorph Crash Log")
                            putExtra(Intent.EXTRA_TEXT, viewModel.logContent)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.crash_log_share)))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.crash_log_share))
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.logContent.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.crash_log_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = viewModel.logContent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}
