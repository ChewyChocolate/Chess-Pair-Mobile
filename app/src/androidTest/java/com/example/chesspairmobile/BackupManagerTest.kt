package com.example.chesspairmobile

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chesspairmobile.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private fun tournament(id: String) = TournamentEntity(id, id, null, "SWISS", false, true, false, null, "SETUP", 0, 5, 1.0, 0.5, 0.0, false, "[]")

    private fun isolated(block: suspend (Context, AppDatabase, File) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getCacheDir() = directory
            override fun getFilesDir() = directory
        }
        val database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        try { block(context, database, directory) } finally { database.close(); directory.deleteRecursively() }
    }

    @Test fun invalidTextDoesNotReplaceCurrentData() = isolated { context, database, directory ->
        database.tournamentDao().insertTournament(tournament("keep"))
        val file = File(directory, "invalid.db").apply { writeText("This is not a database, despite being more than sixteen bytes.") }
        try { BackupManager.restore(context, Uri.fromFile(file), database); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("keep", database.tournamentDao().getAllTournaments().single().id)
    }

    @Test fun unrelatedSQLiteDatabaseDoesNotReplaceCurrentData() = isolated { context, database, directory ->
        database.tournamentDao().insertTournament(tournament("keep"))
        val file = File(directory, "unrelated.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL("CREATE TABLE unrelated (name TEXT)") }
        try { BackupManager.restore(context, Uri.fromFile(file), database); fail("Unrelated database accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("keep", database.tournamentDao().getAllTournaments().single().id)
    }

    @Test fun snapshotRestoresLatestWritesAndKeepsRecoveryCopy() = isolated { context, database, directory ->
        val dao = database.tournamentDao()
        dao.insertTournament(tournament("saved"))
        dao.insertPlayer(PlayerEntity("player", "saved", "Latest player", 1500, null, null, active = true, withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0))
        dao.insertTeam(TeamEntity("team", "saved", "Team", "[\"player\"]"))
        dao.insertMatch(MatchEntity("match", "saved", 1, "player", null, "BYE", 1, null, false, null))
        val file = File(directory, "saved.db")
        BackupManager.export(context, Uri.fromFile(file), database)
        dao.deleteAllMatches(); dao.deleteAllTeams(); dao.deleteAllPlayers(); dao.deleteAllTournaments()
        dao.insertTournament(tournament("previous"))
        BackupManager.restore(context, Uri.fromFile(file), database)
        assertEquals("saved", dao.getAllTournaments().single().id)
        assertEquals("Latest player", dao.getAllPlayers().single().name)
        assertEquals("team", dao.getAllTeams().single().id)
        assertEquals("match", dao.getAllMatches().single().id)
        val recovery = BackupManager.recoveryFile(context)
        assertTrue(recovery.exists())
        val copy = File(directory, "previous.db")
        BackupManager.exportRecovery(context, Uri.fromFile(copy))
        BackupManager.restore(context, Uri.fromFile(copy), database)
        assertEquals("previous", dao.getAllTournaments().single().id)
    }
}
