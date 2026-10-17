package br.gohan.videofeed.navigation

import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import br.gohan.videofeed.feed.FeedRoot

@UnstableApi
fun NavGraphBuilder.feedGraph(
    onNavigateToUpload: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    composable<FeedRoute> {
        FeedRoot(
            onNavigateToUpload = onNavigateToUpload,
            onNavigateToLogin = onNavigateToLogin
        )
    }
}
