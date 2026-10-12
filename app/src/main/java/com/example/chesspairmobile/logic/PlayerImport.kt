package com.example.chesspairmobile.logic

data class ImportedPlayer(val name: String, val rating: Int?, val title: String?, val club: String?)
data class PlayerImportPreview(val players: List<ImportedPlayer>, val errors: List<String>, val duplicates: Int)

fun previewPlayerImport(csv: String, existingNames: List<String>): PlayerImportPreview {
    val players = mutableListOf<ImportedPlayer>()
    val errors = mutableListOf<String>()
    val names = existingNames.map { it.trim().lowercase(java.util.Locale.ROOT) }.toMutableSet()
    var duplicates = 0
    csv.removePrefix("\uFEFF").lines().forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var offset = 0
        while (offset < line.length) {
            val char = line[offset]
            when {
                char == '"' && quoted && offset + 1 < line.length && line[offset + 1] == '"' -> { field.append('"'); offset++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { fields.add(field.toString().trim()); field.clear() }
                else -> field.append(char)
            }
            offset++
        }
        fields.add(field.toString().trim())
        if (index == 0 && fields.map { it.lowercase(java.util.Locale.ROOT) } == listOf("name", "rating", "title", "club")) return@forEachIndexed
        if (quoted || fields.size !in 1..4 || fields[0].isBlank()) {
            errors.add("Row ${index + 1}: use Name,Rating,Title,Club with balanced quotes")
            return@forEachIndexed
        }
        val ratingText = fields.getOrNull(1).orEmpty()
        val rating = ratingText.toIntOrNull()
        if (ratingText.isNotBlank() && (rating == null || rating < 0)) {
            errors.add("Row ${index + 1}: rating must be a nonnegative whole number")
            return@forEachIndexed
        }
        if (!names.add(fields[0].lowercase(java.util.Locale.ROOT))) { duplicates++; return@forEachIndexed }
        players.add(ImportedPlayer(fields[0], rating, fields.getOrNull(2)?.ifBlank { null }, fields.getOrNull(3)?.ifBlank { null }))
    }
    return PlayerImportPreview(players, errors, duplicates)
}
