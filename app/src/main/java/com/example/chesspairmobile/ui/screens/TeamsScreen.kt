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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    teams: List<TeamEntity>,
    players: List<PlayerEntity>,
    onAddTeam: (String) -> Unit,
    onAssignPlayer: (String, String) -> Unit,
    onRemoveFromTeam: (String, String) -> Unit = { _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val gson = remember { Gson() }
    val typeToken = remember { object : TypeToken<List<String>>() {}.type }

    val totalPlayers = players.count { it.active && !it.withdrawn }
    val assignedCount = teams.sumOf { team ->
        (try { gson.fromJson<List<String>>(team.playerIds, typeToken) ?: emptyList() } catch (_: Exception) { emptyList<String>() }).size
    }

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
        Column(modifier = Modifier.padding(padding)) {
                if (assignedCount < totalPlayers) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("$assignedCount of $totalPlayers players assigned to teams", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }

            if (teams.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Group, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("No teams created yet", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap + to create a team", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn {
                    items(teams) { team ->
                        TeamCard(
                            team = team,
                            players = players,
                            allTeams = teams,
                            onAssignPlayer = { playerId -> onAssignPlayer(team.id, playerId) },
                            onRemovePlayer = { playerId -> onRemoveFromTeam(team.id, playerId) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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
    allTeams: List<TeamEntity>,
    onAssignPlayer: (String) -> Unit,
    onRemovePlayer: (String) -> Unit
) {
    val gson = remember { Gson() }
    val teamPlayerIds: List<String> = remember(team) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(team.playerIds, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val assignedPlayers = players.filter { p -> teamPlayerIds.contains(p.id) }
    val allAssignedPlayerIds = remember(allTeams, players) {
        allTeams.flatMap { t ->
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(t.playerIds, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }.toSet()
    }
    val unassignedPlayers = players.filter { p ->
        p.active && !p.withdrawn && p.id !in allAssignedPlayerIds
    }

    var showAssignDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${teamPlayerIds.size} player${if (teamPlayerIds.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Board fill indicator
                val fillColor = when {
                    teamPlayerIds.isEmpty() -> MaterialTheme.colorScheme.outline
                    teamPlayerIds.size >= 4 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = fillColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "${teamPlayerIds.size}P",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = fillColor
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            assignedPlayers.forEachIndexed { index, player ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(player.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    player.rating?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onRemovePlayer(player.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, "Remove from team",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }

            if (unassignedPlayers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { showAssignDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Assign Player (${unassignedPlayers.size} available)")
                    }
                    DropdownMenu(
                        expanded = showAssignDropdown,
                        onDismissRequest = { showAssignDropdown = false }
                    ) {
                        unassignedPlayers.forEach { player ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(player.name)
                                        player.rating?.let {
                                            Spacer(Modifier.width(8.dp))
                                            Text("$it", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
