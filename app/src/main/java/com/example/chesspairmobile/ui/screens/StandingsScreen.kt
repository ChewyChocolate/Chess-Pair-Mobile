package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.PlayerStanding
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.ui.components.TiebreakConfigPanel
import com.example.chesspairmobile.ui.theme.GoldMedal
import com.example.chesspairmobile.ui.theme.SilverMedal
import com.example.chesspairmobile.ui.theme.BronzeMedal
import com.example.chesspairmobile.logic.calculateStandings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    matches: List<MatchEntity>,
    onExportCsv: () -> String,
    onUpdateTiebreakOrder: ((List<TiebreakType>) -> Unit)? = null
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Leaderboard, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("No tournament selected", style = MaterialTheme.typography.headlineSmall)
            }
        }
        return
    }

    if (tournament.status == "SETUP") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Leaderboard, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("Tournament hasn't started yet", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Standings will appear after Round 1 is complete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                result = try { m.result?.let { com.example.chesspairmobile.data.models.MatchResult.valueOf(it) } } catch (_: Exception) { null },
                boardNumber = m.boardNumber, teamMatchId = m.teamMatchId,
                isManual = m.isManual, pgn = m.pgn
            )
        }
    }

    var selectedRound by remember { mutableIntStateOf(tournament.currentRound) }
    LaunchedEffect(tournament.currentRound) {
        selectedRound = tournament.currentRound
    }

    val standings = remember(modelPlayers, modelMatches, tournament, tiebreakOrder, selectedRound) {
        calculateStandings(
            modelPlayers, modelMatches,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss,
            tiebreakOrder,
            upToRound = selectedRound
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Round selector
            Surface(
                Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (r in 1..tournament.currentRound) {
                            FilterChip(
                                selected = selectedRound == r,
                                onClick = { selectedRound = r },
                                label = { Text("$r") },
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            // Tiebreak settings panel
            if (onUpdateTiebreakOrder != null && tournament.status == "SETUP") {
                TiebreakConfigPanel(
                    tiebreakOrder = tiebreakOrder,
                    onTiebreakOrderChanged = onUpdateTiebreakOrder
                )
                HorizontalDivider()
            }

            // Table header
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Name", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Pts", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("BH", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("S-B", Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }

            LazyColumn {
                itemsIndexed(standings) { index, standing ->
                    StandingRow(index = index, standing = standing)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun StandingRow(index: Int, standing: PlayerStanding) {
    val podiumColor = when (index) {
        0 -> GoldMedal
        1 -> SilverMedal
        2 -> BronzeMedal
        else -> null
    }

    val bgColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().then(if (standing.player.withdrawn) Modifier.alpha(0.55f) else Modifier),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank with optional podium
            if (podiumColor != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = podiumColor.copy(alpha = 0.15f),
                    modifier = Modifier.width(36.dp)
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = podiumColor
                    )
                }
            } else {
                Text(
                    "${index + 1}",
                    Modifier.width(36.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(Modifier.weight(2f)) {
                Text(
                    standing.player.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (standing.player.withdrawn) TextDecoration.LineThrough else TextDecoration.None
                )
                standing.player.rating?.let {
                    Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "${standing.score}",
                Modifier.weight(0.7f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            val buchholz = "%.1f".format(standing.buchholz)
            Text(buchholz, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

            val sb = "%.2f".format(standing.sonnebornBerger)
            Text(sb, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
