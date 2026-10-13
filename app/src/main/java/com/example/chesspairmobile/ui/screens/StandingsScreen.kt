package com.example.chesspairmobile.ui.screens

import com.example.chesspairmobile.ui.components.ViewModeSelector

import com.example.chesspairmobile.ui.components.RoundPicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.db.TeamEntity
import com.example.chesspairmobile.data.db.Converters
import com.example.chesspairmobile.logic.calculateTeamStandings
import com.example.chesspairmobile.logic.standingsRoundLabel
import com.example.chesspairmobile.logic.standingsSnapshotRound
import androidx.compose.runtime.saveable.rememberSaveable
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
    teams: List<TeamEntity> = emptyList(),
    onExportCsv: (Int, Boolean) -> String,
    onUpdateTiebreakOrder: ((List<TiebreakType>) -> Unit)? = null
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Leaderboard, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("No tournament selected", style = MaterialTheme.typography.headlineSmall)
            }
        }
        return
    }

    if (tournament.status == "SETUP") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Leaderboard, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                Text("Tournament hasn't started yet", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("Standings appear as results are entered. Review tiebreaks before starting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onUpdateTiebreakOrder != null) TiebreakConfigPanel(
                    Converters().toTiebreakList(tournament.tiebreakOrder), onUpdateTiebreakOrder,
                    try { TournamentType.valueOf(tournament.type) } catch (_: Exception) { null },
                    title = if (tournament.isTeamTournament) "Individual tiebreaks" else "Tiebreak settings"
                )
            }
        }
        return
    }

    val tiebreakOrder = remember(tournament.tiebreakOrder) { Converters().toTiebreakList(tournament.tiebreakOrder) }

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

    var selectedRound by rememberSaveable(tournament.id) { mutableIntStateOf(tournament.currentRound) }
    val snapshotRound = standingsSnapshotRound(selectedRound, modelMatches)
    var teamView by rememberSaveable(tournament.id) { mutableStateOf(tournament.isTeamTournament) }
    var showAllTiebreaks by rememberSaveable(tournament.id) { mutableStateOf(false) }
    val modelTeams = remember(teams) { teams.map { com.example.chesspairmobile.data.models.Team(it.id, it.name, Converters().toStringList(it.playerIds)) } }
    val teamStandings = remember(modelTeams, modelMatches, snapshotRound) { calculateTeamStandings(modelTeams, modelMatches, snapshotRound) }
    var exportRound by rememberSaveable { mutableIntStateOf(snapshotRound) }
    var exportTeams by rememberSaveable { mutableStateOf(teamView) }
    var selectedStanding by remember { mutableStateOf<PlayerStanding?>(null) }
    LaunchedEffect(tournament.currentRound) {
        selectedRound = tournament.currentRound
    }

    val standings = remember(modelPlayers, modelMatches, tournament, tiebreakOrder, snapshotRound) {
        calculateStandings(
            modelPlayers, modelMatches,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss,
            tiebreakOrder,
            upToRound = snapshotRound,
            tournamentType = if (tournament.isTeamTournament) null else com.example.chesspairmobile.data.models.TournamentType.valueOf(tournament.type)
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
        val csv = onExportCsv(exportRound, exportTeams)
        if (csv.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("No standings to export") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot write to the selected file" }.use { out ->
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
                FilledTonalButton(enabled = snapshotRound > 0, onClick = {
                    exportRound = snapshotRound
                    exportTeams = teamView
                    val safeName = tournament.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                    exportLauncher.launch("${safeName}_${if (teamView) "team" else "individual"}_round_${snapshotRound}.csv")
                }) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }
            Text(standingsRoundLabel(selectedRound, modelMatches), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            if (tournament.isTeamTournament) ViewModeSelector(
                listOf("Teams", "Individuals"), if (teamView) 0 else 1, { teamView = it == 0 },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            RoundPicker(selectedRound, tournament.currentRound,
                tournament.totalRounds, { selectedRound = it }, Modifier.padding(horizontal = 8.dp))
            if (teamView) {
                Text("Ranked by match points, game points, then Sonneborn-Berger. Match points: win 2 · draw 1 · loss 0.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(teamStandings, key = { _, standing -> standing.team.id }) { index, standing ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${index + 1}. ${standing.team.name}", style = MaterialTheme.typography.titleMedium)
                                Text("${standing.matchPoints} match points · ${standing.gamePoints} game points", style = MaterialTheme.typography.bodyLarge)
                                Text("Sonneborn-Berger: ${standing.sonnebornBerger}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                val columns = remember(tiebreakOrder, showAllTiebreaks) {
                    if (showAllTiebreaks) tiebreakColumns(tiebreakOrder) else emptyList()
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAllTiebreaks = !showAllTiebreaks }) {
                        Text("All tiebreaks")
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showAllTiebreaks) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                    }
                    Text(if (showAllTiebreaks) "Swipe table for more" else "Tap a player for history",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Expand the whole table together so headings and rows stay aligned.
                    val tableWidth = if (showAllTiebreaks) maxOf(maxWidth, (36 + 180 + 56 + 24 + columns.size * 72).dp) else maxWidth
                    Column(Modifier.horizontalScroll(tableScrollState)) {
                        Column(Modifier.width(tableWidth)) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("#", Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    Text("Player", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                    Text("Pts", Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    columns.forEach { col ->
                                        Text(col.label, Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            LazyColumn(Modifier.weight(1f)) {
                                itemsIndexed(standings, key = { _, standing -> standing.player.id }) { index, standing ->
                                    StandingRow(index, standing, columns, onClick = { selectedStanding = standing })
                                }
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedStanding?.let { standing ->
        val stats = remember(standing, modelPlayers, modelMatches, snapshotRound) {
            getPlayerStats(modelPlayers, modelMatches.filter { it.round <= snapshotRound }, standing.player.id)
        }
        // Also collect requested byes that have no match entry to show them
        val historyWithByes = remember(stats, standing, tournament, snapshotRound) {
            val existingRounds = stats.stats.map { it.round }.toSet()
            val requested = standing.player.requestedByes.filter { it in 1..snapshotRound && it !in existingRounds }
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
                    Text(if (snapshotRound > 0) "Through Round $snapshotRound" else "No rounds started", style = MaterialTheme.typography.bodyMedium)
                    standing.player.title?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (standing.player.withdrawn) Text("Withdrawn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    tiebreakColumns(tiebreakOrder).forEach { column ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(column.description, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(column.format(standing), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                                        color = MaterialTheme.colorScheme.onSurface
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
fun StandingRow(index: Int, standing: PlayerStanding, columns: List<TiebreakColumn>, onClick: () -> Unit = {}) {
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
            .clickable(onClick = onClick),
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
                        color = MaterialTheme.colorScheme.onSurface
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

            Column(Modifier.weight(1f)) {
                Text(
                    standing.player.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (standing.player.withdrawn) TextDecoration.LineThrough else TextDecoration.None
                )
                if (standing.player.withdrawn) Text("Withdrawn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                standing.player.rating?.let {
                    Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "${standing.score}",
                Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Row { columns.forEach { col ->
                Text(
                    col.format(standing),
                    Modifier.width(72.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            }
        }
    }
}

data class TiebreakColumn(
    val label: String,
    val description: String = label,
    val format: (PlayerStanding) -> String
)

private fun tiebreakColumns(order: List<TiebreakType>): List<TiebreakColumn> = order.mapNotNull { tb ->
    when (tb) {
        TiebreakType.BUCHHOLZ -> TiebreakColumn("BH", "BH: Buchholz") { "%.1f".format(it.buchholz) }
        TiebreakType.MEDIAN_BUCHHOLZ -> TiebreakColumn("MB", "MB: Median Buchholz") { "%.1f".format(it.medianBuchholz) }
        TiebreakType.BUCHHOLZ_CUT1 -> TiebreakColumn("BC1", "BC1: Buchholz Cut-1") { "%.1f".format(it.buchholzCut1) }
        TiebreakType.SONNEBORN_BERGER -> TiebreakColumn("S-B", "S-B: Sonneborn-Berger") { "%.2f".format(it.sonnebornBerger) }
        TiebreakType.MOST_WINS -> TiebreakColumn("W", "W: Most wins") { "${it.mostWins}" }
        TiebreakType.PROGRESSIVE -> TiebreakColumn("Prog", "Prog: Progressive") { "%.1f".format(it.progressive) }
        TiebreakType.DIRECT_ENCOUNTER -> TiebreakColumn("DE", "DE: Direct encounter") { "%.1f".format(it.directEncounter) }
        TiebreakType.RATING -> TiebreakColumn("Rtg", "Rtg: Rating") { it.player.rating?.toString() ?: "—" }
    }
}
