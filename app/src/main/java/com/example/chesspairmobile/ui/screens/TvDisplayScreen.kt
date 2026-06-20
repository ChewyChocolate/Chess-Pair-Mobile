package com.example.chesspairmobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.PlayerStanding
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.data.preferences.AppPreferences
import com.example.chesspairmobile.logic.calculateStandings
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF0F0F1A)
private val DarkCardBg = Color(0xFF1A1A2E)
private val TextWhite = Color(0xFFF0F0F0)
private val TextGray = Color(0xFF9090A0)
private val GoldMedalTv = Color(0xFFFFD700)
private val SilverMedalTv = Color(0xFFC0C0C0)
private val BronzeMedalTv = Color(0xFFCD7F32)

@Composable
fun TvDisplayScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    matches: List<MatchEntity>,
    onBack: () -> Unit
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) {
            Text("No tournament selected", color = TextWhite, style = MaterialTheme.typography.headlineSmall)
        }
        return
    }

    val tiebreakOrder = listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ,
        TiebreakType.MEDIAN_BUCHHOLZ, TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.PROGRESSIVE)

    val modelPlayers = remember(players) {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
        players.map { p ->
            val byes = try { gson.fromJson<List<Int>>(p.requestedByes, type) ?: emptyList() } catch (_: Exception) { emptyList<Int>() }
            com.example.chesspairmobile.data.models.Player(
                id = p.id, name = p.name, rating = p.rating,
                title = p.title, club = p.club,
                pairingNumber = p.pairingNumber,
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

    val standings = remember(modelPlayers, modelMatches, tournament, tiebreakOrder) {
        calculateStandings(modelPlayers, modelMatches,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss, tiebreakOrder)
    }

    val currentRound = tournament.currentRound
    val roundMatches = matches.filter { it.round == currentRound }

    var showPairings by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val cycleSeconds by produceState(initialValue = 10) {
        AppPreferences(context).settings.collect { value = it.tvCycleSeconds }
    }

    LaunchedEffect(cycleSeconds) {
        var tick = 0
        while (true) {
            val cycleTime = cycleSeconds.coerceIn(5, 30) * 1000L
            val steps = 50
            val step = cycleTime / steps
            for (i in 0..steps) {
                progress = i.toFloat() / steps
                delay(step)
            }
            tick++
            if (tick % 2 == 0) {
                showPairings = !showPairings
                page = 0
            } else {
                val count = if (showPairings) roundMatches.size else standings.size
                val maxPage = maxOf(0, (count - 1) / 12)
                if (maxPage > 0) page = (page + 1) % (maxPage + 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Text(
                tournament.name,
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                "Round $currentRound / ${tournament.totalRounds}",
                style = MaterialTheme.typography.titleMedium,
                color = TextGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (showPairings) "PAIRINGS" else "STANDINGS",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF4FC3F7),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            // Content area
            if (showPairings) {
                if (roundMatches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No pairings yet", color = TextGray, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    TvPairingsView(roundMatches, players, page)
                }
            } else {
                if (standings.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Standings not available", color = TextGray, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    TvStandingsView(standings, page)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFF4FC3F7),
                trackColor = Color(0xFF2A2A3E)
            )

            // Page dots
            val count = if (showPairings) roundMatches.size else standings.size
            val maxPage = maxOf(0, (count - 1) / 12)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in 0..maxPage) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == page) 10.dp else 7.dp)
                            .background(if (i == page) Color.White else Color(0xFF555566))
                    )
                }
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit TV Mode", tint = Color.White.copy(alpha = 0.7f))
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
            TvMatchRow(
                board = index + 1 + page * itemsPerPage,
                whiteName = white?.name,
                blackName = black?.name,
                result = match.result
            )
        }
    }
}

@Composable
fun TvMatchRow(board: Int, whiteName: String?, blackName: String?, result: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = DarkCardBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = Color(0xFF2A2A3E)
            ) {
                Text(
                    "$board",
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextGray
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                whiteName ?: "???",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )
            val resText = when (result) {
                "WHITE_WIN" -> "1-0"
                "BLACK_WIN" -> "0-1"
                "DRAW" -> "\u00BD-\u00BD"
                "BYE" -> "BYE"
                else -> "vs"
            }
            Text(
                resText,
                Modifier.width(56.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (result != null) TextWhite else TextGray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(12.dp))
            Text(
                blackName ?: "BYE",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun TvStandingsView(standings: List<PlayerStanding>, page: Int) {
    val itemsPerPage = 12
    val pagedStandings = standings.drop(page * itemsPerPage).take(itemsPerPage)

    // Header
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF2A2A3E)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("#", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, color = TextGray, fontWeight = FontWeight.Bold)
            Text("Name", Modifier.weight(2f), style = MaterialTheme.typography.labelMedium, color = TextGray, fontWeight = FontWeight.Bold)
            Text("Pts", Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, color = TextGray, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }

    LazyColumn {
        itemsIndexed(pagedStandings) { index, standing ->
            val rank = index + 1 + page * itemsPerPage
            val podiumColor = when (rank) {
                1 -> GoldMedalTv
                2 -> SilverMedalTv
                3 -> BronzeMedalTv
                else -> null
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = MaterialTheme.shapes.small,
                color = if (rank % 2 == 0) DarkCardBg else DarkBg
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$rank",
                        Modifier.width(48.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = podiumColor ?: TextGray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        standing.player.name,
                        Modifier.weight(2f),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (podiumColor != null) podiumColor else TextWhite
                    )
                    Text(
                        "${standing.score}",
                        Modifier.width(56.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
