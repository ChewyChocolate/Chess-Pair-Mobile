package com.example.chesspairmobile.ui.screens

import com.example.chesspairmobile.ui.components.RoundPicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.db.TeamEntity
import com.example.chesspairmobile.data.db.Converters
import com.example.chesspairmobile.logic.startRequirement
import com.example.chesspairmobile.logic.roundCompletionIssue
import com.example.chesspairmobile.logic.supportsForcedPairings
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.logic.calculateScores
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundsScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    teams: List<TeamEntity> = emptyList(),
    plannedRounds: Int = 0,
    matches: List<MatchEntity>,
    onStartTournament: () -> Unit,
    onGeneratePairings: (Int) -> Unit,
    onUpdateResult: (String, MatchResult?, Int?) -> Unit,
    onCompleteRound: () -> Unit,
    onRollbackRound: () -> Unit,
    onSwapPlayers: (String, Boolean, String, Boolean) -> Unit,
    enableForfeitScoring: Boolean = true,
    onAddForcedPairing: (String, String) -> Unit = { _, _ -> },
    onClearForcedPairings: () -> Unit = {},
    onBroadcast: () -> Unit = {},
    onEditTournament: () -> Unit = {}
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tournament selected")
        }
        return
    }

    var selectedRound by remember(tournament.id) { mutableIntStateOf(tournament.currentRound.coerceAtLeast(1)) }
    var editingPastRound by remember(tournament.id, selectedRound, tournament.status) { mutableStateOf(false) }
    var showCorrectionConfirm by remember(tournament.id, selectedRound) { mutableStateOf(false) }
    LaunchedEffect(tournament.currentRound) {
        selectedRound = tournament.currentRound.coerceAtLeast(1)
        editingPastRound = false
    }

    var showSwapMode by remember(tournament.id, selectedRound, tournament.status) { mutableStateOf(false) }
    var swapMatch1 by remember(tournament.id, selectedRound) { mutableStateOf<MatchEntity?>(null) }
    var swapColor1 by remember(tournament.id, selectedRound) { mutableStateOf<Boolean?>(null) }
    var pendingOnly by remember(tournament.id, selectedRound) { mutableStateOf(false) }
    var boardSearch by remember(tournament.id) { mutableStateOf("") }
    var showBoardSearch by remember(tournament.id) { mutableStateOf(false) }
    var showRoundGuidance by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showRollbackConfirm by remember { mutableStateOf(false) }
    var showForcedPairingDialog by remember { mutableStateOf(false) }

    val isSetup = tournament.status == "SETUP"
    val isActive = tournament.status == "ACTIVE"
    val currentRound = tournament.currentRound

    val roundMatches = matches.filter { it.round == selectedRound }
    val currentRoundMatches = matches.filter { it.round == currentRound }
    val currentRoundHasPairings = currentRoundMatches.isNotEmpty()
    val converters = remember { Converters() }
    val completionIssue = roundCompletionIssue(tournament.type, tournament.isTeamTournament, currentRoundMatches.map {
        com.example.chesspairmobile.data.models.Match(round = it.round, whiteId = it.whiteId, blackId = it.blackId,
            result = converters.toMatchResult(it.result), teamMatchId = it.teamMatchId)
    }, teams.map { com.example.chesspairmobile.data.models.Team(it.id, it.name, converters.toStringList(it.playerIds)) })
    val currentRoundResultsComplete = completionIssue == null
    val canForcePairs = supportsForcedPairings(tournament.type, tournament.isTeamTournament)
    val startError = startRequirement(
        players.map { com.example.chesspairmobile.data.models.Player(id = it.id, name = it.name, active = it.active, withdrawn = it.withdrawn, requestedByes = converters.toIntList(it.requestedByes)) },
        teams.map { com.example.chesspairmobile.data.models.Team(id = it.id, name = it.name, playerIds = converters.toStringList(it.playerIds)) },
        tournament.isTeamTournament, plannedRounds
    )
    val visibleMatches = roundMatches.filter { match ->
        (!pendingOnly || match.result == null) &&
            (boardSearch.isBlank() || match.boardNumber?.toString() == boardSearch.trim() ||
                players.any { (it.id == match.whiteId || it.id == match.blackId) && it.name.contains(boardSearch, true) })
    }

    // Scores before the selected round (FIDE pairings show score before the round)
    val scoreMap: Map<String, Double> = remember(players, matches, tournament, selectedRound) {
        try {
            val gson = Gson()
            val type = object : TypeToken<List<Int>>() {}.type
            val modelPlayers = players.map { p ->
                val byes = try { gson.fromJson<List<Int>>(p.requestedByes, type) ?: emptyList<Int>() } catch (_: Exception) { emptyList<Int>() }
                com.example.chesspairmobile.data.models.Player(
                    id = p.id, name = p.name, rating = p.rating, title = p.title, club = p.club,
                    pairingNumber = p.pairingNumber, active = p.active, withdrawn = p.withdrawn,
                    requestedByes = byes, penaltyPoints = p.penaltyPoints
                )
            }
            val modelMatches = matches.map { m ->
                com.example.chesspairmobile.data.models.Match(
                    id = m.id, round = m.round, whiteId = m.whiteId, blackId = m.blackId,
                    result = try { m.result?.let { com.example.chesspairmobile.data.models.MatchResult.valueOf(it) } } catch (_: Exception) { null },
                    boardNumber = m.boardNumber, teamMatchId = m.teamMatchId, isManual = m.isManual, pgn = m.pgn
                )
            }
            val upTo = (selectedRound - 1).coerceAtLeast(0)
            // upTo 0 means no completed rounds yet -> all scores 0 but requested byes for round 0 none
            calculateScores(
                modelPlayers, modelMatches,
                tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss,
                upToRound = if (upTo == 0) 0 else upTo
            ).scores
        } catch (_: Exception) { emptyMap() }
    }
    fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", v)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isActive && selectedRound == currentRound) Surface(shadowElevation = 4.dp) {
                Button(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = !currentRoundHasPairings || currentRoundResultsComplete,
                    onClick = { if (!currentRoundHasPairings) onGeneratePairings(currentRound) else showCompleteConfirm = true }) {
                    Text(when {
                        !currentRoundHasPairings -> "Generate Round $currentRound pairings"
                        !currentRoundResultsComplete -> if (currentRoundMatches.any { it.result == null }) "${currentRoundMatches.count { it.result == null }} results remaining" else "Resolve knockout ${if (tournament.isTeamTournament) "ties" else "draws"}"
                        currentRound == tournament.totalRounds -> "Review and finish tournament"
                        else -> "Review and complete Round $currentRound"
                    })
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Title row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Rounds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (selectedRound == currentRound && (isActive || tournament.status == "COMPLETED")) {
                            DropdownMenuItem(text = { Text(if (isActive) "Roll back Round $currentRound" else "Reopen tournament") }, onClick = { showMenu = false; showRollbackConfirm = true })
                        }
                        if (!tournament.isTeamTournament && selectedRound == currentRound && isActive && roundMatches.isNotEmpty() && roundMatches.filter { it.blackId != null }.all { it.result == null }) {
                            DropdownMenuItem(text = { Text("Swap Players") }, onClick = { showMenu = false; showSwapMode = true })
                        }
                        if (tournament.type == "KNOCKOUT") DropdownMenuItem(text = { Text("Round guidance") },
                            onClick = { showMenu = false; showRoundGuidance = true })
                        if (canForcePairs) DropdownMenuItem(
                            text = { Text("Forced pairings…") },
                            enabled = isSetup || (isActive && (!currentRoundHasPairings || currentRound < tournament.totalRounds)),
                            onClick = { showMenu = false; showForcedPairingDialog = true }
                        )
                        if (canForcePairs) DropdownMenuItem(
                            text = { Text("Clear forced pairings") },
                            enabled = (isSetup || isActive) && tournament.forcedPairings != "[]",
                            onClick = { showMenu = false; onClearForcedPairings() }
                        )
                        if (!tournament.liveBroadcastUrl.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Broadcast results") },
                                onClick = { showMenu = false; onBroadcast() }
                            )
                        }
                    }
                }
            }
            if (isSetup) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Review tournament setup", style = MaterialTheme.typography.titleLarge)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${tournament.type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} · ${if (tournament.isTeamTournament) "Teams" else "Individual"}", style = MaterialTheme.typography.titleMedium)
                                Text(if (plannedRounds > 0) "$plannedRounds rounds planned" else "Rounds calculated from the roster")
                                Text("${players.count { it.active && !it.withdrawn }} active players${if (tournament.isTeamTournament) " · ${teams.size} teams" else ""}")
                                Text("Time control: ${tournament.timeControl ?: "Not specified"}")
                                Text("Player scoring: win ${tournament.pointsForWin} · draw ${tournament.pointsForDraw} · loss ${tournament.pointsForLoss}")
                                if (tournament.isTeamTournament) Text("Team order: match points, game points, then Sonneborn-Berger")
                                Text("${if (tournament.isTeamTournament) "Individual tiebreaks" else "Tiebreaks"}: ${converters.toTiebreakList(tournament.tiebreakOrder).joinToString { it.name.lowercase().replace('_', ' ') }.ifBlank { "None" }}")
                                if (!tournament.isTeamTournament) {
                                    Text("Avoid club pairings: ${if (tournament.avoidClubPairings) "On" else "Off"}")
                                    if (tournament.type == "SWISS") Text("Acceleration: ${tournament.accelerationMode.lowercase().replaceFirstChar { it.uppercase() }}")
                                }
                            }
                        }
                        OutlinedButton(onClick = onEditTournament, modifier = Modifier.fillMaxWidth()) { Text("Edit tournament") }
                        Button(
                            onClick = onStartTournament,
                            enabled = startError == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Tournament")
                        }
                        Spacer(Modifier.height(8.dp))
                        if (startError != null) {
                            Text(
                                startError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundPicker(selectedRound, currentRound,
                        tournament.totalRounds, { selectedRound = it }, Modifier.weight(1f))
                    if (selectedRound == currentRound && isActive) {
                        Text(when {
                            roundMatches.isEmpty() -> "Not paired"
                            currentRoundResultsComplete -> "All results in"
                            roundMatches.all { it.result != null } -> "Playoff required"
                            else -> "Results pending"
                        }, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (selectedRound <= currentRound) {
                        TextButton(onClick = { if (editingPastRound) editingPastRound = false else showCorrectionConfirm = true }) {
                            Text(if (editingPastRound) "Done editing" else "Correct results")
                        }
                    }
                }
                if (showSwapMode) {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Swap Mode: Tap two players to swap",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { showSwapMode = false; swapMatch1 = null; swapColor1 = null }) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                if (roundMatches.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${roundMatches.count { it.result != null }} / ${roundMatches.size} entered",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            FilterChip(selected = pendingOnly, onClick = { pendingOnly = !pendingOnly },
                                label = { Text("Pending (${roundMatches.count { it.result == null }})") })
                            IconButton(onClick = { showBoardSearch = !showBoardSearch; if (!showBoardSearch) boardSearch = "" }) {
                                Icon(if (showBoardSearch) Icons.Default.Close else Icons.Default.Search,
                                    if (showBoardSearch) "Close board search" else "Find board or player")
                            }
                        }
                        if (showBoardSearch) OutlinedTextField(value = boardSearch, onValueChange = { boardSearch = it },
                            label = { Text("Find board or player") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        if (isActive && selectedRound == currentRound && completionIssue != null && roundMatches.all { it.result != null }) {
                            Text(completionIssue, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (editingPastRound) Text("Correcting Round $selectedRound · later pairings stay as drawn",
                            Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(visibleMatches.sortedBy { it.boardNumber }, key = { it.id }) { match ->
                        MatchCard(
                            match = match,
                            players = players,
                            scores = scoreMap,
                            onUpdateResult = { result -> onUpdateResult(match.id, result, if (editingPastRound) selectedRound else null) },
                            isSwapMode = showSwapMode && match.blackId != null,
                            isSwapSelected = swapMatch1?.id == match.id,
                            onSwapSelect = { color ->
                                if (swapMatch1 == null) {
                                    swapMatch1 = match
                                    swapColor1 = color
                                } else {
                                    val m1 = swapMatch1!!
                                    val c1 = swapColor1!!
                                    if (m1.id != match.id) {
                                        onSwapPlayers(m1.id, c1, match.id, color)
                                    }
                                    swapMatch1 = null
                                    swapColor1 = null
                                }
                            },
                            showResults = (selectedRound == currentRound && isActive) || editingPastRound,
                            teamLabel = if (tournament.isTeamTournament) teams.filter { team -> converters.toStringList(team.playerIds).any { it == match.whiteId || it == match.blackId } }.joinToString(" vs ") { it.name } else null,
                            enableForfeitScoring = enableForfeitScoring,
                            allowDraw = tournament.type != "KNOCKOUT" || tournament.isTeamTournament,
                            swapSelectedColor = if (swapMatch1?.id == match.id) swapColor1 else null,
                            swapUnavailable = showSwapMode && match.blackId == null
                        )
                    }
                    if (visibleMatches.isEmpty() && roundMatches.isNotEmpty()) item {
                        Text("No boards match this filter", Modifier.padding(24.dp))
                    }
                }

                if (roundMatches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (selectedRound == currentRound && isActive) {
                                    Text("Tap Generate Round $selectedRound pairings below to create matches",
                                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            } else {
                                Text("No matches in this round", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRoundGuidance) AlertDialog(onDismissRequest = { showRoundGuidance = false },
        title = { Text("Knockout results") },
        text = { Text(if (tournament.isTeamTournament) "Enter all board results. If a team match is tied, resolve it with a playoff and enter the deciding result before completing the round."
            else "Resolve draws with a playoff and enter the deciding result before completing this round.") },
        confirmButton = { TextButton(onClick = { showRoundGuidance = false }) { Text("Got it") } })

    // Confirm dialogs
    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = { Text(if (currentRound == tournament.totalRounds) "Finish tournament?" else "Complete Round $currentRound?") },
            text = { Text(if (currentRound == tournament.totalRounds) "All ${currentRoundMatches.size} results are entered. Finish the tournament and review final standings. Results will be read-only unless you choose to correct them." else "All ${currentRoundMatches.size} results are entered. Complete this round and move to Round ${currentRound + 1}. Results will be read-only unless you choose to correct them.") },
            confirmButton = {
                TextButton(onClick = {
                    onCompleteRound()
                    showCompleteConfirm = false
                }) { Text(if (currentRound == tournament.totalRounds) "Finish tournament" else "Complete round") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRollbackConfirm) {
        AlertDialog(
            onDismissRequest = { showRollbackConfirm = false },
            title = { Text("Rollback Round?") },
            text = {
                if (tournament.status == "COMPLETED") {
                    Text("Reopen the tournament to edit the final round? All recorded results, including byes, will be kept.")
                } else {
                    Text("Roll back to Round ${currentRound - 1}? All pairings from Round $currentRound will be deleted.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRollbackRound()
                    showRollbackConfirm = false
                }) {
                    Text("Rollback", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRollbackConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showCorrectionConfirm) {
        AlertDialog(onDismissRequest = { showCorrectionConfirm = false },
            title = { Text("Correct Round $selectedRound results?") },
            text = { Text("Changes update standings immediately. Pairings already drawn for later rounds will stay unchanged. Choose Done editing when finished.") },
            confirmButton = { TextButton(onClick = { editingPastRound = true; showCorrectionConfirm = false }) { Text("Edit results") } },
            dismissButton = { TextButton(onClick = { showCorrectionConfirm = false }) { Text("Cancel") } })
    }

    if (showForcedPairingDialog) {
        ForcedPairingDialog(
            players = players,
            existingPairings = parseForcedPairings(tournament.forcedPairings),
            onDismiss = { showForcedPairingDialog = false },
            onAdd = { white, black ->
                onAddForcedPairing(white, black)
                showForcedPairingDialog = false
            }
        )
    }
}

private fun parseForcedPairings(json: String?): List<Pair<String, String>> {
    if (json.isNullOrBlank() || json == "[]") return emptyList()
    return try {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        val list: List<String> = gson.fromJson(json, type) ?: return emptyList()
        list.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    } catch (_: Exception) { emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForcedPairingDialog(
    players: List<PlayerEntity>,
    existingPairings: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var whiteId by remember { mutableStateOf<String?>(null) }
    var blackId by remember { mutableStateOf<String?>(null) }
    var whiteMenu by remember { mutableStateOf(false) }
    var blackMenu by remember { mutableStateOf(false) }
    val assignedIds = existingPairings.flatMap { listOf(it.first, it.second) }.toSet()

    val playerName: (String?) -> String = { id ->
        players.firstOrNull { it.id == id }?.name ?: "Select…"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forced Pairing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Force a specific pair for the next unpaired round. Each player can appear in one forced pairing. The other pairings are generated normally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (existingPairings.isNotEmpty()) {
                    existingPairings.forEach { (white, black) -> Text("${playerName(white)} (White) vs ${playerName(black)} (Black)", style = MaterialTheme.typography.bodyMedium) }
                }
                ExposedDropdownMenuBox(expanded = whiteMenu, onExpandedChange = { whiteMenu = it }) {
                    OutlinedTextField(
                        value = playerName(whiteId),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("White") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = whiteMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = whiteMenu, onDismissRequest = { whiteMenu = false }) {
                        players.filter { it.active && !it.withdrawn && it.id !in assignedIds && it.id != blackId }.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { whiteId = p.id; whiteMenu = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = blackMenu, onExpandedChange = { blackMenu = it }) {
                    OutlinedTextField(
                        value = playerName(blackId),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Black") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = blackMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = blackMenu, onDismissRequest = { blackMenu = false }) {
                        players.filter { it.active && !it.withdrawn && it.id !in assignedIds && it.id != whiteId }.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { blackId = p.id; blackMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = whiteId
                    val b = blackId
                    if (w != null && b != null && w != b) onAdd(w, b)
                },
                enabled = whiteId != null && blackId != null && whiteId != blackId && whiteId !in assignedIds && blackId !in assignedIds
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MatchCard(
    match: MatchEntity,
    players: List<PlayerEntity>,
    onUpdateResult: (MatchResult?) -> Unit,
    isSwapMode: Boolean,
    isSwapSelected: Boolean,
    onSwapSelect: (Boolean) -> Unit,
    showResults: Boolean,
    enableForfeitScoring: Boolean = true,
    scores: Map<String, Double> = emptyMap(),
    teamLabel: String? = null,
    allowDraw: Boolean = true,
    swapSelectedColor: Boolean? = null,
    swapUnavailable: Boolean = false
) {
    val whitePlayer = players.find { it.id == match.whiteId }
    val blackPlayer = players.find { it.id == match.blackId }
    val isBye = match.blackId == null
    fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", v)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = if (isSwapSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            teamLabel?.let { Text(it, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Board number
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isBye) "BYE" else "${match.boardNumber ?: "?"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))

                // White player
                Column(modifier = Modifier.weight(1f)) {
                    PlayerRow(
                        player = whitePlayer,
                        colorName = "White",
                        isSwapMode = isSwapMode,
                        isSelected = isSwapSelected && swapSelectedColor == true,
                        onSwapClick = { onSwapSelect(true) },
                        color = Color(0xFFF5F5F5),
                        score = whitePlayer?.id?.let { scores[it] }?.let { formatScore(it) }
                    )
                    if (!isBye) {
                        Spacer(Modifier.height(6.dp))
                        PlayerRow(
                            player = blackPlayer,
                            colorName = "Black",
                            isSwapMode = isSwapMode,
                            isSelected = isSwapSelected && swapSelectedColor == false,
                            onSwapClick = { onSwapSelect(false) },
                            color = Color(0xFF2C2C2C),
                            score = blackPlayer?.id?.let { scores[it] }?.let { formatScore(it) }
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Text(
                                "BYE",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

            }
            if (swapUnavailable) Text("Bye recipients cannot be swapped", style = MaterialTheme.typography.bodyMedium)
            if (isBye && showResults && match.result == null) {
                TextButton(onClick = { onUpdateResult(MatchResult.BYE) }) { Text("Restore bye result") }
            }
            if (!showResults && match.result != null && !isBye) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val (label, color) = when (match.result) {
                        "WHITE_WIN" -> "1-0" to Color(0xFF2E7D32)
                        "BLACK_WIN" -> "0-1" to Color(0xFF2E7D32)
                        "DRAW" -> "\u00BD-\u00BD" to Color(0xFF546E7A)
                        "FORFEIT_WHITE" -> "Black wins by forfeit" to MaterialTheme.colorScheme.onSurfaceVariant
                        "FORFEIT_BLACK" -> "White wins by forfeit" to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> null to null
                    }
                    if (label != null && color != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (!isSwapMode && showResults && !isBye) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ResultSelector(
                        currentResult = match.result?.let { try { MatchResult.valueOf(it) } catch (_: Exception) { null } },
                        onSelect = onUpdateResult,
                        enableForfeitScoring = enableForfeitScoring,
                        allowDraw = allowDraw
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerRow(
    player: PlayerEntity?,
    colorName: String,
    isSwapMode: Boolean,
    isSelected: Boolean,
    onSwapClick: () -> Unit,
    color: Color,
    score: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A labeled piece marker leaves more room for the player's name.
        Surface(
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(24.dp).semantics { contentDescription = colorName },
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(colorName.take(1), style = MaterialTheme.typography.labelMedium,
                    color = if (colorName == "White") Color.Black else Color.White)
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player?.name ?: "???",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (score != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            score,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            player?.rating?.let {
                Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isSwapMode) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSwapClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    "Swap $colorName",
                    modifier = Modifier.size(22.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSelector(
    currentResult: MatchResult?, onSelect: (MatchResult?) -> Unit, enableForfeitScoring: Boolean = true,
    allowDraw: Boolean = true
) {
    var showExceptions by remember { mutableStateOf(false) }
    val outcomes = listOf(MatchResult.WHITE_WIN to "1–0", MatchResult.DRAW to "½–½", MatchResult.BLACK_WIN to "0–1")
        .filter { allowDraw || it.first != MatchResult.DRAW }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                outcomes.forEachIndexed { index, (result, notation) ->
                    val description = when (result) {
                        MatchResult.WHITE_WIN -> "White wins"
                        MatchResult.BLACK_WIN -> "Black wins"
                        else -> "Draw"
                    }
                    SegmentedButton(selected = currentResult == result, onClick = { onSelect(result) },
                        shape = SegmentedButtonDefaults.itemShape(index, outcomes.size), icon = {},
                        label = { Text(notation, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.semantics { contentDescription = description })
                }
            }
            Box {
                IconButton(onClick = { showExceptions = true }) { Icon(Icons.Default.MoreHoriz, "More result options") }
                DropdownMenu(expanded = showExceptions, onDismissRequest = { showExceptions = false }) {
                    if (enableForfeitScoring) {
                        DropdownMenuItem(text = { Text("White wins by forfeit") }, onClick = { onSelect(MatchResult.FORFEIT_BLACK); showExceptions = false })
                        DropdownMenuItem(text = { Text("Black wins by forfeit") }, onClick = { onSelect(MatchResult.FORFEIT_WHITE); showExceptions = false })
                    }
                    DropdownMenuItem(text = { Text("Clear result") }, enabled = currentResult != null,
                        onClick = { onSelect(null); showExceptions = false })
                }
            }
        }
        if (currentResult == MatchResult.FORFEIT_WHITE || currentResult == MatchResult.FORFEIT_BLACK) {
            Text(if (currentResult == MatchResult.FORFEIT_WHITE) "Black wins by forfeit" else "White wins by forfeit",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
