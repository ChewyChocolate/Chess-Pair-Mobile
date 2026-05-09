package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.PlayerStanding
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.logic.calculateStandings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    matches: List<MatchEntity>,
    onExportCsv: () -> String
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tournament selected")
        }
        return
    }

    if (tournament.status == "SETUP") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tournament hasn't started yet", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val tiebreakOrder = remember(tournament) {
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val names: List<String> = gson.fromJson(tournament.tiebreakOrder, type) ?: emptyList()
            names.mapNotNull { try { TiebreakType.valueOf(it) } catch (_: Exception) { null } }
                .ifEmpty {
                    listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
                        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING)
                }
        } catch (e: Exception) {
            listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
                TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING)
        }
    }

    val modelPlayers = remember(players) {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
        players.map { p ->
            val byes = try { gson.fromJson<List<Int>>(p.requestedByes, type) ?: emptyList() } catch (_: Exception) { emptyList<Int>() }
            com.example.chesspairmobile.data.models.Player(
                id = p.id, name = p.name, rating = p.rating,
                title = p.title, club = p.club,
                active = p.active, withdrawn = p.withdrawn,
                requestedByes = byes, penaltyPoints = p.penaltyPoints
            )
        }
    }

    val modelMatches = remember(matches) {
        matches.map { m ->
            com.example.chesspairmobile.data.models.Match(
                id = m.id, round = m.round, whiteId = m.whiteId, blackId = m.blackId,
                result = m.result?.let { com.example.chesspairmobile.data.models.MatchResult.valueOf(it) },
                boardNumber = m.boardNumber, teamMatchId = m.teamMatchId,
                isManual = m.isManual, pgn = m.pgn
            )
        }
    }

    val standings = remember(modelPlayers, modelMatches, tournament, tiebreakOrder) {
        calculateStandings(
            modelPlayers, modelMatches,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss,
            tiebreakOrder
        )
    }

    var selectedRound by remember { mutableIntStateOf(tournament.currentRound) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standings") },
                actions = {
                    val csvData = remember(standings) {
                        "Rank,Name,Rating,Score,Buchholz,Median,S-B,MostWins\n" +
                        standings.mapIndexed { idx, s ->
                            "${idx + 1},${s.player.name},${s.player.rating ?: ""},${s.score},${s.buchholz},${s.medianBuchholz},${s.sonnebornBerger},${s.mostWins}"
                        }.joinToString("\n")
                    }
                    IconButton(onClick = { /* Export CSV - handled via share intent */ }) {
                        Icon(Icons.Default.FileDownload, "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Round selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for (r in 1..tournament.currentRound) {
                    FilterChip(
                        selected = selectedRound == r,
                        onClick = { selectedRound = r },
                        label = { Text("R$r") },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("#", Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Name", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Pts", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("BH", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("S-B", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Wins", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            HorizontalDivider()

            LazyColumn {
                itemsIndexed(standings) { index, standing ->
                    StandingRow(index = index, standing = standing)
                }
            }
        }
    }
}

@Composable
fun StandingRow(index: Int, standing: PlayerStanding) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            Modifier.width(32.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Column(Modifier.weight(2f)) {
            Text(standing.player.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            standing.player.rating?.let {
                Text("$it", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("${standing.score}", Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        val buchholz = "%.1f".format(standing.buchholz)
        Text(buchholz, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

        val sb = "%.2f".format(standing.sonnebornBerger)
        Text(sb, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

        Text("${standing.mostWins}", Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
    HorizontalDivider()
}
