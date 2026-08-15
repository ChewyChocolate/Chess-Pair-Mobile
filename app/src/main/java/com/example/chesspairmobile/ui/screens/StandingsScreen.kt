package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.example.chesspairmobile.data.models.TournamentType
import com.example.chesspairmobile.ui.components.TiebreakConfigPanel
import com.example.chesspairmobile.ui.theme.GoldMedal
import com.example.chesspairmobile.ui.theme.SilverMedal
import com.example.chesspairmobile.ui.theme.BronzeMedal
import com.example.chesspairmobile.logic.calculateStandings
import com.example.chesspairmobile.logic.getPlayerStats
import kotlinx.coroutines.launch

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
                        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.PROGRESSIVE)
                }
        } catch (e: Exception) {
            listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
                TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.PROGRESSIVE)
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

    var selectedRound by remember { mutableIntStateOf(tournament.currentRound) }
    var selectedStanding by remember { mutableStateOf<PlayerStanding?>(null) }
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

    val tableScrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = onExportCsv()
        if (csv.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No standings to export") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                snackbarHostState.showSnackbar("Standings exported")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Export failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Standings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = {
                    val safeName = tournament.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                    exportLauncher.launch("${safeName}_standings.csv")
                }) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }
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
                    onTiebreakOrderChanged = onUpdateTiebreakOrder,
                    tournamentType = try { TournamentType.valueOf(tournament.type) } catch (_: Exception) { null }
                )
                HorizontalDivider()
            }

            // Table header
            val columns = remember(tiebreakOrder) { tiebreakColumns(tiebreakOrder) }
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(tableScrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Name", Modifier.width(160.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Pts", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    columns.forEach { col ->
                        Text(col.label, Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }

            LazyColumn {
                itemsIndexed(standings) { index, standing ->
                    StandingRow(
                        index = index,
                        standing = standing,
                        columns = columns,
                        scrollState = tableScrollState,
                        onClick = { selectedStanding = standing }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedStanding?.let { standing ->
        val stats = remember(standing, modelPlayers, modelMatches) {
            getPlayerStats(modelPlayers, modelMatches, standing.player.id)
        }
        // Also collect requested byes that have no match entry to show them
        val historyWithByes = remember(stats, standing, tournament) {
            val existingRounds = stats.stats.map { it.round }.toSet()
            val requested = standing.player.requestedByes.filter { it in 1..tournament.currentRound && it !in existingRounds }
                .map { r ->
                    com.example.chesspairmobile.data.models.PlayerMatchStat(
                        round = r,
                        opponentId = null,
                        opponentName = "BYE (requested)",
                        color = null,
                        result = "0.5"
                    )
                }
            (stats.stats + requested).sortedBy { it.round }
        }
        AlertDialog(
            onDismissRequest = { selectedStanding = null },
            title = {
                Column {
                    Text(standing.player.name, fontWeight = FontWeight.Bold)
                    standing.player.title?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        standing.player.rating?.let {
                            Text("Rating $it", style = MaterialTheme.typography.labelSmall)
                        }
                        standing.player.club?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${standing.score} pts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (historyWithByes.isEmpty()) {
                        Text("No games played yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("R", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("Clr", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("Opponent", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Res", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        historyWithByes.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${s.round}", Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                // Color
                                if (s.color != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (s.color == 'W') Color(0xFFF5F5F5) else Color(0xFF2C2C2C),
                                        border = if (s.color == 'W') androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null,
                                        modifier = Modifier.width(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text("${s.color}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (s.color == 'W') Color.Black else Color.White)
                                        }
                                    }
                                } else {
                                    Text("-", Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    s.opponentName,
                                    Modifier.weight(1f).padding(horizontal = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                val (resText, resColor) = when (s.result) {
                                    "1" -> "1-0" to Color(0xFF2E7D32)
                                    "0" -> "0-1" to Color(0xFFC62828)
                                    "0.5" -> "½-½" to Color(0xFF546E7A)
                                    null -> "vs" to MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> (s.result ?: "-") to MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = resColor.copy(alpha = if (s.result == null) 0.08f else 0.12f)
                                ) {
                                    Text(
                                        resText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).width(44.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = resColor
                                    )
                                }
                            }
                        }
                        if (stats.hasColorWarning || stats.hasByeWarning) {
                            Spacer(Modifier.height(8.dp))
                            if (stats.hasColorWarning) {
                                Text("⚠ ${stats.consecutiveColors} same colors in a row (last ${stats.lastColor})", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF6C00))
                            }
                            if (stats.hasByeWarning) {
                                Text("⚠ ${stats.byeCount} byes", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF6C00))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStanding = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun StandingRow(index: Int, standing: PlayerStanding, columns: List<TiebreakColumn>, scrollState: ScrollState, onClick: () -> Unit = {}) {
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
        modifier = Modifier.fillMaxWidth()
            .then(if (standing.player.withdrawn) Modifier.alpha(0.55f) else Modifier)
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
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

            Column(Modifier.width(160.dp)) {
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
                Modifier.width(48.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            columns.forEach { col ->
                Text(
                    col.format(standing),
                    Modifier.width(56.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class TiebreakColumn(
    val label: String,
    val format: (PlayerStanding) -> String
)

private fun tiebreakColumns(order: List<TiebreakType>): List<TiebreakColumn> = order.mapNotNull { tb ->
    when (tb) {
        TiebreakType.BUCHHOLZ -> TiebreakColumn("BH") { "%.1f".format(it.buchholz) }
        TiebreakType.MEDIAN_BUCHHOLZ -> TiebreakColumn("MB") { "%.1f".format(it.medianBuchholz) }
        TiebreakType.BUCHHOLZ_CUT1 -> TiebreakColumn("BC1") { "%.1f".format(it.buchholzCut1) }
        TiebreakType.SONNEBORN_BERGER -> TiebreakColumn("S-B") { "%.2f".format(it.sonnebornBerger) }
        TiebreakType.MOST_WINS -> TiebreakColumn("W") { "${it.mostWins}" }
        TiebreakType.PROGRESSIVE -> TiebreakColumn("Prog") { "%.1f".format(it.progressive) }
        TiebreakType.DIRECT_ENCOUNTER -> TiebreakColumn("DE") { "%.1f".format(it.directEncounter) }
        else -> null
    }
}
