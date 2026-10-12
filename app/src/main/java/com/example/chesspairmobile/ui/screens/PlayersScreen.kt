package com.example.chesspairmobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.chesspairmobile.logic.parseByeRounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.PlayerEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    players: List<PlayerEntity>,
    isTeamTournament: Boolean = false,
    onShowTeams: () -> Unit = {},
    tournamentStatus: String,
    tournamentRound: Int,
    tournamentTotalRounds: Int = 0,
    onAddPlayer: (String, Int?, String?, String?) -> Unit,
    onBulkAdd: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onWithdrawPlayer: (String) -> Unit,
    onRejoinPlayer: (String) -> Unit,
    onClearPlayers: () -> Unit,
    onUpdatePlayer: (PlayerEntity) -> Unit = {}
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkDialog by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isSetup = tournamentStatus == "SETUP"
    val isActive = tournamentStatus == "ACTIVE"
    val canEdit = isSetup || (isActive && !isTeamTournament)

    val filteredPlayers = remember(players, searchQuery) {
        if (searchQuery.isBlank()) players
        else players.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.club?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.title?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, "Add Player")
                    Spacer(Modifier.width(8.dp))
                    Text("Add player")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isTeamTournament) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("Players") })
                    FilterChip(selected = false, onClick = onShowTeams, label = { Text("Teams") })
                }
            }
            // Title row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Players (${players.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isSetup) TextButton(onClick = { showBulkDialog = true }) {
                    Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
                if (isSetup && players.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, "Clear All")
                    }
                }
            }
            // Search/add bar
            if (players.size >= 8 || canEdit) {
                SearchAddBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
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
            if (!canEdit) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isActive) "Team rosters are locked after starting" else "Tournament is completed — roster is locked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Tap Add player to enter your roster", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            totalRounds = tournamentTotalRounds,
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
            existingNames = players.map { it.name },
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
fun SearchAddBar(searchQuery: String, onSearchChange: (String) -> Unit) {
    OutlinedTextField(value = searchQuery, onValueChange = onSearchChange, label = { Text("Search players") },
        placeholder = { Text("Name, club, or title") }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Clear, "Clear search") } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}
