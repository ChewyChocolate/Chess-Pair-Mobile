package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tournaments: List<TournamentEntity>,
    activeId: String?,
    onSelectTournament: (String) -> Unit,
    onCreateTournament: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>) -> Unit,
    onDeleteTournament: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ChessPair") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create Tournament")
            }
        }
    ) { padding ->
        if (tournaments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            onCreate = { name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks ->
                onCreateTournament(name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks)
                showCreateDialog = false
            }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = if (isActive) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tournament.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                tournament.section?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row {
                    Text(tournament.type, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(tournament.status, style = MaterialTheme.typography.bodySmall)
                    if (tournament.isTeamTournament) {
                        Spacer(Modifier.width(8.dp))
                        Text("Team", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TournamentType.SWISS) }
    var isTeam by remember { mutableStateOf(false) }
    var rounds by remember { mutableStateOf("5") }
    var timeControl by remember { mutableStateOf("") }
    var winPts by remember { mutableStateOf("1") }
    var drawPts by remember { mutableStateOf("0.5") }
    var lossPts by remember { mutableStateOf("0") }
    var avoidClubs by remember { mutableStateOf(false) }
    var autoCalc by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tournament") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tournament Name") }, singleLine = true)
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section (optional)") }, singleLine = true)

                // Tournament type dropdown
                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                    OutlinedTextField(
                        value = type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        TournamentType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { type = t; expandedType = false }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTeam, onCheckedChange = { isTeam = it })
                    Text("Team Tournament")
                }

                OutlinedTextField(value = rounds, onValueChange = { rounds = it }, label = { Text("Number of Rounds") }, singleLine = true)

                if (type == TournamentType.SWISS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoCalc, onCheckedChange = { autoCalc = it })
                        Text("Auto-calculate rounds")
                    }
                }

                OutlinedTextField(value = timeControl, onValueChange = { timeControl = it }, label = { Text("Time Control (optional)") }, singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = avoidClubs, onCheckedChange = { avoidClubs = it })
                    Text("Avoid club pairings")
                }

                Text("Points", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = winPts, onValueChange = { winPts = it }, label = { Text("Win") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = drawPts, onValueChange = { drawPts = it }, label = { Text("Draw") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = lossPts, onValueChange = { lossPts = it }, label = { Text("Loss") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name, type, isTeam,
                            rounds.toIntOrNull() ?: 5, timeControl.ifBlank { null },
                            Triple(winPts.toDoubleOrNull() ?: 1.0, drawPts.toDoubleOrNull() ?: 0.5, lossPts.toDoubleOrNull() ?: 0.0),
                            avoidClubs, autoCalc, false, section.ifBlank { null },
                            listOf(TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
                                TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING)
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
