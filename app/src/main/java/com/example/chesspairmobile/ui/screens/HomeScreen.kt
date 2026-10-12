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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.db.TournamentEntity
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.ui.components.TiebreakConfigPanel
import com.example.chesspairmobile.ui.components.StatusBadge
import com.example.chesspairmobile.BuildConfig
import com.example.chesspairmobile.logic.TestTournamentPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tournaments: List<TournamentEntity>,
    onSettings: () -> Unit = {},
    activeId: String?,
    onSelectTournament: (String) -> Unit,
    onCreateTournament: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>, AccelerationMode, Boolean) -> Unit,
    onDeleteTournament: (String) -> Unit,
    defaultPoints: Triple<Double, Double, Double> = Triple(1.0, 0.5, 0.0),
    defaultAvoidClubs: Boolean = false,
    defaultAcceleration: AccelerationMode = AccelerationMode.NONE,
    defaultTiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS
    ),
    onLoadTestTournament: ((TestTournamentPreset) -> Unit)? = null,
    isLoadingTestTournament: Boolean = false
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showTestPresets by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tournaments") }, actions = {
                if (BuildConfig.DEBUG && onLoadTestTournament != null) {
                    TextButton(onClick = { showTestPresets = true }, enabled = !isLoadingTestTournament) {
                        Text(if (isLoadingTestTournament) "Loading…" else "Load test tournament")
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            })
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, "Create Tournament")
                Spacer(Modifier.width(8.dp))
                Text("Create tournament")
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
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showCreateDialog = true }) { Text("Create tournament") }
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

    if (BuildConfig.DEBUG && showTestPresets && onLoadTestTournament != null) {
        AlertDialog(
            onDismissRequest = { showTestPresets = false },
            title = { Text("Load test tournament") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a preset to create a fresh tournament with sample players, ready to start.")
                    TestTournamentPreset.entries.forEach { preset ->
                        OutlinedButton(
                            onClick = { showTestPresets = false; onLoadTestTournament(preset) },
                            enabled = !isLoadingTestTournament,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(preset.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTestPresets = false }) { Text("Cancel") } }
        )
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
            onCreate = { name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode, swissExtra ->
                onCreateTournament(name, type, isTeam, rounds, tc, pts, noClub, autoCalc, manualOverride, section, tiebreaks, accelMode, swissExtra)
                showCreateDialog = false
            },
            defaultPoints = defaultPoints,
            defaultAvoidClubs = defaultAvoidClubs,
            defaultAcceleration = defaultAcceleration,
            defaultTiebreakOrder = defaultTiebreakOrder,
            defaultName = generateSequence(1) { it + 1 }
                .map { "Tournament $it" }
                .first { candidate ->
                    tournaments.none { it.name.trim().equals(candidate, ignoreCase = true) }
                }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TournamentTypeChip(tournament.type)
                    StatusBadge(tournament.status.lowercase().replaceFirstChar { it.uppercase() })
                    if (tournament.isTeamTournament) {
                        StatusBadge("Team")
                    }
                }
            }
            Box {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Actions for ${tournament.name}") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Delete tournament") }, onClick = { showMenu = false; onDelete() })
                }
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
    StatusBadge(label)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, TournamentType, Boolean, Int, String?, Triple<Double, Double, Double>, Boolean, Boolean, Boolean, String?, List<TiebreakType>, AccelerationMode, Boolean) -> Unit,
    defaultPoints: Triple<Double, Double, Double> = Triple(1.0, 0.5, 0.0),
    defaultAvoidClubs: Boolean = false,
    defaultAcceleration: AccelerationMode = AccelerationMode.NONE,
    defaultTiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS
    ),
    defaultName: String = "Tournament 1"
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var section by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TournamentType.SWISS) }
    var isTeam by rememberSaveable { mutableStateOf(false) }
    var rounds by rememberSaveable { mutableStateOf("") }
    var timeControl by rememberSaveable { mutableStateOf("") }
    var winPts by rememberSaveable { mutableStateOf(defaultPoints.first.toString()) }
    var drawPts by rememberSaveable { mutableStateOf(defaultPoints.second.toString()) }
    var lossPts by rememberSaveable { mutableStateOf(defaultPoints.third.toString()) }
    var avoidClubs by rememberSaveable { mutableStateOf(defaultAvoidClubs) }
    var autoCalc by rememberSaveable { mutableStateOf(true) }
    var swissExtraRound by rememberSaveable { mutableStateOf(true) }
    var accelMode by rememberSaveable { mutableStateOf(defaultAcceleration) }
    var tiebreakOrder by rememberSaveable { mutableStateOf(defaultTiebreakOrder) }
    var tiebreakCustomized by rememberSaveable { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val validScoring = listOf(winPts, drawPts, lossPts).all { value -> value.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true }
    val validRounds = type != TournamentType.SWISS || autoCalc || (rounds.toIntOrNull()?.let { it > 0 } == true)
    fun create() {
        if (name.isBlank() || !validScoring || !validRounds) return
        val automatic = type != TournamentType.SWISS || autoCalc
        onCreate(name.trim(), type, isTeam, if (automatic) 0 else rounds.toInt(), timeControl.ifBlank { null },
            Triple(winPts.toDouble(), drawPts.toDouble(), lossPts.toDouble()), avoidClubs, automatic, false,
            section.ifBlank { null }, tiebreakOrder, if (type == TournamentType.SWISS) accelMode else AccelerationMode.NONE, swissExtraRound)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(title = { Text("Create tournament") }, navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel creation") }
            }) },
            bottomBar = { Surface(shadowElevation = 4.dp) {
                Button(onClick = { create() }, enabled = name.isNotBlank() && validScoring && validRounds,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text("Create tournament") }
            } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tournament basics", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tournament name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = name.isBlank())
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                    fun label(value: TournamentType) = when (value) { TournamentType.SWISS -> "Swiss"; TournamentType.ROUND_ROBIN -> "Round robin"; TournamentType.KNOCKOUT -> "Knockout" }
                    OutlinedTextField(value = label(type), onValueChange = {}, readOnly = true, label = { Text("Format") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        TournamentType.entries.forEach { choice -> DropdownMenuItem(text = { Text(label(choice)) }, onClick = {
                            type = choice; expandedType = false
                            if (!tiebreakCustomized) tiebreakOrder = defaultTiebreakOrderForType(choice)
                        }) }
                    }
                }
                Text(when (type) { TournamentType.SWISS -> "Players meet opponents with similar scores across a set number of rounds."; TournamentType.ROUND_ROBIN -> "Every participant meets every other participant."; TournamentType.KNOCKOUT -> "Winners advance to the next round." }, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isTeam, onClick = { isTeam = false }, label = { Text("Individual") })
                    FilterChip(selected = isTeam, onClick = { isTeam = true }, label = { Text("Teams") })
                }
                if (type == TournamentType.SWISS) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = autoCalc, onCheckedChange = { autoCalc = it }); Text("Calculate rounds from roster") }
                    if (autoCalc) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = swissExtraRound, onCheckedChange = { swissExtraRound = it }); Text("Add one extra round") }
                    else OutlinedTextField(value = rounds, onValueChange = { rounds = it.filter(Char::isDigit) }, label = { Text("Number of rounds") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = !validRounds,
                        supportingText = { if (!validRounds) Text("Enter at least one round") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                } else Text("Rounds are calculated from the ${if (isTeam) "teams" else "players"} when you start.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = timeControl, onValueChange = { timeControl = it }, label = { Text("Time control (optional, e.g. 90+30)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced settings" else "Advanced settings") }
                if (advanced) {
                    Text("Scoring", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = winPts, onValueChange = { winPts = it }, label = { Text("Win") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = drawPts, onValueChange = { drawPts = it }, label = { Text("Draw") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = lossPts, onValueChange = { lossPts = it }, label = { Text("Loss") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    if (!validScoring) Text("Enter nonnegative numbers for all scores", color = MaterialTheme.colorScheme.error)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = avoidClubs, onCheckedChange = { avoidClubs = it }); Text("Avoid club pairings") }
                    if (type == TournamentType.SWISS && !isTeam) {
                        Text("Acceleration", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccelerationMode.entries.forEach { mode -> FilterChip(selected = accelMode == mode, onClick = { accelMode = mode }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                        }
                        Text("Baku and Lim change initial seeding for larger Swiss fields. Choose None for standard pairing order.", style = MaterialTheme.typography.bodyMedium)
                    }
                    TiebreakConfigPanel(tiebreakOrder, { tiebreakOrder = it; tiebreakCustomized = true }, type)
                }
                Text("${if (isTeam) "Team" else "Individual"} tournament · ${if (type != TournamentType.SWISS || autoCalc) "automatic rounds" else "$rounds rounds"} · win $winPts / draw $drawPts / loss $lossPts", style = MaterialTheme.typography.bodyMedium)
                if (!validScoring && !advanced) Text("Open Advanced settings to correct scoring", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