@Composable
fun PlayerCard(
    player: PlayerEntity, isSetup: Boolean, canEdit: Boolean = isSetup, totalRounds: Int = 0,
    onRemove: () -> Unit, onWithdraw: () -> Unit, onRejoin: () -> Unit, onUpdatePlayer: (PlayerEntity) -> Unit = {}
) {
    var showByeDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmAction by rememberSaveable { mutableStateOf<String?>(null) }
    val converters = remember { com.example.chesspairmobile.data.db.Converters() }
    val byeRounds = remember(player.requestedByes) { converters.toIntList(player.requestedByes) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(player.rating?.let { "Rating $it" }, player.title, player.club).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (player.withdrawn) Text("Withdrawn", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (byeRounds.isNotEmpty()) Text("Requested byes: ${byeRounds.joinToString()}", style = MaterialTheme.typography.bodyMedium)
            }
            if (canEdit) Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Actions for ${player.name}") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isSetup) {
                        DropdownMenuItem(text = { Text("Edit player") }, onClick = { showEditDialog = true; showMenu = false })
                        DropdownMenuItem(text = { Text("Requested byes") }, onClick = { showByeDialog = true; showMenu = false })
                    }
                    DropdownMenuItem(text = { Text(if (player.withdrawn) "Rejoin" else "Withdraw") }, onClick = {
                        showMenu = false
                        if (player.withdrawn) onRejoin() else confirmAction = "Withdraw"
                    })
                    if (isSetup) DropdownMenuItem(text = { Text("Remove player") }, onClick = { confirmAction = "Remove"; showMenu = false })
                }
            }
        }
    }
    confirmAction?.let { action ->
        AlertDialog(onDismissRequest = { confirmAction = null }, title = { Text("$action ${player.name}?") },
            text = { Text(if (action == "Withdraw") "They will be excluded from future individual pairings. Existing results stay recorded. You can rejoin them later." else "Remove this player from the roster and any team assignments?") },
            confirmButton = { TextButton(onClick = { if (action == "Withdraw") onWithdraw() else onRemove(); confirmAction = null }) { Text(action) } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Cancel") } })
    }
    if (showByeDialog) RequestedByeDialog(byeRounds, totalRounds, { showByeDialog = false }) {
        onUpdatePlayer(player.copy(requestedByes = converters.fromIntList(it))); showByeDialog = false
    }
    if (showEditDialog) EditPlayerDialog(player, { showEditDialog = false }) { onUpdatePlayer(it); showEditDialog = false }
}
@Composable
fun EditPlayerDialog(
    player: PlayerEntity,
    onDismiss: () -> Unit,
    onSave: (PlayerEntity) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(player.name) }
    var rating by rememberSaveable { mutableStateOf(player.rating?.toString() ?: "") }
    var title by rememberSaveable { mutableStateOf(player.title ?: "") }
    var club by rememberSaveable { mutableStateOf(player.club ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Player") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = rating,
                    onValueChange = { input -> rating = input.filter { it.isDigit() } },
                    label = { Text("Rating (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional, e.g. GM, IM)") }, singleLine = true)
                OutlinedTextField(value = club, onValueChange = { club = it }, label = { Text("Club (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(player.copy(
                            name = name.trim(),
                            rating = rating.toIntOrNull(),
                            title = title.ifBlank { null },
                            club = club.ifBlank { null }
                        ))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RequestedByeDialog(
    byeRounds: List<Int>,
    totalRounds: Int,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(byeRounds.joinToString(", ")) }
    val parsedByes = parseByeRounds(text, totalRounds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Requested Byes") },
        text = {
            Column {
                val hint = if (totalRounds > 0) "Enter rounds 1-$totalRounds separated by commas (e.g. 1, 3, 5)"
                    else "Enter round numbers separated by commas (e.g. 1, 3, 5)"
                Text(hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    isError = parsedByes == null,
                    supportingText = { if (parsedByes == null) Text(if (totalRounds > 0) "Use round numbers from 1 to $totalRounds" else "Use positive round numbers separated by commas") },
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. 2, 4") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsedByes != null, onClick = {
                parsedByes?.let(onSave)
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
    var name by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var club by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Player") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedTextField(
                    value = rating,
                    onValueChange = { input -> rating = input.filter { it.isDigit() } },
                    label = { Text("Rating (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional, e.g. GM, IM)") }, singleLine = true)
                OutlinedTextField(value = club, onValueChange = { club = it }, label = { Text("Club (optional)") }, singleLine = true)
                Text("Add & Next keeps rating, title, and club for the next player.", style = MaterialTheme.typography.bodyMedium)
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
fun BulkImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit, existingNames: List<String> = emptyList()) {
    var csv by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            try {
                csv = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val data = ByteArray(64 * 1024 + 1)
                    var count = 0
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        while (count < data.size) {
                            val read = input.read(data, count, data.size - count)
                            if (read < 0) break
                            count += read
                        }
                    }
                    require(count <= 64 * 1024) { "Choose a CSV smaller than 64 KB or import in batches" }
                    String(data, 0, count, Charsets.UTF_8)
                }
                fileError = null
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                fileError = error.message ?: "Cannot read file"
            }
        }
    }
    val preview = remember(csv, existingNames) { com.example.chesspairmobile.logic.previewPlayerImport(csv, existingNames) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Import players") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a CSV or paste rows: Name,Rating,Title,Club. Duplicate names are skipped.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { filePicker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }) { Text("Choose CSV file") }
            OutlinedTextField(value = csv, onValueChange = { if (it.length <= 64 * 1024) csv = it }, label = { Text("CSV data") }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 8)
            fileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("${preview.players.size} players ready · ${preview.duplicates} duplicates skipped", style = MaterialTheme.typography.bodyMedium)
            preview.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            preview.players.take(5).forEach { Text("${it.name}${it.rating?.let { rating -> " · $rating" } ?: ""}", style = MaterialTheme.typography.bodyMedium) }
            if (preview.players.size > 5) Text("…and ${preview.players.size - 5} more", style = MaterialTheme.typography.bodyMedium)
        }
    }, confirmButton = { TextButton(enabled = preview.players.isNotEmpty() && preview.errors.isEmpty(), onClick = { onImport(csv) }) { Text("Import ${preview.players.size} players") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
