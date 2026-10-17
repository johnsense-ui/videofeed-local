package br.gohan.videofeed.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import br.gohan.videofeed.upload.UploadRoot

fun NavGraphBuilder.uploadGraph(navController: NavHostController) {
    composable<UploadRoute> {
        UploadRoot(
            onNavigateToFeed = {
                navController.navigate(FeedRoute) {
                    popUpTo(UploadRoute) { inclusive = true }
                }
            }
        )
    }
}
