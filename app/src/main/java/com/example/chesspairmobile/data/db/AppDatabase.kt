package com.example.chesspairmobile.data.db

import androidx.room.*
import com.example.chesspairmobile.data.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Type converters for Room
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String = gson.toJson(value ?: emptyList<Int>())

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromTournamentType(value: TournamentType): String = value.name
    @TypeConverter
    fun toTournamentType(value: String): TournamentType = try { TournamentType.valueOf(value) } catch (_: Exception) { TournamentType.SWISS }

    @TypeConverter
    fun fromTournamentStatus(value: TournamentStatus): String = value.name
    @TypeConverter
    fun toTournamentStatus(value: String): TournamentStatus = try { TournamentStatus.valueOf(value) } catch (_: Exception) { TournamentStatus.SETUP }

    @TypeConverter
    fun fromTiebreakList(value: List<TiebreakType>?): String = gson.toJson(value?.map { it.name } ?: emptyList<String>())
    @TypeConverter
    fun toTiebreakList(value: String): List<TiebreakType> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val names: List<String> = gson.fromJson(value, type) ?: return defaultTiebreaks
            names.mapNotNull { try { TiebreakType.valueOf(it) } catch (_: Exception) { null } }
                .ifEmpty { defaultTiebreaks }
        } catch (_: Exception) {
            defaultTiebreaks
        }
    }

    @TypeConverter
    fun fromMatchResult(value: MatchResult?): String? = value?.name
    @TypeConverter
    fun toMatchResult(value: String?): MatchResult? = try { value?.let { MatchResult.valueOf(it) } } catch (_: Exception) { null }

    @TypeConverter
    fun fromAccelerationMode(value: AccelerationMode): String = value.name
    @TypeConverter
    fun toAccelerationMode(value: String): AccelerationMode = try { AccelerationMode.valueOf(value) } catch (_: Exception) { AccelerationMode.NONE }

    companion object {
        private val defaultTiebreaks = listOf(
            TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
            TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING
        )
    }
}

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val name: String,
    val rating: Int?,
    val title: String?,
    val club: String?,
    val pairingNumber: Int = 0,
    val active: Boolean,
    val withdrawn: Boolean,
    val requestedByes: String,
    val penaltyPoints: Double
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val name: String,
    val playerIds: String // JSON list of String
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val round: Int,
    val whiteId: String?,
    val blackId: String?,
    val result: String?,
    val boardNumber: Int?,
    val teamMatchId: String?,
    val isManual: Boolean,
    val pgn: String?
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val section: String?,
    val type: String,
    val isTeamTournament: Boolean,
    val autoCalculateRounds: Boolean,
    val manualRoundOverride: Boolean,
    val timeControl: String?,
    val status: String,
    val currentRound: Int,
    val totalRounds: Int,
    val pointsForWin: Double,
    val pointsForDraw: Double,
    val pointsForLoss: Double,
    val avoidClubPairings: Boolean,
    val tiebreakOrder: String,
    val accelerationMode: String = "NONE",
    val liveBroadcastUrl: String? = null,
    val forcedPairings: String = "[]"
)

@Dao
interface TournamentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: TournamentEntity)

    @Update
    suspend fun updateTournament(tournament: TournamentEntity)

    @Delete
    suspend fun deleteTournament(tournament: TournamentEntity)

    @Query("SELECT * FROM tournaments")
    suspend fun getAllTournaments(): List<TournamentEntity>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournament(id: String): TournamentEntity?

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: String)

    // Players
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayer(id: String)

    @Query("DELETE FROM players WHERE tournamentId = :tournamentId")
    suspend fun deletePlayersByTournament(tournamentId: String)

    @Query("SELECT * FROM players WHERE tournamentId = :tournamentId")
    suspend fun getPlayersByTournament(tournamentId: String): List<PlayerEntity>

    // Teams
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity)

    @Update
    suspend fun updateTeam(team: TeamEntity)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteTeam(id: String)

    @Query("DELETE FROM teams WHERE tournamentId = :tournamentId")
    suspend fun deleteTeamsByTournament(tournamentId: String)

    @Query("SELECT * FROM teams WHERE tournamentId = :tournamentId")
    suspend fun getTeamsByTournament(tournamentId: String): List<TeamEntity>

    // Matches
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>)

    @Update
    suspend fun updateMatch(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatch(id: String)

    @Query("DELETE FROM matches WHERE tournamentId = :tournamentId")
    suspend fun deleteMatchesByTournament(tournamentId: String)

    @Query("DELETE FROM matches WHERE tournamentId = :tournamentId AND round = :round")
    suspend fun deleteMatchesByRound(tournamentId: String, round: Int)

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId")
    suspend fun getMatchesByTournament(tournamentId: String): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId AND round = :round")
    suspend fun getMatchesByRound(tournamentId: String, round: Int): List<MatchEntity>

    @Query("DELETE FROM matches WHERE tournamentId = :tournamentId AND (whiteId = :playerId OR blackId = :playerId)")
    suspend fun deleteMatchesByPlayer(tournamentId: String, playerId: String)
}

@Database(
    entities = [TournamentEntity::class, PlayerEntity::class, TeamEntity::class, MatchEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tournamentDao(): TournamentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chesspair-db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
