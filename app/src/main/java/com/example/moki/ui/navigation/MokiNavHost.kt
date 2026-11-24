package com.example.moki.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.moki.ui.MokiViewModel
import com.example.moki.ui.screens.MainScreen
import com.example.moki.ui.screens.SetupScreen

@Composable
fun MokiNavHost(
    viewModel: MokiViewModel,
    autoListenOnStart: Boolean = false
) {
    val navController = rememberNavController()

    LaunchedEffect(autoListenOnStart) {
        if (autoListenOnStart) {
            viewModel.triggerSimulatedHeyMoki()
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSetup = { navController.navigate("setup") }
            )
        }
        composable("setup") {
            SetupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}