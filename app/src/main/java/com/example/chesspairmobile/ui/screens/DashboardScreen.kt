package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.logic.startRequirement
import com.example.chesspairmobile.logic.roundCompletionIssue

@Composable
fun DashboardScreen(
    tournament: TournamentEntity?, players: List<PlayerEntity>, teams: List<TeamEntity>,
    matches: List<MatchEntity>, plannedRounds: Int = 0, onNavigate: (String) -> Unit = {},
    onEditTournament: () -> Unit = {}
) {
    if (tournament == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Choose a tournament to begin", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onNavigate("home") }) { Text("Tournaments") }
        }
        return
    }
    val converters = Converters()
    val requirement = startRequirement(
        players.map { com.example.chesspairmobile.data.models.Player(id = it.id, name = it.name, active = it.active, withdrawn = it.withdrawn, requestedByes = converters.toIntList(it.requestedByes)) },
        teams.map { com.example.chesspairmobile.data.models.Team(id = it.id, name = it.name, playerIds = converters.toStringList(it.playerIds)) },
        tournament.isTeamTournament, plannedRounds
    )
    val currentMatches = matches.filter { it.round == tournament.currentRound }
    val pending = currentMatches.count { it.result == null }
    val completionIssue = roundCompletionIssue(tournament.type, tournament.isTeamTournament, currentMatches.map {
        com.example.chesspairmobile.data.models.Match(round = it.round, whiteId = it.whiteId, blackId = it.blackId,
            result = converters.toMatchResult(it.result), teamMatchId = it.teamMatchId)
    }, teams.map { com.example.chesspairmobile.data.models.Team(it.id, it.name, converters.toStringList(it.playerIds)) })
    val completedRounds = if (tournament.status == "COMPLETED") tournament.currentRound else (tournament.currentRound - 1).coerceAtLeast(0)
    val (heading, description, action, route) = when {
        tournament.status == "SETUP" -> listOf("Prepare your tournament", requirement ?: "Your roster is ready. Review the setup, then start your tournament.", if (requirement == null) "Review and start" else if (tournament.isTeamTournament) "Set up teams" else "Add players", if (requirement == null) "rounds" else if (tournament.isTeamTournament) "teams" else "players")
        tournament.status == "COMPLETED" -> listOf("Tournament complete", "All ${tournament.totalRounds} rounds are complete. Review the final standings and export your results.", "View final standings", "standings")
        currentMatches.isEmpty() -> listOf("Round ${tournament.currentRound} is ready", "Generate pairings to begin this round.", "Generate pairings", "rounds")
        pending > 0 -> listOf("Round ${tournament.currentRound} in progress", "${currentMatches.size - pending} of ${currentMatches.size} results entered · $pending remaining", "Enter remaining results", "rounds")
        completionIssue != null -> listOf("Round ${tournament.currentRound} needs review", completionIssue, "Resolve results", "rounds")
        else -> listOf("Round ${tournament.currentRound} results entered", "Review the results before ${if (tournament.currentRound == tournament.totalRounds) "finishing the tournament" else "completing the round"}.", "Review and complete", "rounds")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Overview", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryStat("Active players", "${players.count { it.active && !it.withdrawn }}", Modifier.weight(1f))
            if (tournament.isTeamTournament) SummaryStat("Teams", "${teams.size}", Modifier.weight(1f))
            SummaryStat(if (tournament.status == "SETUP") "Planned rounds" else "Rounds complete",
                if (tournament.status == "SETUP") plannedRounds.takeIf { it > 0 }?.toString() ?: "Auto" else "$completedRounds / ${tournament.totalRounds}", Modifier.weight(1f))
        }
        if (tournament.status != "SETUP") LinearProgressIndicator(progress = { if (tournament.totalRounds > 0) completedRounds.toFloat() / tournament.totalRounds else 0f }, modifier = Modifier.fillMaxWidth())
        if (tournament.section != null || tournament.timeControl != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tournament.section?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            tournament.timeControl?.let { Text("Time control: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (tournament.status == "SETUP") OutlinedButton(onClick = onEditTournament, modifier = Modifier.fillMaxWidth()) { Text("Edit tournament") }
        Column {
            DashboardLink("Manage players") { onNavigate("players") }
            if (tournament.isTeamTournament) DashboardLink("View teams") { onNavigate("teams") }
            DashboardLink("View standings") { onNavigate("standings") }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DashboardLink(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
