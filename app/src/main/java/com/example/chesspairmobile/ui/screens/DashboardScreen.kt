package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.TournamentEntity

@Composable
fun DashboardScreen(
    tournament: TournamentEntity?,
    playerCount: Int,
    teamCount: Int,
    matchCount: Int,
    onNavigate: (String) -> Unit = {}
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Dashboard, null,
                    Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text("No tournament selected", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { onNavigate("home") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select Tournament")
                }
            }
        }
        return
    }

    val statusColor = when (tournament.status) {
        "SETUP" -> MaterialTheme.colorScheme.outline
        "ACTIVE" -> MaterialTheme.colorScheme.tertiary
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val roundProgress = if (tournament.totalRounds > 0) {
        tournament.currentRound.toFloat() / tournament.totalRounds.toFloat()
    } else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tournament.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                tournament.section?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SuggestionChip(
                onClick = {},
                label = { Text(tournament.status.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = statusColor.copy(alpha = 0.12f),
                    labelColor = statusColor
                )
            )
        }

        if (tournament.timeControl != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(tournament.timeControl, style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider()

        // Round progress
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Round ${tournament.currentRound} of ${tournament.totalRounds}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${(roundProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { roundProgress }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }

        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Players", "$playerCount", Icons.Default.Person, Modifier.weight(1f))
            if (tournament.isTeamTournament) {
                StatCard("Teams", "$teamCount", Icons.Default.Group, Modifier.weight(1f))
            }
            StatCard("Matches", "$matchCount", Icons.Default.GridView, Modifier.weight(1f))
        }

        HorizontalDivider()

        // Quick-action links
        if (tournament.status == "SETUP") {
            Text("Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onNavigate("players") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Players")
                }
                if (tournament.isTeamTournament) {
                    FilledTonalButton(
                        onClick = { onNavigate("teams") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GroupAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Setup Teams")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Add ${maxOf(0, 2 - playerCount)} more player(s) and configure your tournament, then head to Rounds to start.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onNavigate("rounds") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GridView, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rounds")
                }
                FilledTonalButton(
                    onClick = { onNavigate("standings") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Leaderboard, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Standings")
                }
            }
        }

        if (tournament.isTeamTournament) {
            FilledTonalButton(
                onClick = { onNavigate("tv") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tv, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("TV Display Mode")
            }
        } else {
            OutlinedButton(
                onClick = { onNavigate("tv") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tv, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("TV Display Mode")
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
