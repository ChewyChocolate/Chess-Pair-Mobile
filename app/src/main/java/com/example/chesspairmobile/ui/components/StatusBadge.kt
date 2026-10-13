package com.example.chesspairmobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun StatusBadge(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TournamentStatusBadge(status: String) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground, icon) = when (status) {
        "ACTIVE" -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Default.PlayCircle)
        "COMPLETED" -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Default.CheckCircle)
        else -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, Icons.Default.Edit)
    }
    Surface(shape = MaterialTheme.shapes.small, color = background, contentColor = foreground) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(status.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
        }
    }
}
