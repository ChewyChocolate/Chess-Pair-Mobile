package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.layout.*
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
    matchCount: Int
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tournament selected", style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(tournament.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        tournament.section?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

        HorizontalDivider()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Status", tournament.status)
            StatCard("Round", "${tournament.currentRound}/${tournament.totalRounds}")
            StatCard("Type", tournament.type)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Players", "$playerCount")
            if (tournament.isTeamTournament) {
                StatCard("Teams", "$teamCount")
            }
            StatCard("Matches", "$matchCount")
        }

        if (tournament.status == "SETUP") {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Setup", style = MaterialTheme.typography.titleMedium)
                    Text("Add players and configure your tournament before starting.")
                    if (tournament.isTeamTournament) {
                        Text("Then create teams and assign players to boards.")
                    }
                }
            }
        }

        if (tournament.timeControl != null) {
            Text("Time Control: ${tournament.timeControl}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Card(Modifier.padding(4.dp)) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
