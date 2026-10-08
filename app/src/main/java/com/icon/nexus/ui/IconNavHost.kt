package com.icon.nexus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icon.nexus.onboarding.MAIN_ROUTE
import com.icon.nexus.onboarding.ONBOARDING_ROUTE
import com.icon.nexus.onboarding.initialDestination
import com.icon.nexus.viewmodel.MainViewModel

@Composable
fun IconNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val startDestination = remember {
        initialDestination(viewModel.showOnboarding.value)
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ONBOARDING_ROUTE) {
            OnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(MAIN_ROUTE) {
            MainScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMemory = { navController.navigate("memory") },
            )
        }
        composable("memory") {
            MemoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
