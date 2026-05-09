package com.example.chesspairmobile.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.outlined.*
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
import com.example.chesspairmobile.data.db.MatchEntity
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.ui.theme.*

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
    var showSwapMode by remember { mutableStateOf(false) }
    var swapMatch1 by remember { mutableStateOf<MatchEntity?>(null) }
    var swapColor1 by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(tournament.currentRound) {
        selectedRound = tournament.currentRound.coerceAtLeast(1)
    }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (isActive) {
                        TextButton(
                            onClick = onCompleteRound,
                            enabled = currentRoundHasPairings && currentRoundResultsComplete
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Complete")
                        }
                    }
                    if (isActive || tournament.status == "COMPLETED") {
                        IconButton(onClick = onRollbackRound) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Rollback")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (isSetup) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PlayCircle,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onStartTournament,
                            enabled = players.count { it.active && !it.withdrawn } >= 2
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Tournament")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Add at least 2 active players to begin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Round selector chips
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val maxDisplayRound = maxOf(currentRound, 1)
                        for (r in 1..maxDisplayRound) {
                            val isCurrent = r == currentRound
                            FilterChip(
                                selected = selectedRound == r,
                                onClick = { selectedRound = r },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Round $r")
                                        if (isCurrent) {
                                            Spacer(Modifier.width(4.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(6.dp)
                                            ) {}
                                        }
                                    }
                                },
                                border = if (isCurrent && selectedRound != r) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else null
                            )
                        }
                    }
                }

                // Status row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Round $selectedRound of ${tournament.totalRounds}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.HourglassTop,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Results pending",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            !hasMatches -> {
                                Button(onClick = { onGeneratePairings(selectedRound) }) {
                                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Generate Pairings")
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WinGreen.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = WinGreen
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "All results in",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = WinGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Swap mode banner
                if (showSwapMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.SwapHoriz, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(6.dp))
                                Text("Tap two players to swap positions",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { showSwapMode = false; swapMatch1 = null; swapColor1 = null }) {
                                Text("Done")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Matches list
                if (roundMatches.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (selectedRound == currentRound && isActive) "Tap 'Generate Pairings' above"
                            else "No matches in this round",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                        onSwapPlayers(m1.id, c1, match.id, color)
                                        swapMatch1 = null
                                        swapColor1 = null
                                    }
                                }
                            )
                        }

                        if (!showSwapMode && selectedRound == currentRound && isActive) {
                            item {
                                TextButton(
                                    onClick = { showSwapMode = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Swap Players")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(
    match: MatchEntity,
    players: List<PlayerEntity>,
    onUpdateResult: (MatchResult?) -> Unit,
    isSwapMode: Boolean,
    isSwapSelected: Boolean,
    onSwapSelect: (Boolean) -> Unit
) {
    val whitePlayer = players.find { it.id == match.whiteId }
    val blackPlayer = players.find { it.id == match.blackId }
    val isBye = match.blackId == null
    val result = match.result?.let { try { MatchResult.valueOf(it) } catch (_: Exception) { null } }

    val borderColor by animateColorAsState(
        if (isSwapSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        label = "swap_border"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (isSwapSelected) BorderStroke(2.dp, borderColor) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Board number badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${match.boardNumber ?: "?"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Player names column
            Column(modifier = Modifier.weight(1f)) {
                // White player
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSwapMode) {
                        IconButton(
                            onClick = { onSwapSelect(true) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.CompareArrows, "Swap white", Modifier.size(16.dp),
                                tint = if (isSwapSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFFFFF).copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("♙", fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        whitePlayer?.name ?: "???",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    whitePlayer?.rating?.let { rating ->
                        Spacer(Modifier.width(6.dp))
                        RatingBadge(rating)
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (!isBye) {
                    // Black player
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSwapMode) {
                            IconButton(
                                onClick = { onSwapSelect(false) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.CompareArrows, "Swap black", Modifier.size(16.dp),
                                    tint = if (isSwapSelected) MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF424242),
                            modifier = Modifier.size(14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("♟", fontSize = 10.sp, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            blackPlayer?.name ?: "???",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        blackPlayer?.rating?.let { rating ->
                            Spacer(Modifier.width(6.dp))
                            RatingBadge(rating)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(if (isSwapMode) 36.dp else 0.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ByeBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                " BYE ",
                                style = MaterialTheme.typography.labelSmall,
                                color = ByeBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Result section
            if (!isSwapMode) {
                if (result != null) {
                    ResultBadge(result)
                } else if (isBye) {
                    // Already shown as BYE badge
                } else {
                    ResultSelector(currentResult = result, onSelect = onUpdateResult)
                }
            }
        }
    }
}

@Composable
fun ResultBadge(result: MatchResult) {
    val (label, bgColor, textColor) = when (result) {
        MatchResult.WHITE_WIN -> Triple("1–0", WinGreen.copy(alpha = 0.12f), WinGreen)
        MatchResult.BLACK_WIN -> Triple("0–1", LossRed.copy(alpha = 0.12f), LossRed)
        MatchResult.DRAW -> Triple("½–½", DrawOrange.copy(alpha = 0.12f), DrawOrange)
        MatchResult.BYE -> Triple("BYE", ByeBlue.copy(alpha = 0.12f), ByeBlue)
        MatchResult.FORFEIT_WHITE -> Triple("0–1F", ForfeitGray.copy(alpha = 0.12f), ForfeitGray)
        MatchResult.FORFEIT_BLACK -> Triple("1–0F", ForfeitGray.copy(alpha = 0.12f), ForfeitGray)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun RatingBadge(rating: Int) {
    val color = when {
        rating >= 2400 -> Color(0xFF8E24AA) // GM level
        rating >= 2200 -> Color(0xFF1565C0) // Master
        rating >= 2000 -> Color(0xFF2E7D32) // Expert
        rating >= 1800 -> Color(0xFFF57C00) // Advanced
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        "$rating",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        fontSize = 11.sp
    )
}

@Composable
fun ResultSelector(
    currentResult: MatchResult?,
    onSelect: (MatchResult?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                currentResult?.let { resultLabel(it) } ?: "—",
                style = MaterialTheme.typography.labelMedium
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultLabelChip("1–0", WinGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("White wins")
                }},
                onClick = { onSelect(MatchResult.WHITE_WIN); expanded = false }
            )
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultLabelChip("0–1", LossRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Black wins")
                }},
                onClick = { onSelect(MatchResult.BLACK_WIN); expanded = false }
            )
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultLabelChip("½–½", DrawOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Draw")
                }},
                onClick = { onSelect(MatchResult.DRAW); expanded = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultLabelChip("1–0F", ForfeitGray)
                    Spacer(Modifier.width(8.dp))
                    Text("Forfeit Black")
                }},
                onClick = { onSelect(MatchResult.FORFEIT_BLACK); expanded = false }
            )
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultLabelChip("0–1F", ForfeitGray)
                    Spacer(Modifier.width(8.dp))
                    Text("Forfeit White")
                }},
                onClick = { onSelect(MatchResult.FORFEIT_WHITE); expanded = false }
            )
            if (currentResult != null) {
                DropdownMenuItem(
                    text = { Text("Clear result", color = MaterialTheme.colorScheme.error) },
                    onClick = { onSelect(null); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ResultLabelChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun resultLabel(result: MatchResult): String = when (result) {
    MatchResult.WHITE_WIN -> "1–0"
    MatchResult.BLACK_WIN -> "0–1"
    MatchResult.DRAW -> "½–½"
    MatchResult.BYE -> "BYE"
    MatchResult.FORFEIT_WHITE -> "0–1F"
    MatchResult.FORFEIT_BLACK -> "1–0F"
}
