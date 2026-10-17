package br.gohan.videofeed.navigation

import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

@UnstableApi
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        authGraph(
            navController = navController,
            onNavigateToFeed = {
                navController.navigate(FeedRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                }
            }
        )
        feedGraph(
            onNavigateToUpload = { navController.navigate(UploadRoute) },
            onNavigateToLogin = {
                navController.navigate(LoginRoute) {
                    popUpTo(FeedRoute) { inclusive = true }
                }
            }
        )
        uploadGraph(navController)
    }
}
