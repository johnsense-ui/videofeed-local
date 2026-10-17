package br.gohan.videofeed.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.gohan.videofeed.feed.presenter.VideoUi

@Composable
fun VideoInfoOverlay(
    video: VideoUi,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 16.dp, bottom = 80.dp, end = 80.dp)
    ) {
        Text(
            text = "@${video.uploaderName}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
