package com.example.chesspairmobile.data.db

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Copy validated records in a transaction; never replace the open database file. */
object BackupManager {
    private val mutex = Mutex()
    fun recoveryFile(context: Context) = File(context.filesDir, "chesspair-before-restore.db")

    private data class Snapshot(
        val tournaments: List<TournamentEntity>, val players: List<PlayerEntity>,
        val teams: List<TeamEntity>, val matches: List<MatchEntity>
    )

    private suspend fun read(database: AppDatabase): Snapshot = database.withTransaction {
        val dao = database.tournamentDao()
        Snapshot(dao.getAllTournaments(), dao.getAllPlayers(), dao.getAllTeams(), dao.getAllMatches())
    }

    private suspend fun write(database: AppDatabase, snapshot: Snapshot) = database.withTransaction {
        val dao = database.tournamentDao()
        dao.deleteAllMatches()
        dao.deleteAllTeams()
        dao.deleteAllPlayers()
        dao.deleteAllTournaments()
        snapshot.tournaments.forEach { dao.insertTournament(it) }
        dao.insertPlayers(snapshot.players)
        snapshot.teams.forEach { dao.insertTeam(it) }
        dao.insertMatches(snapshot.matches)
    }

    private fun open(context: Context, file: File): AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    private suspend fun snapshotFile(context: Context, snapshot: Snapshot, file: File) {
        val database = open(context, file)
        try { write(database, snapshot) } finally { database.close() }
    }

    private fun validate(snapshot: Snapshot) {
        val tournamentIds = snapshot.tournaments.map { it.id }.toSet()
        val players = snapshot.players.associateBy { it.id }
        require(snapshot.players.all { it.tournamentId in tournamentIds } &&
            snapshot.teams.all { it.tournamentId in tournamentIds } &&
            snapshot.matches.all { it.tournamentId in tournamentIds }) { "Backup contains orphaned tournament records" }
        val converters = Converters()
        require(snapshot.teams.all { team -> converters.toStringList(team.playerIds).all { players[it]?.tournamentId == team.tournamentId } }) { "Backup contains missing team members" }
        require(snapshot.matches.all { match -> listOfNotNull(match.whiteId, match.blackId).all { players[it]?.tournamentId == match.tournamentId } }) { "Backup contains missing players" }
        require(snapshot.tournaments.all { it.status in listOf("SETUP", "ACTIVE", "COMPLETED") && it.initialColour in listOf("W", "B") && it.currentRound >= 0 && it.totalRounds > 0 && it.pointsForWin.isFinite() && it.pointsForDraw.isFinite() && it.pointsForLoss.isFinite() }) { "Backup contains invalid tournament settings" }
    }

    suspend fun export(context: Context, uri: Uri, database: AppDatabase = AppDatabase.getInstance(context)) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(context.cacheDir, "backup-${UUID.randomUUID()}.db")
            try {
                snapshotFile(context, read(database), file)
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot write to the selected file" }.use { output -> file.inputStream().use { it.copyTo(output) } }
            } finally { context.deleteDatabase(file.absolutePath) }
        }
    }

    suspend fun exportRecovery(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(recoveryFile(context).exists()) { "No previous data saved" }
            requireNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot write to the selected file" }.use { output -> recoveryFile(context).inputStream().use { it.copyTo(output) } }
        }
    }

    suspend fun restore(context: Context, uri: Uri, database: AppDatabase = AppDatabase.getInstance(context)) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val incoming = File(context.cacheDir, "restore-${UUID.randomUUID()}.db")
            val recoveryTemp = File(context.filesDir, "recovery-${UUID.randomUUID()}.db")
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read the selected file" }.use { input -> incoming.outputStream().use { input.copyTo(it) } }
                val header = ByteArray(16)
                incoming.inputStream().use { java.io.DataInputStream(it).readFully(header) }
                require(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))) { "Choose a valid ChessPair database backup" }
                android.database.sqlite.SQLiteDatabase.openDatabase(incoming.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                    require(sqlite.version in 2..4) { "This backup version is not supported" }
                    val tables = mutableSetOf<String>()
                    sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                        while (cursor.moveToNext()) tables.add(cursor.getString(0))
                    }
                    require(tables.containsAll(listOf("tournaments", "players", "teams", "matches", "room_master_table"))) { "Choose a ChessPair database backup" }
                }
                val imported = open(context, incoming)
                val snapshot = try {
                    imported.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                        require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Backup is damaged" }
                    }
                    read(imported).also { validate(it) }
                } finally { imported.close() }
                val live = database
                live.withTransaction {
                    snapshotFile(context, read(live), recoveryTemp)
                    Files.move(recoveryTemp.toPath(), recoveryFile(context).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    write(live, snapshot)
                }
            } finally {
                context.deleteDatabase(incoming.absolutePath)
                context.deleteDatabase(recoveryTemp.absolutePath)
            }
        }
    }
}
