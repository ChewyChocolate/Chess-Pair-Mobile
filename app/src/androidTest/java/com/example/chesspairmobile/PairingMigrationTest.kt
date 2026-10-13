package com.example.chesspairmobile

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chesspairmobile.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PairingMigrationTest {
    @Test fun versionThreeRecordsSurvivePairingSettingsMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "pairing-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        // Exact v3 table layout, including a previously recorded pairing and TPN.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE tournaments (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, section TEXT, type TEXT NOT NULL, isTeamTournament INTEGER NOT NULL, autoCalculateRounds INTEGER NOT NULL, manualRoundOverride INTEGER NOT NULL, timeControl TEXT, status TEXT NOT NULL, currentRound INTEGER NOT NULL, totalRounds INTEGER NOT NULL, pointsForWin REAL NOT NULL, pointsForDraw REAL NOT NULL, pointsForLoss REAL NOT NULL, avoidClubPairings INTEGER NOT NULL, tiebreakOrder TEXT NOT NULL, accelerationMode TEXT NOT NULL, liveBroadcastUrl TEXT, forcedPairings TEXT NOT NULL, swissExtraRound INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE players (id TEXT NOT NULL PRIMARY KEY, tournamentId TEXT NOT NULL, name TEXT NOT NULL, rating INTEGER, title TEXT, club TEXT, pairingNumber INTEGER NOT NULL, active INTEGER NOT NULL, withdrawn INTEGER NOT NULL, requestedByes TEXT NOT NULL, penaltyPoints REAL NOT NULL)")
            db.execSQL("CREATE TABLE teams (id TEXT NOT NULL PRIMARY KEY, tournamentId TEXT NOT NULL, name TEXT NOT NULL, playerIds TEXT NOT NULL)")
            db.execSQL("CREATE TABLE matches (id TEXT NOT NULL PRIMARY KEY, tournamentId TEXT NOT NULL, round INTEGER NOT NULL, whiteId TEXT, blackId TEXT, result TEXT, boardNumber INTEGER, teamMatchId TEXT, isManual INTEGER NOT NULL, pgn TEXT)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, '6469feca01aa06ce696c50829de0258c')")
            db.execSQL("INSERT INTO tournaments VALUES ('t', 'Existing event', NULL, 'SWISS', 0, 0, 0, NULL, 'ACTIVE', 1, 5, 1, 0.5, 0, 0, '[]', 'NONE', NULL, '[]', 1)")
            db.execSQL("INSERT INTO players VALUES ('p', 't', 'Existing player', 1800, NULL, NULL, 7, 1, 0, '[]', 0)")
            db.execSQL("INSERT INTO teams VALUES ('team', 't', 'Existing team', '[\"p\"]')")
            db.execSQL("INSERT INTO matches VALUES ('m', 't', 1, 'p', NULL, 'BYE', 999, NULL, 0, NULL)")
            db.version = 3
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4).build()
        try {
            val dao = database.tournamentDao()
            assertEquals("W", dao.getAllTournaments().single().initialColour)
            assertEquals("ACTIVE", dao.getAllTournaments().single().status)
            assertEquals(0, dao.getAllTeams().single().pairingNumber)
            assertEquals(7, dao.getAllPlayers().single().pairingNumber)
            assertEquals("BYE", dao.getAllMatches().single().result)
            assertEquals(4, database.openHelper.readableDatabase.version)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
