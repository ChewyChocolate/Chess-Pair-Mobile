package com.example.chesspairmobile.ui.screens

import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.chesspairmobile.data.db.BackupManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.example.chesspairmobile.ui.components.LabeledCheckbox
import com.example.chesspairmobile.data.models.AccelerationMode
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.data.preferences.AppPreferences
import com.example.chesspairmobile.data.preferences.AppSettings
import com.example.chesspairmobile.data.preferences.ThemeMode
import com.example.chesspairmobile.ui.components.ALL_TIEBREAKS
import com.example.chesspairmobile.ui.components.TiebreakConfigPanel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit
) {
    val settings by preferences.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var busy by remember { mutableStateOf(false) }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPrevious by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            try {
                if (exportPrevious) BackupManager.exportRecovery(context, uri) else BackupManager.export(context, uri)
                snackbarHostState.showSnackbar("Backup saved")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                snackbarHostState.showSnackbar("Backup failed: ${error.message ?: "Cannot write file"}")
            } finally { busy = false }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        pendingRestore = uri?.toString()
    }
    pendingRestore?.let { selectedUri ->
        AlertDialog(onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all current tournaments, players, teams, and results. The backup will be validated first. Your current data will be saved as a recovery backup.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                scope.launch {
                    busy = true
                    try {
                        BackupManager.restore(context, selectedUri.toUri())
                        restoreMessage = "Backup restored. Previous data is saved; export it below if you need to recover it."
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        restoreMessage = "Restore failed. Current tournaments are unchanged. ${error.message ?: "Choose a valid ChessPair backup."}"
                    } finally { busy = false }
                }
            }) { Text("Replace and restore") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Appearance ----
            SettingsSection(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    ThemeMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = {
                                scope.launch {
                                    preferences.setThemeMode(mode)
                                    onThemeChanged()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }

            }

            // ---- Behavior ----
            SettingsSection(title = "Behavior") {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = settings.enableForfeitScoring, role = Role.Switch,
                    onValueChange = { enabled -> scope.launch { preferences.setEnableForfeitScoring(enabled) } }
                ), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable forfeit scoring", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Shows forfeit outcomes in the More menu when entering results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableForfeitScoring,
                        onCheckedChange = null
                    )
                }
            }

            // ---- Tournament Defaults ----
            SettingsSection(title = "Tournament Defaults") {
                Text("Scoring", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "These values pre-fill the Create Tournament dialog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                var winPts by rememberSaveable { mutableStateOf(settings.defaultWinPoints.toString()) }
                var drawPts by rememberSaveable { mutableStateOf(settings.defaultDrawPoints.toString()) }
                var lossPts by rememberSaveable { mutableStateOf(settings.defaultLossPoints.toString()) }
                var scoringSaved by remember { mutableStateOf(false) }
                var scoringDirty by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(settings.defaultWinPoints, settings.defaultDrawPoints, settings.defaultLossPoints) {
                    if (!scoringDirty) {
                        winPts = settings.defaultWinPoints.toString()
                        drawPts = settings.defaultDrawPoints.toString()
                        lossPts = settings.defaultLossPoints.toString()
                    }
                }
                val validScoring = listOf(winPts, drawPts, lossPts).all { value -> value.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("Win", winPts, { value: String -> winPts = value }), Triple("Draw", drawPts, { value: String -> drawPts = value }), Triple("Loss", lossPts, { value: String -> lossPts = value })).forEach { (label, value, update) ->
                        OutlinedTextField(value = value, onValueChange = { update(it); scoringSaved = false; scoringDirty = true }, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    }
                }
                if (!validScoring) Text("Enter nonnegative numbers for all scores", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = validScoring, onClick = {
                    scope.launch { preferences.setDefaultScoring(winPts.toDouble(), drawPts.toDouble(), lossPts.toDouble()); scoringSaved = true; scoringDirty = false }
                }) { Text(if (scoringSaved) "Scoring defaults saved" else "Save scoring defaults") }
                Spacer(Modifier.height(12.dp))

                LabeledCheckbox("Avoid club pairings by default", settings.defaultAvoidClubs) {
                    scope.launch { preferences.setDefaultAvoidClubs(it) }
                }

                Spacer(Modifier.height(8.dp))

                Text("Default acceleration", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow {
                    AccelerationMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = settings.defaultAcceleration == mode,
                            onClick = {
                                scope.launch { preferences.setDefaultAcceleration(mode) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(idx, AccelerationMode.entries.size),
                            label = { Text(when(mode) { AccelerationMode.NONE -> "None"; AccelerationMode.BAKU -> "Baku"; AccelerationMode.LIM -> "Lim" }) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Default tiebreak order", style = MaterialTheme.typography.bodyMedium)
                TiebreakConfigPanel(
                    tiebreakOrder = settings.defaultTiebreakOrder,
                    onTiebreakOrderChanged = { order ->
                        scope.launch { preferences.setDefaultTiebreakOrder(order) }
                    }
                )
            }

            // ---- Data ----
            SettingsSection(title = "Data") {
                Text("Backups include all tournaments. Restoring replaces current data and saves a recovery copy.", style = MaterialTheme.typography.bodyMedium)
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working on backup…") }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportPrevious = false; exportLauncher.launch("chesspair_backup.db") },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back up tournaments")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore backup")
                    }
                    restoreMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (BackupManager.recoveryFile(context).exists()) OutlinedButton(enabled = !busy, onClick = { exportPrevious = true; exportLauncher.launch("chesspair_previous_data.db") }, modifier = Modifier.fillMaxWidth()) { Text("Export data from before restore") }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
