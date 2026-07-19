package com.surya.miniconnect.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surya.miniconnect.domain.model.TransferState

/**
 * Displays file transfer progress. Visible only when a transfer is in progress or just completed.
 */
@Composable
fun TransferProgressBar(transferState: TransferState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = transferState is TransferState.InProgress || transferState is TransferState.Success,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (transferState) {
                is TransferState.InProgress -> {
                    Text(
                        text = "Transferring: ${transferState.fileName}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LinearProgressIndicator(
                        progress = { transferState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
                is TransferState.Success -> {
                    Text(
                        text = "Transfer complete: ${transferState.fileName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {}
            }
        }
    }
}
