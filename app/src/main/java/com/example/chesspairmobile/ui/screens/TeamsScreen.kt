package com.example.chesspairmobile.ui.screens

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
import com.example.chesspairmobile.data.db.PlayerEntity
import com.example.chesspairmobile.data.db.TeamEntity
import com.example.chesspairmobile.data.models.Team
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    teams: List<TeamEntity>,
    players: List<PlayerEntity>,
    onAddTeam: (String) -> Unit,
    onAssignPlayer: (String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Teams (${teams.size})") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.GroupAdd, "Add Team")
            }
        }
    ) { padding ->
        if (teams.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No teams created yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(teams) { team ->
                    TeamCard(
                        team = team,
                        players = players,
                        onAssignPlayer = { playerId -> onAssignPlayer(team.id, playerId) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTeamDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name ->
                onAddTeam(name)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TeamCard(
    team: TeamEntity,
    players: List<PlayerEntity>,
    onAssignPlayer: (String) -> Unit
) {
    val gson = remember { Gson() }
    val teamPlayerIds: List<String> = remember(team) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(team.playerIds, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val assignedPlayers = players.filter { p -> teamPlayerIds.contains(p.id) }
    val unassignedPlayers = players.filter { p -> p.active && !p.withdrawn && !teamPlayerIds.contains(p.id) }

    var showAssignDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            assignedPlayers.forEachIndexed { index, player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Board ${index + 1}: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(player.name, style = MaterialTheme.typography.bodyMedium)
                    player.rating?.let {
                        Text(" ($it)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (unassignedPlayers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { showAssignDropdown = true }) {
                        Text("Assign player")
                    }
                    DropdownMenu(
                        expanded = showAssignDropdown,
                        onDismissRequest = { showAssignDropdown = false }
                    ) {
                        unassignedPlayers.forEach { player ->
                            DropdownMenuItem(
                                text = {
                                    Text("${player.name}${player.rating?.let { " ($it)" } ?: ""}")
                                },
                                onClick = {
                                    onAssignPlayer(player.id)
                                    showAssignDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTeamDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Team Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
