package com.example.chesspairmobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.PlayerStanding
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.logic.calculateStandings
import kotlinx.coroutines.delay

@Composable
fun TvDisplayScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    matches: List<MatchEntity>,
    onBack: () -> Unit
) {
    if (tournament == null) return

    val tiebreakOrder = listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ,
        TiebreakType.MEDIAN_BUCHHOLZ, TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING)

    val modelPlayers = players.map { p ->
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
        val byes = try { gson.fromJson<List<Int>>(p.requestedByes, type) ?: emptyList() } catch (_: Exception) { emptyList<Int>() }
        com.example.chesspairmobile.data.models.Player(
            id = p.id, name = p.name, rating = p.rating,
            title = p.title, club = p.club,
            active = p.active, withdrawn = p.withdrawn,
            requestedByes = byes, penaltyPoints = p.penaltyPoints
        )
    }

    val modelMatches = matches.map { m ->
        com.example.chesspairmobile.data.models.Match(
            id = m.id, round = m.round, whiteId = m.whiteId, blackId = m.blackId,
            result = m.result?.let { com.example.chesspairmobile.data.models.MatchResult.valueOf(it) },
            boardNumber = m.boardNumber, teamMatchId = m.teamMatchId,
            isManual = m.isManual, pgn = m.pgn
        )
    }

    val standings = remember(modelPlayers, modelMatches, tournament, tiebreakOrder) {
        calculateStandings(modelPlayers, modelMatches,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss, tiebreakOrder)
    }

    val currentRound = tournament.currentRound
    val roundMatches = matches.filter { it.round == currentRound }

    var showPairings by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }

    // Auto-cycle: toggle pairings/standings every 10s, advance page every 5s
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(5000)
            tick++
            if (tick % 2 == 0) {
                showPairings = !showPairings
                page = 0
            } else {
                val maxPage = if (showPairings) {
                    maxOf(0, (roundMatches.size - 1) / 12)
                } else {
                    maxOf(0, (standings.size - 1) / 12)
                }
                if (maxPage > 0) {
                    page = (page + 1) % (maxPage + 1)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            // Header
            Text(
                tournament.name,
                style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                "Round $currentRound/${tournament.totalRounds}",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.Gray),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            // Content
            AnimatedContent(
                targetState = showPairings,
                modifier = Modifier.weight(1f)
            ) { isPairings ->
                if (isPairings) {
                    TvPairingsView(roundMatches, players, page)
                } else {
                    TvStandingsView(standings, page)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { /* animated progress */ 0.5f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color.White,
                trackColor = Color.DarkGray
            )

            // Pagination indicator
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val maxPage = if (showPairings) maxOf(0, (roundMatches.size - 1) / 12)
                else maxOf(0, (standings.size - 1) / 12)

                for (i in 0..maxPage) {
                    Box(
                        Modifier
                            .padding(4.dp)
                            .size(if (i == page) 10.dp else 6.dp)
                            .background(if (i == page) Color.White else Color.Gray)
                    )
                }
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }
    }
}

@Composable
fun TvPairingsView(
    matches: List<MatchEntity>,
    players: List<PlayerEntity>,
    page: Int
) {
    val itemsPerPage = 12
    val pagedMatches = matches.drop(page * itemsPerPage).take(itemsPerPage)

    LazyColumn {
        itemsIndexed(pagedMatches) { index, match ->
            val white = players.find { it.id == match.whiteId }
            val black = players.find { it.id == match.blackId }
            TvMatchRow(index + 1 + page * itemsPerPage, white?.name, black?.name, match.result)
        }
    }
}

@Composable
fun TvMatchRow(
    board: Int,
    whiteName: String?,
    blackName: String?,
    result: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$board", Modifier.width(40.dp), color = Color.Gray, fontSize = 14.sp,
            textAlign = TextAlign.Center)
        Text(whiteName ?: "???", Modifier.weight(1f), color = Color.White,
            fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(result?.let { formatTvResult(it) } ?: "vs", Modifier.width(50.dp),
            color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text(blackName ?: "BYE", Modifier.weight(1f), color = Color.White,
            fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
fun TvStandingsView(
    standings: List<PlayerStanding>,
    page: Int
) {
    val itemsPerPage = 12
    val pagedStandings = standings.drop(page * itemsPerPage).take(itemsPerPage)

    // Table header
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("#", Modifier.width(30.dp), color = Color.Gray, fontSize = 12.sp)
        Text("Name", Modifier.weight(2f), color = Color.Gray, fontSize = 12.sp)
        Text("Pts", Modifier.width(50.dp), color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
    }

    LazyColumn {
        itemsIndexed(pagedStandings) { index, standing ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1 + page * itemsPerPage}",
                    Modifier.width(30.dp),
                    color = if (index < 3) Color(0xFFFFD700) else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    standing.player.name,
                    Modifier.weight(2f),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${standing.score}",
                    Modifier.width(50.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
        }
    }
}

fun formatTvResult(result: String): String {
    return when (result) {
        "WHITE_WIN" -> "1-0"
        "BLACK_WIN" -> "0-1"
        "DRAW" -> "½-½"
        "BYE" -> "BYE"
        "FORFEIT_WHITE" -> "0-1F"
        "FORFEIT_BLACK" -> "1-0F"
        else -> ""
    }
}
