package com.example.chesspairmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.data.models.TournamentType
import com.example.chesspairmobile.data.models.defaultTiebreakOrderForType

data class TiebreakInfo(
    val type: TiebreakType,
    val name: String,
    val description: String
)

val ALL_TIEBREAKS = listOf(
    TiebreakInfo(TiebreakType.DIRECT_ENCOUNTER, "Direct Encounter", "Result between the tied players"),
    TiebreakInfo(TiebreakType.BUCHHOLZ, "Buchholz", "Sum of opponents' scores"),
    TiebreakInfo(TiebreakType.MEDIAN_BUCHHOLZ, "Median Buchholz", "Buchholz dropping highest & lowest opponent"),
    TiebreakInfo(TiebreakType.BUCHHOLZ_CUT1, "Buchholz Cut-1", "Buchholz dropping lowest opponent"),
    TiebreakInfo(TiebreakType.SONNEBORN_BERGER, "Sonneborn-Berger", "Sum of defeated opponents' scores + half of drawn"),
    TiebreakInfo(TiebreakType.MOST_WINS, "Most Wins", "Most game wins (excluding byes)"),
    TiebreakInfo(TiebreakType.PROGRESSIVE, "Progressive", "Sum of cumulative scores after each round")
)

@Composable
fun TiebreakConfigPanel(
    tiebreakOrder: List<TiebreakType>,
    onTiebreakOrderChanged: (List<TiebreakType>) -> Unit,
    tournamentType: TournamentType? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Tiebreak Settings (${tiebreakOrder.size})")
        }

        if (expanded) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Drag up/down to reorder. Tap to remove. Add from list below.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    // Currently configured tiebreaks
                    tiebreakOrder.forEachIndexed { index, tb ->
                        val info = ALL_TIEBREAKS.find { it.type == tb }
                        TiebreakRow(
                            label = info?.name ?: tb.name,
                            description = info?.description,
                            index = index,
                            count = tiebreakOrder.size,
                            onMoveUp = if (index > 0) {
                                {
                                    val new = tiebreakOrder.toMutableList()
                                    new[index] = new[index - 1].also { new[index - 1] = new[index] }
                                    onTiebreakOrderChanged(new)
                                }
                            } else null,
                            onMoveDown = if (index < tiebreakOrder.size - 1) {
                                {
                                    val new = tiebreakOrder.toMutableList()
                                    new[index] = new[index + 1].also { new[index + 1] = new[index] }
                                    onTiebreakOrderChanged(new)
                                }
                            } else null,
                            onRemove = {
                                val new = tiebreakOrder.toMutableList()
                                new.removeAt(index)
                                onTiebreakOrderChanged(new)
                            }
                        )
                    }

                    if (tiebreakOrder.isEmpty()) {
                        Text("No tiebreaks configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Add new tiebreaks from unselected ones
                    Text("Add tiebreak:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    val unselected = ALL_TIEBREAKS.filter { it.type !in tiebreakOrder }
                    if (unselected.isEmpty()) {
                        Text("All tiebreaks already selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            unselected.forEach { info ->
                                SuggestionChip(
                                    onClick = {
                                        onTiebreakOrderChanged(tiebreakOrder + info.type)
                                    },
                                    label = { Text(info.name) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            onTiebreakOrderChanged(
                                if (tournamentType != null) defaultTiebreakOrderForType(tournamentType)
                                else ALL_TIEBREAKS.map { it.type }
                            )
                        }) { Text("Reset to default") }
                    }
                }
            }
        }
    }
}

@Composable
fun TiebreakRow(
    label: String,
    description: String?,
    index: Int,
    count: Int,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Arrow buttons
            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move up", Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.size(32.dp))
            }
            if (onMoveDown != null) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move down", Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.size(32.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }
        }
    }
}
