package com.surya.miniconnect.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surya.miniconnect.domain.model.Peer

/**
 * Displays a peer. If [onClick] is provided the card becomes clickable.
 */
@Composable
fun PeerCard(peer: Peer, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val clickMod = if (onClick != null) modifier.clickable { onClick() } else modifier
    Card(modifier = clickMod.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = peer.name.ifEmpty { peer.address }, style = MaterialTheme.typography.titleMedium)
            Text(text = peer.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}

