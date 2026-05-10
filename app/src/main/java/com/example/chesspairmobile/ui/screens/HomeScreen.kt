package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.ui.components.TiebreakConfigPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tournaments: List<TournamentEntity>,
    activeId: String?,
    onSelectTournament: (String) -> Unit,
    onCreateTournament: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>, AccelerationMode) -> Unit,
    onDeleteTournament: (String) -> Unit,
    defaultPoints: Triple<Double, Double, Double> = Triple(1.0, 0.5, 0.0),
    defaultAvoidClubs: Boolean = false,
    defaultAcceleration: AccelerationMode = AccelerationMode.NONE,
    defaultTiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING
    )
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ChessPair") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, "Create Tournament")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (tournaments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No tournaments yet", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Create one to get started", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(tournaments) { tournament ->
                    TournamentCard(
                        tournament = tournament,
                        isActive = tournament.id == activeId,
                        onClick = { onSelectTournament(tournament.id) },
                        onDelete = { deleteConfirmId = tournament.id }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete Tournament") },
            text = { Text("Are you sure you want to delete this tournament and all its data?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTournament(id)
                    deleteConfirmId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateDialog) {
        CreateTournamentDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode ->
                onCreateTournament(name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode)
                showCreateDialog = false
            },
            defaultPoints = defaultPoints,
            defaultAvoidClubs = defaultAvoidClubs,
            defaultAcceleration = defaultAcceleration,
            defaultTiebreakOrder = defaultTiebreakOrder
        )
    }
}

@Composable
fun TournamentCard(
    tournament: TournamentEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (tournament.status) {
        "SETUP" -> MaterialTheme.colorScheme.outline
        "ACTIVE" -> MaterialTheme.colorScheme.tertiary
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tournament.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                tournament.section?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TournamentTypeChip(tournament.type)
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tournament.status.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = statusColor.copy(alpha = 0.12f),
                            labelColor = statusColor
                        )
                    )
                    if (tournament.isTeamTournament) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Team") },
                            icon = { Icon(Icons.Default.Group, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun TournamentTypeChip(type: String) {
    val (label, icon) = when (type) {
        "SWISS" -> "Swiss" to Icons.Default.GridView
        "ROUND_ROBIN" -> "Round Robin" to Icons.Default.Loop
        "KNOCKOUT" -> "Knockout" to Icons.Default.EmojiEvents
        else -> type to Icons.Default.Info
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        icon = { Icon(icon, null, Modifier.size(16.dp)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>, AccelerationMode) -> Unit,
    defaultPoints: Triple<Double, Double, Double> = Triple(1.0, 0.5, 0.0),
    defaultAvoidClubs: Boolean = false,
    defaultAcceleration: AccelerationMode = AccelerationMode.NONE,
    defaultTiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING
    )
) {
    var name by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TournamentType.SWISS) }
    var isTeam by remember { mutableStateOf(false) }
    var rounds by remember { mutableStateOf("5") }
    var timeControl by remember { mutableStateOf("") }
    var winPts by remember { mutableStateOf(defaultPoints.first.toString()) }
    var drawPts by remember { mutableStateOf(defaultPoints.second.toString()) }
    var lossPts by remember { mutableStateOf(defaultPoints.third.toString()) }
    var avoidClubs by remember { mutableStateOf(defaultAvoidClubs) }
    var autoCalc by remember { mutableStateOf(false) }
    var accelMode by remember { mutableStateOf(defaultAcceleration) }
    var tiebreakOrder by remember { mutableStateOf(defaultTiebreakOrder) }
    var expandedType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tournament") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tournament Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                    OutlinedTextField(
                        value = when(type) { TournamentType.SWISS -> "Swiss"; TournamentType.ROUND_ROBIN -> "Round Robin"; TournamentType.KNOCKOUT -> "Knockout" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        TournamentType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(when(t) { TournamentType.SWISS -> "Swiss"; TournamentType.ROUND_ROBIN -> "Round Robin"; TournamentType.KNOCKOUT -> "Knockout" }) },
                                onClick = { type = t; expandedType = false }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTeam, onCheckedChange = { isTeam = it })
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Group, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Team Tournament")
                }

                OutlinedTextField(value = rounds, onValueChange = { rounds = it }, label = { Text("Number of Rounds") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                if (type == TournamentType.SWISS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoCalc, onCheckedChange = { autoCalc = it })
                        Text("Auto-calculate rounds")
                    }
                }

                OutlinedTextField(value = timeControl, onValueChange = { timeControl = it }, label = { Text("Time Control (optional, e.g. 90+30)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = avoidClubs, onCheckedChange = { avoidClubs = it })
                    Text("Avoid club pairings")
                }

                if (type == TournamentType.SWISS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accelMode != AccelerationMode.NONE, onCheckedChange = { accelMode = if (it) AccelerationMode.BAKU else AccelerationMode.NONE })
                        Text("Accelerated pairings (Baku)")
                    }
                }

                Text("Scoring", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = winPts, onValueChange = { winPts = it }, label = { Text("Win") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = drawPts, onValueChange = { drawPts = it }, label = { Text("Draw") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = lossPts, onValueChange = { lossPts = it }, label = { Text("Loss") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TiebreakConfigPanel(
                    tiebreakOrder = tiebreakOrder,
                    onTiebreakOrderChanged = { tiebreakOrder = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name, type, isTeam,
                            (rounds.toIntOrNull() ?: 5).coerceAtLeast(1), timeControl.ifBlank { null },
                            Triple(winPts.toDoubleOrNull() ?: 1.0, drawPts.toDoubleOrNull() ?: 0.5, lossPts.toDoubleOrNull() ?: 0.0),
                            avoidClubs, autoCalc, false, section.ifBlank { null },
                            tiebreakOrder,
                            accelMode
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
