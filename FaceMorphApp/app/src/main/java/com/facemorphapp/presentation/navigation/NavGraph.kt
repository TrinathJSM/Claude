package com.facemorphapp.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.facemorphapp.domain.model.FaceDetectionResult
import com.facemorphapp.domain.model.MorphMode
import com.facemorphapp.presentation.screens.crashlog.CrashLogScreen
import com.facemorphapp.presentation.screens.home.HomeScreen
import com.facemorphapp.presentation.screens.processing.ProcessingScreen
import com.facemorphapp.presentation.screens.result.ResultScreen
import com.facemorphapp.presentation.screens.settings.SettingsScreen
import com.facemorphapp.presentation.screens.targetpicker.TargetPickerScreen
import com.facemorphapp.presentation.screens.upload.UploadScreen

sealed class Screen(val route: String) {
    object Home          : Screen("home")
    object TargetPicker  : Screen("target_picker")
    object Upload        : Screen("upload/{targetUri}") {
        fun createRoute(targetUri: Uri) = "upload/${Uri.encode(targetUri.toString())}"
    }
    object Processing    : Screen("processing")
    object Result        : Screen("result")
    object Settings      : Screen("settings")
    object CrashLog      : Screen("crash_log")
}

@Composable
fun FaceMorphNavGraph() {
    val navController = rememberNavController()

    // In-memory store for data too large to serialize in nav args
    val navStore = remember { NavStore() }

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onGetStarted = { navController.navigate(Screen.TargetPicker.route) },
                onSettings   = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.TargetPicker.route) {
            TargetPickerScreen(
                onTargetSelected = { uri ->
                    navStore.targetUri = uri
                    navController.navigate(Screen.Upload.createRoute(uri))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Upload.route,
            arguments = listOf(navArgument("targetUri") { type = NavType.StringType })
        ) { backStack ->
            val targetUri = Uri.parse(Uri.decode(backStack.arguments?.getString("targetUri") ?: ""))
            UploadScreen(
                targetUri = targetUri,
                onMorphNow = { sourceUri, sourceFace ->
                    navStore.sourceUri = sourceUri
                    navStore.sourceFace = sourceFace
                    navStore.targetFace = navStore.targetFace  // already set by TargetPicker flow
                    navController.navigate(Screen.Processing.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Processing.route) {
            val sourceUri  = navStore.sourceUri  ?: return@composable
            val targetUri  = navStore.targetUri  ?: return@composable
            val sourceFace = navStore.sourceFace ?: return@composable
            val targetFace = navStore.targetFace ?: sourceFace  // fallback: use source as target

            ProcessingScreen(
                sourceUri  = sourceUri,
                targetUri  = targetUri,
                sourceFace = sourceFace,
                targetFace = targetFace,
                onComplete = { resultUri ->
                    navStore.resultUri = resultUri
                    navController.navigate(Screen.Result.route) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.Result.route) {
            val sourceUri = navStore.sourceUri ?: return@composable
            val resultUri = navStore.resultUri ?: return@composable

            ResultScreen(
                sourceUri    = sourceUri,
                resultUri    = resultUri,
                durationMs   = navStore.durationMs,
                landmarkCount = navStore.landmarkCount,
                morphMode    = navStore.morphMode,
                onTryAnother = {
                    navStore.reset()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack     = { navController.popBackStack() },
                onCrashLog = { navController.navigate(Screen.CrashLog.route) }
            )
        }

        composable(Screen.CrashLog.route) {
            CrashLogScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** In-memory nav store for large objects that can't be serialised into nav args. */
class NavStore {
    var targetUri: Uri? = null
    var sourceUri: Uri? = null
    var resultUri: Uri? = null
    var sourceFace: FaceDetectionResult? = null
    var targetFace: FaceDetectionResult? = null
    var durationMs: Long = 0
    var landmarkCount: Int = 0
    var morphMode: MorphMode = MorphMode.CPU

    fun reset() {
        targetUri = null; sourceUri = null; resultUri = null
        sourceFace = null; targetFace = null
        durationMs = 0; landmarkCount = 0; morphMode = MorphMode.CPU
    }
}

