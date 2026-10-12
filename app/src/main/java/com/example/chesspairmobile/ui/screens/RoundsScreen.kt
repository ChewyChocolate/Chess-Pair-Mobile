package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    onBroadcast: () -> Unit = {}
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
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showRollbackConfirm by remember { mutableStateOf(false) }
    var showForcedPairingDialog by remember { mutableStateOf(false) }

    val isSetup = tournament.status == "SETUP"
    val isActive = tournament.status == "ACTIVE"
    val currentRound = tournament.currentRound

    val roundMatches = matches.filter { it.round == selectedRound }
    val currentRoundMatches = matches.filter { it.round == currentRound }
    val currentRoundHasPairings = currentRoundMatches.isNotEmpty()
    val currentRoundResultsComplete = currentRoundMatches
        .all { it.result != null }
    val converters = remember { Converters() }
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
                        !currentRoundResultsComplete -> "${currentRoundMatches.count { it.result == null }} results remaining"
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
                        DropdownMenuItem(
                            text = { Text("Forced pairings…") },
                            enabled = isSetup || isActive,
                            onClick = { showMenu = false; showForcedPairingDialog = true }
                        )
                        DropdownMenuItem(
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.GridView, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onStartTournament,
                            enabled = startError == null
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
                }
            } else {
                // Round selector
                Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val maxDisplayRound = maxOf(currentRound, 1)
                        for (r in 1..maxDisplayRound) {
                            FilterChip(
                                selected = selectedRound == r,
                                onClick = { selectedRound = r },
                                label = { Text("$r") },
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Round $selectedRound / ${tournament.totalRounds}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedRound == currentRound && isActive) {
                            val hasMatches = roundMatches.isNotEmpty()
                            val allDone = roundMatches.filter { it.blackId != null }.all { it.result != null }
                            when {
                                hasMatches && !allDone -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "Results pending",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                !hasMatches -> {
                                    Text("Pairings not generated", style = MaterialTheme.typography.bodyMedium)
                                }
                                else -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "All results in",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        } else if (selectedRound <= currentRound) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        "Completed",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { if (editingPastRound) editingPastRound = false else showCorrectionConfirm = true }) {
                                    Text(if (editingPastRound) "Done editing" else "Correct results")
                                }
                            }
                        } else if (selectedRound > currentRound) {
                            // Future round - no action
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
                } else if (!tournament.isTeamTournament && selectedRound == currentRound && isActive && roundMatches.isNotEmpty() && roundMatches.filter { it.blackId != null }.all { it.result == null }) {
                    TextButton(
                        onClick = { showSwapMode = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Swap Players")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (roundMatches.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("${roundMatches.count { it.result != null }} of ${roundMatches.size} results entered", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = boardSearch, onValueChange = { boardSearch = it }, label = { Text("Find board or player") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !pendingOnly, onClick = { pendingOnly = false }, label = { Text("All boards") })
                            FilterChip(selected = pendingOnly, onClick = { pendingOnly = true }, label = { Text("Pending (${roundMatches.count { it.result == null }})") })
                        }
                        if (editingPastRound) Text("Correcting Round $selectedRound · later pairings stay as drawn", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                LazyColumn(Modifier.weight(1f)) {
                    items(visibleMatches.sortedBy { it.boardNumber }, key = { it.id }) { match ->
                        MatchCard(
                            match = match,
                            players = players,
                            scores = scoreMap,
                            onUpdateResult = { result -> onUpdateResult(match.id, result, if (editingPastRound) selectedRound else null) },
                            isSwapMode = showSwapMode,
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
                            enableForfeitScoring = enableForfeitScoring
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
                    Text("Reopen the tournament? Results from the final round will be cleared.")
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

    val playerName: (String?) -> String = { id ->
        players.firstOrNull { it.id == id }?.name ?: "Select…"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forced Pairing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Force a specific pair for the next round. The other pairings are still generated normally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (existingPairings.isNotEmpty()) {
                    Text("Existing forced pairings: ${existingPairings.size}", style = MaterialTheme.typography.labelMedium)
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
                        players.filter { it.active && !it.withdrawn }.forEach { p ->
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
                        players.filter { it.active && !it.withdrawn && it.id != whiteId }.forEach { p ->
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
                enabled = whiteId != null && blackId != null && whiteId != blackId
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
    teamLabel: String? = null
) {
    val whitePlayer = players.find { it.id == match.whiteId }
    val blackPlayer = players.find { it.id == match.blackId }
    val isBye = match.blackId == null
    fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", v)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (isSwapMode) Modifier.clickable { /* handled per player */ }
                else Modifier
            ),
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
                        isSelected = isSwapSelected,
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
                            isSelected = isSwapSelected,
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
                        enableForfeitScoring = enableForfeitScoring
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
        Text(colorName, Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium)
        // Color indicator circle
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(18.dp),
            color = color,
            border = if (color == Color(0xFFF5F5F5)) BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null,
            content = {}
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player?.name ?: "???",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (score != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            score,
                            style = MaterialTheme.typography.labelSmall,
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
    currentResult: MatchResult?, onSelect: (MatchResult?) -> Unit, enableForfeitScoring: Boolean = true
) {
    var showExceptions by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(MatchResult.WHITE_WIN to "1–0", MatchResult.DRAW to "½–½", MatchResult.BLACK_WIN to "0–1").forEach { (result, notation) ->
                val description = when (result) {
                    MatchResult.WHITE_WIN -> "White wins"
                    MatchResult.BLACK_WIN -> "Black wins"
                    else -> "Draw"
                }
                FilterChip(selected = currentResult == result, onClick = { onSelect(result) },
                    label = { Text(notation) }, modifier = Modifier.weight(1f).semantics { contentDescription = description })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(when (currentResult) {
                MatchResult.WHITE_WIN -> "White wins"
                MatchResult.BLACK_WIN -> "Black wins"
                MatchResult.DRAW -> "Draw"
                MatchResult.FORFEIT_WHITE -> "Black wins by forfeit"
                MatchResult.FORFEIT_BLACK -> "White wins by forfeit"
                else -> "Result pending"
            }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { showExceptions = true }) { Text("More") }
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
    }
}
