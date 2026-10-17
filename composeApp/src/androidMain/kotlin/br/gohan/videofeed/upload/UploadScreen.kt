package br.gohan.videofeed.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.gohan.videofeed.auth.ObserveAsEvents
import br.gohan.videofeed.upload.presenter.UploadAction
import br.gohan.videofeed.upload.presenter.UploadEvent
import br.gohan.videofeed.upload.presenter.UploadState
import br.gohan.videofeed.upload.presenter.UploadStatus
import br.gohan.videofeed.upload.presenter.UploadViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UploadRoot(
    onNavigateToFeed: () -> Unit,
    viewModel: UploadViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UploadEvent.NavigateToFeed -> onNavigateToFeed()
        }
    }

    UploadScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun UploadScreen(
    state: UploadState,
    onAction: (UploadAction) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = readBytesFromUri(context, uri)
            val filename = getFilenameFromUri(context, uri)
            val mimeType = getMimeTypeFromUri(context, uri)
            onAction(UploadAction.OnFileSelected(bytes, filename, mimeType))
        }
    }

    val isLoading = state.status is UploadStatus.Presigning
        || state.status is UploadStatus.Uploading
        || state.status is UploadStatus.Finalizing
    val canSubmit = !isLoading && state.selectedFilename != null && state.title.isNotBlank()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { launcher.launch("video/*") },
                enabled = !isLoading
            ) {
                Text(if (state.selectedFilename == null) "Select Video" else "Change Video")
            }

            state.selectedFilename?.let { filename ->
                Spacer(Modifier.height(8.dp))
                Text(text = filename, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.title,
                onValueChange = { onAction(UploadAction.OnTitleChange(it)) },
                label = { Text("Title") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            when (val status = state.status) {
                is UploadStatus.Uploading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Uploading… ${(status.progress * 100).toInt()}%")
                }
                is UploadStatus.Presigning -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Preparing upload…")
                }
                is UploadStatus.Finalizing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Saving…")
                }
                is UploadStatus.Error -> Text(
                    text = status.message,
                    color = MaterialTheme.colorScheme.error
                )
                is UploadStatus.Done -> Text("Upload complete!")
                else -> Unit
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onAction(UploadAction.OnSubmit) },
                enabled = canSubmit
            ) {
                Text("Upload")
            }
        }
    }
}
