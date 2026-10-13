package com.example.chesspairmobile.ui.screens

import com.example.chesspairmobile.ui.components.RosterTabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    tournamentStatus: String = "SETUP",
    onShowPlayers: () -> Unit = {},
    onAddTeam: (String) -> Unit,
    onAssignPlayer: (String, String) -> Unit,
    onMovePlayer: (String, String, Int) -> Unit = { _, _, _ -> },
    onRemoveFromTeam: (String, String) -> Unit = { _, _ -> }
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val canEdit = tournamentStatus == "SETUP"
    val gson = remember { Gson() }
    val typeToken = remember { object : TypeToken<List<String>>() {}.type }

    val totalPlayers = players.count { it.active && !it.withdrawn }
    val assignedIds = teams.flatMap { team ->
        try { gson.fromJson<List<String>>(team.playerIds, typeToken) ?: emptyList() } catch (_: Exception) { emptyList<String>() }
    }.toSet()
    val assignedCount = players.count { it.active && !it.withdrawn && it.id in assignedIds }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (canEdit) ExtendedFloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.GroupAdd, "Add Team")
                Spacer(Modifier.width(8.dp))
                Text("Add team")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            RosterTabs(true, onShowPlayers, {})
            if (!canEdit) Text("Team rosters are locked after starting", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            // Title row
            Text(
                "Teams (${teams.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
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
                        Text("Use Add team to create a roster", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn {
                    items(teams) { team ->
                        TeamCard(
                            team = team,
                            players = players,
                            allTeams = teams,
                            canEdit = canEdit,
                            onMovePlayer = { playerId, offset -> onMovePlayer(team.id, playerId, offset) },
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
    canEdit: Boolean = true,
    onAssignPlayer: (String) -> Unit,
    onMovePlayer: (String, Int) -> Unit = { _, _ -> },
    onRemovePlayer: (String) -> Unit
) {
    val gson = remember { Gson() }
    val teamPlayerIds: List<String> = remember(team) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(team.playerIds, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val assignedPlayers = teamPlayerIds.mapNotNull { id -> players.find { it.id == id } }
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
    var assignSearch by remember { mutableStateOf("") }

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
                    Text(player.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    player.rating?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canEdit) Box {
                        var menu by remember(player.id) { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Board actions for ${player.name}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { onMovePlayer(player.id, -1); menu = false })
                            DropdownMenuItem(text = { Text("Move down") }, enabled = index < assignedPlayers.lastIndex, onClick = { onMovePlayer(player.id, 1); menu = false })
                            DropdownMenuItem(text = { Text("Remove from team") }, onClick = { onRemovePlayer(player.id); menu = false })
                        }
                    }
                }
            }

            if (canEdit && unassignedPlayers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(
                        onClick = { showAssignDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Assign Player (${unassignedPlayers.size} available)")
                    }
                }
            }
        }
    }
    if (showAssignDropdown) AlertDialog(onDismissRequest = { showAssignDropdown = false }, title = { Text("Assign player to ${team.name}") },
        text = { Column {
            OutlinedTextField(value = assignSearch, onValueChange = { assignSearch = it }, label = { Text("Search available players") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val available = unassignedPlayers.filter { it.name.contains(assignSearch, true) }
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(available, key = { it.id }) { player ->
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { onAssignPlayer(player.id); showAssignDropdown = false; assignSearch = "" }) {
                        Text("${player.name}${player.rating?.let { " · $it" } ?: ""}")
                    }
                }
                if (available.isEmpty()) item { Text("No players match", Modifier.padding(16.dp)) }
            }
        } }, confirmButton = { TextButton(onClick = { showAssignDropdown = false }) { Text("Done") } })
}

@Composable
fun AddTeamDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

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
