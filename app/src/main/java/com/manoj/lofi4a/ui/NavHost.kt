    package com.manoj.lofi4a.ui

    import androidx.compose.runtime.Composable
    import androidx.navigation.compose.NavHost
    import androidx.navigation.compose.composable
    import androidx.navigation.compose.rememberNavController
    import com.manoj.lofi4a.ui.screens.AboutScreen
    import com.manoj.lofi4a.ui.screens.ChatScreen
    import com.manoj.lofi4a.ui.screens.ModelsScreen

    object Routes {
        const val CHAT = "chat"
        const val MODELS = "models"
        const val ABOUT = "about"
    }

    @Composable
    fun AppNavHost() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.CHAT) {
            composable(Routes.CHAT) { ChatScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.MODELS) { ModelsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
