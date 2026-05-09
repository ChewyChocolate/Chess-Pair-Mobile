package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.MatchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundsScreen(
    tournament: TournamentEntity?,
    players: List<PlayerEntity>,
    matches: List<MatchEntity>,
    onStartTournament: () -> Unit,
    onGeneratePairings: (Int) -> Unit,
    onUpdateResult: (String, MatchResult?) -> Unit,
    onCompleteRound: () -> Unit,
    onRollbackRound: () -> Unit,
    onSwapPlayers: (String, Boolean, String, Boolean) -> Unit
) {
    if (tournament == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tournament selected")
        }
        return
    }

    var selectedRound by remember { mutableIntStateOf(tournament.currentRound.coerceAtLeast(1)) }
    LaunchedEffect(tournament.currentRound) {
        selectedRound = tournament.currentRound.coerceAtLeast(1)
    }

    var showSwapMode by remember { mutableStateOf(false) }
    var swapMatch1 by remember { mutableStateOf<MatchEntity?>(null) }
    var swapColor1 by remember { mutableStateOf<Boolean?>(null) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showRollbackConfirm by remember { mutableStateOf(false) }

    val isSetup = tournament.status == "SETUP"
    val isActive = tournament.status == "ACTIVE"
    val currentRound = tournament.currentRound

    val roundMatches = matches.filter { it.round == selectedRound }
    val currentRoundMatches = matches.filter { it.round == currentRound }
    val currentRoundHasPairings = currentRoundMatches.isNotEmpty()
    val currentRoundResultsComplete = currentRoundMatches
        .filter { it.blackId != null }
        .all { it.result != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rounds") },
                actions = {
                    if (isActive) {
                        TextButton(
                            onClick = {
                                if (currentRoundHasPairings && currentRoundResultsComplete) {
                                    showCompleteConfirm = true
                                }
                            },
                            enabled = currentRoundHasPairings && currentRoundResultsComplete
                        ) {
                            Text("Complete Round")
                        }
                    }
                    if (isActive || tournament.status == "COMPLETED") {
                        IconButton(
                            onClick = { showRollbackConfirm = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Filled.Undo, "Rollback")
                        }
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
            if (isSetup) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.GridView, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onStartTournament,
                            enabled = players.count { it.active && !it.withdrawn } >= 2
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Tournament")
                        }
                        Spacer(Modifier.height(8.dp))
                        if (players.count { it.active && !it.withdrawn } < 2) {
                            Text(
                                "Add at least 2 active players to begin",
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
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
                                    Button(onClick = { onGeneratePairings(selectedRound) }) {
                                        Text("Generate Pairings")
                                    }
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
                        } else if (selectedRound < currentRound) {
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
                } else if (selectedRound == currentRound && isActive && roundMatches.isNotEmpty() && roundMatches.all { it.result == null }) {
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

                LazyColumn {
                    items(roundMatches.sortedBy { it.boardNumber }) { match ->
                        MatchCard(
                            match = match,
                            players = players,
                            onUpdateResult = { result -> onUpdateResult(match.id, result) },
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
                            showResults = selectedRound <= currentRound
                        )
                    }
                }

                if (roundMatches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (selectedRound == currentRound && isActive) {
                                Text("Click 'Generate Pairings' to create round $selectedRound matches",
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
            title = { Text("Complete Round $currentRound?") },
            text = { Text("All results will be locked and standings updated. This moves to Round ${currentRound + 1}.") },
            confirmButton = {
                TextButton(onClick = {
                    onCompleteRound()
                    showCompleteConfirm = false
                }) { Text("Complete Round") }
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
}

@Composable
fun MatchCard(
    match: MatchEntity,
    players: List<PlayerEntity>,
    onUpdateResult: (MatchResult?) -> Unit,
    isSwapMode: Boolean,
    isSwapSelected: Boolean,
    onSwapSelect: (Boolean) -> Unit,
    showResults: Boolean
) {
    val whitePlayer = players.find { it.id == match.whiteId }
    val blackPlayer = players.find { it.id == match.blackId }
    val isBye = match.blackId == null

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
                            "${match.boardNumber ?: "?"}",
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
                        color = Color(0xFFF5F5F5)
                    )
                    if (!isBye) {
                        Spacer(Modifier.height(6.dp))
                        PlayerRow(
                            player = blackPlayer,
                            colorName = "Black",
                            isSwapMode = isSwapMode,
                            isSelected = isSwapSelected,
                            onSwapClick = { onSwapSelect(false) },
                            color = Color(0xFF2C2C2C)
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

                if (!isSwapMode && showResults) {
                    ResultSelector(
                        currentResult = match.result?.let { try { MatchResult.valueOf(it) } catch (_: Exception) { null } },
                        onSelect = onUpdateResult
                    )
                }
            }

            // Show result badge if set
            if (match.result != null && !isBye && !isSwapMode) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ResultBadge(match.result!!)
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
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Color indicator circle
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(18.dp),
            color = color,
            border = if (color == Color(0xFFF5F5F5)) BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null,
            content = {}
        )

        Spacer(Modifier.width(8.dp))

        Column {
            Text(
                player?.name ?: "???",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            player?.rating?.let {
                Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isSwapMode) {
            Spacer(Modifier.width(8.dp))
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

@Composable
fun ResultBadge(result: String) {
    val (label, color) = when (result) {
        "WHITE_WIN" -> "1-0" to Color(0xFF2E7D32)
        "BLACK_WIN" -> "0-1" to Color(0xFF2E7D32)
        "DRAW" -> "\u00BD-\u00BD" to Color(0xFFE65100)
        "FORFEIT_BLACK" -> "1-0F" to Color(0xFFC62828)
        "FORFEIT_WHITE" -> "0-1F" to Color(0xFFC62828)
        "BYE" -> "BYE" to MaterialTheme.colorScheme.primary
        else -> "" to Color.Gray
    }
    if (label.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color
        )
    }
}

@Composable
fun ResultSelector(
    currentResult: MatchResult?,
    onSelect: (MatchResult?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    when (currentResult) {
                        null -> "Set result"
                        MatchResult.WHITE_WIN -> "1-0"
                        MatchResult.BLACK_WIN -> "0-1"
                        MatchResult.DRAW -> "\u00BD-\u00BD"
                        MatchResult.BYE -> "BYE"
                        MatchResult.FORFEIT_WHITE -> "0-1F"
                        MatchResult.FORFEIT_BLACK -> "1-0F"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color(0xFF2E7D32)) },
                text = { Text("1-0 (White wins)") },
                onClick = { onSelect(MatchResult.WHITE_WIN); expanded = false }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color(0xFF2E7D32)) },
                text = { Text("0-1 (Black wins)") },
                onClick = { onSelect(MatchResult.BLACK_WIN); expanded = false }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Minimize, null, Modifier.size(18.dp), tint = Color(0xFFE65100)) },
                text = { Text("\u00BD-\u00BD (Draw)") },
                onClick = { onSelect(MatchResult.DRAW); expanded = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Flag, null, Modifier.size(18.dp), tint = Color(0xFFC62828)) },
                text = { Text("1-0 Forfeit (Black forfeits)") },
                onClick = { onSelect(MatchResult.FORFEIT_BLACK); expanded = false }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Flag, null, Modifier.size(18.dp), tint = Color(0xFFC62828)) },
                text = { Text("0-1 Forfeit (White forfeits)") },
                onClick = { onSelect(MatchResult.FORFEIT_WHITE); expanded = false }
            )
            if (currentResult != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) },
                    text = { Text("Clear result") },
                    onClick = { onSelect(null); expanded = false }
                )
            }
        }
    }
}
