package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.logic.startRequirement

@Composable
fun DashboardScreen(
    tournament: TournamentEntity?, players: List<PlayerEntity>, teams: List<TeamEntity>,
    matches: List<MatchEntity>, plannedRounds: Int = 0, onNavigate: (String) -> Unit = {}
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
    val completedRounds = if (tournament.status == "COMPLETED") tournament.currentRound else (tournament.currentRound - 1).coerceAtLeast(0)
    val (heading, description, action, route) = when {
        tournament.status == "SETUP" -> listOf("Prepare your tournament", requirement ?: "Your roster is ready. Review the setup, then start your tournament.", if (requirement == null) "Review and start" else if (tournament.isTeamTournament) "Set up teams" else "Add players", if (requirement == null) "rounds" else if (tournament.isTeamTournament) "teams" else "players")
        tournament.status == "COMPLETED" -> listOf("Tournament complete", "All ${tournament.totalRounds} rounds are complete. Review the final standings and export your results.", "View final standings", "standings")
        currentMatches.isEmpty() -> listOf("Round ${tournament.currentRound} is ready", "Generate pairings to begin this round.", "Generate pairings", "rounds")
        pending > 0 -> listOf("Round ${tournament.currentRound} in progress", "${currentMatches.size - pending} of ${currentMatches.size} results entered · $pending remaining", "Enter remaining results", "rounds")
        else -> listOf("Round ${tournament.currentRound} results entered", "Review the results before ${if (tournament.currentRound == tournament.totalRounds) "finishing the tournament" else "completing the round"}.", "Review and complete", "rounds")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Overview", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineSmall)
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
        Text(if (tournament.status == "SETUP") (if (plannedRounds > 0) "$plannedRounds rounds planned" else "Rounds calculated when the roster is ready") else "$completedRounds of ${tournament.totalRounds} rounds completed", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { if (tournament.totalRounds > 0) completedRounds.toFloat() / tournament.totalRounds else 0f }, modifier = Modifier.fillMaxWidth())
        Text("${players.count { it.active && !it.withdrawn }} active players${if (tournament.isTeamTournament) " · ${teams.size} teams" else ""}", style = MaterialTheme.typography.bodyLarge)
        tournament.section?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        tournament.timeControl?.let { Text("Time control: $it", style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = { onNavigate("players") }, modifier = Modifier.fillMaxWidth()) { Text("Manage players") }
        if (tournament.isTeamTournament) OutlinedButton(onClick = { onNavigate("teams") }, modifier = Modifier.fillMaxWidth()) { Text("View teams") }
        OutlinedButton(onClick = { onNavigate("standings") }, modifier = Modifier.fillMaxWidth()) { Text("View standings") }
    }
}
