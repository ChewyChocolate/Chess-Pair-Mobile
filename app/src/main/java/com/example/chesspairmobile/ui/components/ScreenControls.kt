package com.example.chesspairmobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterTabs(teamsSelected: Boolean, onSelectPlayers: () -> Unit, onSelectTeams: () -> Unit) {
    PrimaryTabRow(selectedTabIndex = if (teamsSelected) 1 else 0) {
        Tab(selected = !teamsSelected, onClick = onSelectPlayers, text = { Text("Players") })
        Tab(selected = teamsSelected, onClick = onSelectTeams, text = { Text("Teams") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewModeSelector(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(selected = selectedIndex == index, onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size), label = { Text(label) })
        }
    }
}

@Composable
fun RoundPicker(selectedRound: Int, availableRounds: Int, totalRounds: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Round $selectedRound / $totalRounds", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ExpandMore, "Choose round", Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (round in 1..availableRounds.coerceAtLeast(1)) {
                DropdownMenuItem(text = { Text("Round $round") }, onClick = { onSelect(round); expanded = false })
            }
        }
    }
}
