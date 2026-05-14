package com.facemorphapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.facemorphapp.domain.repository.SettingsRepository
import com.facemorphapp.presentation.navigation.FaceMorphNavGraph
import com.facemorphapp.presentation.screens.privacy.PrivacyOnboardingScreen
import com.facemorphapp.presentation.theme.FaceMorphAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read privacy flag synchronously once on startup to avoid a composable flicker
        val privacyAlreadyAcknowledged = runBlocking {
            settingsRepository.isPrivacyAcknowledged()
        }

        setContent {
            FaceMorphAppTheme {
                var privacyShown by remember { mutableStateOf(!privacyAlreadyAcknowledged) }

                if (privacyShown) {
                    PrivacyOnboardingScreen(
                        onAccepted = {
                            lifecycleScope.launch { settingsRepository.acknowledgePrivacy() }
                            privacyShown = false
                        }
                    )
                } else {
                    FaceMorphNavGraph()
                }
            }
        }
    }
}
