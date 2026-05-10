package com.example.chesspairmobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // DB export
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val dbFile = context.getDatabasePath("chesspair-db")
                if (dbFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        dbFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                }
            }
        }
    }

    // DB import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val dbFile = context.getDatabasePath("chesspair-db")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    dbFile.outputStream().use { out -> inp.copyTo(out) }
                }
            }
        }
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
        }
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

                Spacer(Modifier.height(16.dp))

                Text("TV display cycle: ${settings.tvCycleSeconds}s", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.tvCycleSeconds.toFloat(),
                    onValueChange = {
                        scope.launch { preferences.setTvCycleSeconds(it.toInt()) }
                    },
                    valueRange = 5f..30f,
                    steps = 24, // step by 1
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("5s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("30s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Behavior ----
            SettingsSection(title = "Behavior") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable forfeit scoring", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Shows forfeit result buttons (1-0F, 0-1F) when entering match results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableForfeitScoring,
                        onCheckedChange = {
                            scope.launch { preferences.setEnableForfeitScoring(it) }
                        }
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var winPts by remember(settings) { mutableStateOf(settings.defaultWinPoints.toString()) }
                    var drawPts by remember(settings) { mutableStateOf(settings.defaultDrawPoints.toString()) }
                    var lossPts by remember(settings) { mutableStateOf(settings.defaultLossPoints.toString()) }

                    OutlinedTextField(
                        value = winPts,
                        onValueChange = {
                            winPts = it
                            it.toDoubleOrNull()?.let { v ->
                                scope.launch { preferences.setDefaultScoring(v, settings.defaultDrawPoints, settings.defaultLossPoints) }
                            }
                        },
                        label = { Text("Win") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = drawPts,
                        onValueChange = {
                            drawPts = it
                            it.toDoubleOrNull()?.let { v ->
                                scope.launch { preferences.setDefaultScoring(settings.defaultWinPoints, v, settings.defaultLossPoints) }
                            }
                        },
                        label = { Text("Draw") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lossPts,
                        onValueChange = {
                            lossPts = it
                            it.toDoubleOrNull()?.let { v ->
                                scope.launch { preferences.setDefaultScoring(settings.defaultWinPoints, settings.defaultDrawPoints, v) }
                            }
                        },
                        label = { Text("Loss") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.defaultAvoidClubs,
                        onCheckedChange = {
                            scope.launch { preferences.setDefaultAvoidClubs(it) }
                        }
                    )
                    Text("Avoid club pairings by default")
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("chesspair_backup.db") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export DB")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import DB")
                    }
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
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            content()
        }
    }
}
