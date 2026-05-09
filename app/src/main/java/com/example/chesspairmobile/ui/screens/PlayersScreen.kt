package com.example.chesspairmobile.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.PlayerEntity

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
    onClearPlayers: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Players (${players.size})") },
                actions = {
                    IconButton(onClick = { showBulkDialog = true }) {
                        Icon(Icons.Default.FileUpload, "Import CSV")
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.PersonAdd, "Add Player")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Quick add bar — type name, press Enter to add instantly
            QuickAddBar(onAdd = { name -> onAddPlayer(name, null, null, null) })

            if (players.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No players added yet", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(players) { player ->
                        PlayerCard(
                            player = player,
                            onRemove = { onRemovePlayer(player.id) },
                            onWithdraw = { onWithdrawPlayer(player.id) },
                            onRejoin = { onRejoinPlayer(player.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlayerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, rating, title, club ->
                onAddPlayer(name, rating, title, club)
                // Dialog stays open — caller can add more
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
            text = { Text("Are you sure you want to remove all players? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearPlayers()
                    showClearConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun QuickAddBar(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Type name and press Enter to add") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed)
                        name = ""
                    }
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotBlank()) {
                    onAdd(trimmed)
                    name = ""
                    focusRequester.requestFocus()
                }
            },
            enabled = name.isNotBlank()
        ) {
            Text("Add")
        }
    }
    HorizontalDivider()
}

@Composable
fun PlayerCard(
    player: PlayerEntity,
    onRemove: () -> Unit,
    onWithdraw: () -> Unit,
    onRejoin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (player.withdrawn) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("Withdrawn") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }
                }
                Row {
                    player.rating?.let { Text("Rating: $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(8.dp))
                    player.title?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.width(8.dp))
                    player.club?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (player.withdrawn) {
                TextButton(onClick = onRejoin) { Text("Rejoin") }
            } else {
                TextButton(onClick = onWithdraw) { Text("Withdraw") }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) {
                                onAdd(name.trim(), rating.toIntOrNull(), title.ifBlank { null }, club.ifBlank { null })
                                name = ""
                                focusRequester.requestFocus()
                            }
                        }
                    )
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
                        name = ""
                        focusRequester.requestFocus()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Add & Next") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun BulkImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var csv by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import CSV") },
        text = {
            Column {
                Text("Paste CSV data: Name,Rating,Title,Club (one per line)")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = csv,
                    onValueChange = { csv = it },
                    label = { Text("CSV Data") },
                    modifier = Modifier.height(200.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(csv) },
                enabled = csv.isNotBlank()
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
