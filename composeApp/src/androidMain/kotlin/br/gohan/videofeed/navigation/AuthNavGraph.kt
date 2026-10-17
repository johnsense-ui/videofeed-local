package br.gohan.videofeed.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.gohan.videofeed.auth.LoginRoot
import br.gohan.videofeed.auth.RegisterRoot

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onNavigateToFeed: () -> Unit
) {
    composable<LoginRoute> {
        LoginRoot(
            onNavigateToFeed = onNavigateToFeed,
            onNavigateToRegister = { navController.navigate(RegisterRoute) }
        )
    }
    composable<RegisterRoute> {
        RegisterRoot(
            onNavigateToFeed = onNavigateToFeed,
            onNavigateToLogin = { navController.popBackStack() }
        )
    }
}
