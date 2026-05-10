package com.example.chesspairmobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.PlayerEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    players: List<PlayerEntity>,
    tournamentStatus: String,
    tournamentRound: Int,
    onAddPlayer: (String, Int?, String?, String?) -> Unit,
    onBulkAdd: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onWithdrawPlayer: (String) -> Unit,
    onRejoinPlayer: (String) -> Unit,
    onClearPlayers: () -> Unit,
    onUpdatePlayer: (PlayerEntity) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val isSetup = tournamentStatus == "SETUP"
    val isActive = tournamentStatus == "ACTIVE"
    val canEdit = isSetup || isActive

    val filteredPlayers = remember(players, searchQuery) {
        if (searchQuery.isBlank()) players
        else players.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.club?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.title?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Players (${players.size})") },
                actions = {
                    TextButton(onClick = { showBulkDialog = true }) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import")
                    }
                    if (players.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, "Clear All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, "Add Player")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search/add bar
            if (players.size >= 8 || canEdit) {
                SearchAddBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onAdd = { name -> onAddPlayer(name, null, null, null) },
                    canAdd = canEdit,
                    showCount = players.size >= 8
                )
                if (searchQuery.isNotBlank() && players.size >= 8) {
                    Text(
                        "${filteredPlayers.size} of ${players.size} players",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
                HorizontalDivider()
            }
            if (!canEdit && players.size < 8) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Tournament is completed — roster is locked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }

            // Player list
            if (filteredPlayers.isEmpty() && searchQuery.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No players match \"$searchQuery\"", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (players.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("No players added yet", style = MaterialTheme.typography.bodyLarge)
                        if (canEdit) {
                            Spacer(Modifier.height(4.dp))
                            Text("Type a name above or tap + to add", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(filteredPlayers, key = { it.id }) { player ->
                        PlayerCard(
                            player = player,
                            isSetup = isSetup,
                            canEdit = canEdit,
                            onRemove = { onRemovePlayer(player.id) },
                            onWithdraw = { onWithdrawPlayer(player.id) },
                            onRejoin = { onRejoinPlayer(player.id) },
                            onUpdatePlayer = onUpdatePlayer
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlayerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, rating, title, club ->
                onAddPlayer(name, rating, title, club)
            }
        )
    }

    if (showBulkDialog) {
        BulkImportDialog(
            onDismiss = { showBulkDialog = false },
            onImport = { csv ->
                onBulkAdd(csv)
                showBulkDialog = false
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Players") },
            text = { Text("Are you sure you want to remove all ${players.size} players? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearPlayers()
                    showClearConfirm = false
                }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SearchAddBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    canAdd: Boolean,
    showCount: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    val isSearching = searchQuery.isNotBlank()

    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
        if (!canAdd) {
            // Read-only search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search players by name, club, or title", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (isSearching) {
                        IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            // Combined search + add bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search or type new name to add", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        val trimmed = searchQuery.trim()
                        if (trimmed.isNotBlank()) { onAdd(trimmed); onSearchChange(""); focusRequester.requestFocus() }
                    },
                    enabled = searchQuery.isNotBlank()
                ) { Text("Add") }
            }
        }
    }
}

@Composable
fun PlayerCard(
    player: PlayerEntity,
    isSetup: Boolean,
    canEdit: Boolean = isSetup,
    onRemove: () -> Unit,
    onWithdraw: () -> Unit,
    onRejoin: () -> Unit,
    onUpdatePlayer: (PlayerEntity) -> Unit = {}
) {
    var showByeDialog by remember { mutableStateOf(false) }
    val gson = remember { Gson() }

    val byeRounds: List<Int> = remember(player.requestedByes) {
        try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson(player.requestedByes, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (player.withdrawn) Modifier.alpha(0.55f) else Modifier)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        player.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (player.withdrawn) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (player.withdrawn) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                            Text("W/D", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    player.rating?.let { Text("Rating: $it", style = MaterialTheme.typography.bodySmall) }
                    player.title?.let {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                            Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    player.club?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                // Requested byes display
                if (byeRounds.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Bye rounds: ${byeRounds.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Action buttons
            if (isSetup) {
                IconButton(onClick = { showByeDialog = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.EventBusy, "Edit byes", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }
            if (canEdit) {
                if (player.withdrawn) {
                    TextButton(onClick = onRejoin) { Text("Rejoin") }
                } else {
                    TextButton(onClick = onWithdraw) { Text("Forfeit") }
                }
                Spacer(Modifier.width(4.dp))
                if (isSetup) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    if (showByeDialog) {
        RequestedByeDialog(
            byeRounds = byeRounds,
            totalRounds = 99,
            onDismiss = { showByeDialog = false },
            onSave = { newByes ->
                onUpdatePlayer(player.copy(requestedByes = gson.toJson(newByes)))
                showByeDialog = false
            }
        )
    }
}

@Composable
fun RequestedByeDialog(
    byeRounds: List<Int>,
    totalRounds: Int,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    var text by remember { mutableStateOf(byeRounds.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Requested Byes") },
        text = {
            Column {
                Text("Enter round numbers separated by commas (e.g. 1, 3, 5)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. 2, 4") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = text.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                onSave(parsed)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) {
                            onAdd(name.trim(), rating.toIntOrNull(), title.ifBlank { null }, club.ifBlank { null })
                            name = ""; focusRequester.requestFocus()
                        }
                    })
                )
                OutlinedTextField(value = rating, onValueChange = { rating = it }, label = { Text("Rating (optional)") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional, e.g. GM, IM)") }, singleLine = true)
                OutlinedTextField(value = club, onValueChange = { club = it }, label = { Text("Club (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(name.trim(), rating.toIntOrNull(), title.ifBlank { null }, club.ifBlank { null })
                        name = ""; focusRequester.requestFocus()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add & Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun BulkImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var csv by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Players") },
        text = {
            Column {
                Text("Paste CSV data: Name,Rating,Title,Club", style = MaterialTheme.typography.bodySmall)
                Text("One player per line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = csv, onValueChange = { csv = it },
                    placeholder = { Text("Magnus Carlsen,2830,GM,Norway\nHikaru Nakamura,2790,GM,USA") },
                    modifier = Modifier.height(200.dp), maxLines = 10
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(csv) }, enabled = csv.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
