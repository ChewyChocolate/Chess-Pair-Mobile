package com.example.chesspairmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.logic.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

class TournamentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.tournamentDao()
    private val converters = Converters()

    private val _tournaments = MutableStateFlow<List<TournamentEntity>>(emptyList())
    val tournaments: StateFlow<List<TournamentEntity>> = _tournaments.asStateFlow()

    private val _activeTournamentId = MutableStateFlow<String?>(null)
    val activeTournamentId: StateFlow<String?> = _activeTournamentId.asStateFlow()

    private val _players = MutableStateFlow<List<PlayerEntity>>(emptyList())
    val players: StateFlow<List<PlayerEntity>> = _players.asStateFlow()

    private val _teams = MutableStateFlow<List<TeamEntity>>(emptyList())
    val teams: StateFlow<List<TeamEntity>> = _teams.asStateFlow()

    private val _matches = MutableStateFlow<List<MatchEntity>>(emptyList())
    val matches: StateFlow<List<MatchEntity>> = _matches.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            _tournaments.value = dao.getAllTournaments()
        }
    }

    fun setActiveTournament(id: String?) {
        loadJob?.cancel()
        _activeTournamentId.value = id
        if (id != null) {
            loadJob = viewModelScope.launch {
                _players.value = dao.getPlayersByTournament(id)
                _teams.value = dao.getTeamsByTournament(id)
                _matches.value = dao.getMatchesByTournament(id)
            }
        } else {
            _players.value = emptyList()
            _teams.value = emptyList()
            _matches.value = emptyList()
        }
    }

    fun getActiveTournament(): TournamentEntity? {
        val id = _activeTournamentId.value ?: return null
        return _tournaments.value.find { it.id == id }
    }

    private val activeTournamentOrNull: TournamentEntity?
        get() {
            val id = _activeTournamentId.value ?: return null
            return _tournaments.value.find { it.id == id }
        }

    private fun requireStatus(vararg allowed: TournamentStatus): Boolean {
        val t = activeTournamentOrNull ?: return false
        val status = try { TournamentStatus.valueOf(t.status) } catch (_: Exception) { return false }
        return status in allowed
    }

    fun createTournament(
        name: String,
        type: TournamentType,
        isTeamTournament: Boolean,
        totalRounds: Int,
        timeControl: String? = null,
        points: Triple<Double, Double, Double> = Triple(1.0, 0.5, 0.0),
        avoidClubs: Boolean = false,
        autoCalcRounds: Boolean = false,
        manualOverride: Boolean = false,
        section: String? = null,
        tiebreakOrder: List<TiebreakType> = listOf(
            TiebreakType.DIRECT_ENCOUNTER, TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ,
            TiebreakType.SONNEBORN_BERGER, TiebreakType.MOST_WINS, TiebreakType.RATING
        )
    ) {
        val rounds = totalRounds.coerceAtLeast(1)
        val t = TournamentEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            section = section,
            type = type.name,
            isTeamTournament = isTeamTournament,
            autoCalculateRounds = autoCalcRounds,
            manualRoundOverride = manualOverride,
            timeControl = timeControl,
            status = TournamentStatus.SETUP.name,
            currentRound = 0,
            totalRounds = rounds,
            pointsForWin = points.first,
            pointsForDraw = points.second,
            pointsForLoss = points.third,
            avoidClubPairings = avoidClubs,
            tiebreakOrder = converters.fromTiebreakList(tiebreakOrder)
        )
        viewModelScope.launch {
            dao.insertTournament(t)
            _tournaments.value = dao.getAllTournaments()
            _activeTournamentId.value = t.id
            _players.value = emptyList()
            _teams.value = emptyList()
            _matches.value = emptyList()
        }
    }

    fun updateTournament(updates: TournamentEntity) {
        viewModelScope.launch {
            dao.updateTournament(updates)
            _tournaments.value = dao.getAllTournaments()
        }
    }

    fun deleteTournament(id: String) {
        viewModelScope.launch {
            dao.deletePlayersByTournament(id)
            dao.deleteTeamsByTournament(id)
            dao.deleteMatchesByTournament(id)
            dao.deleteTournamentById(id)
            _tournaments.value = dao.getAllTournaments()
            if (_activeTournamentId.value == id) {
                _activeTournamentId.value = null
                _players.value = emptyList()
                _teams.value = emptyList()
                _matches.value = emptyList()
            }
        }
    }

    fun getTournamentPlayers(): List<Player> {
        return _players.value.map { entityToPlayer(it) }
    }

    fun getTournamentTeams(): List<Team> {
        return _teams.value.map { entityToTeam(it) }
    }

    fun getTournamentMatches(): List<Match> {
        return _matches.value.map { entityToMatch(it) }
    }

    // ---- Player management (SETUP only) ----

    fun addPlayer(name: String, rating: Int? = null, title: String? = null, club: String? = null) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        val p = PlayerEntity(
            id = UUID.randomUUID().toString(),
            tournamentId = tid,
            name = name,
            rating = rating,
            title = title,
            club = club,
            active = true,
            withdrawn = false,
            requestedByes = "[]",
            penaltyPoints = 0.0
        )
        viewModelScope.launch {
            dao.insertPlayer(p)
            _players.value = dao.getPlayersByTournament(tid)
        }
    }

    fun bulkAddPlayers(csvData: String) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        val newPlayers = csvData.lines()
            .mapNotNull { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.isEmpty() || parts[0].isBlank()) return@mapNotNull null
                PlayerEntity(
                    id = UUID.randomUUID().toString(),
                    tournamentId = tid,
                    name = parts[0],
                    rating = parts.getOrNull(1)?.toIntOrNull(),
                    title = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                    club = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    active = true,
                    withdrawn = false,
                    requestedByes = "[]",
                    penaltyPoints = 0.0
                )
            }
        if (newPlayers.isNotEmpty()) {
            viewModelScope.launch {
                dao.insertPlayers(newPlayers)
                _players.value = dao.getPlayersByTournament(tid)
            }
        }
    }

    fun updatePlayer(player: PlayerEntity) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updatePlayer(player)
            _players.value = dao.getPlayersByTournament(tid)
        }
    }

    fun removePlayer(id: String) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.deletePlayer(id)
            dao.deleteMatchesByPlayer(tid, id)
            _players.value = dao.getPlayersByTournament(tid)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun clearPlayers() {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.deletePlayersByTournament(tid)
            dao.deleteMatchesByTournament(tid)
            _players.value = emptyList()
            _matches.value = emptyList()
        }
    }

    fun withdrawPlayer(id: String) {
        val player = _players.value.find { it.id == id } ?: return
        updatePlayer(player.copy(withdrawn = true))
    }

    fun rejoinPlayer(id: String) {
        val player = _players.value.find { it.id == id } ?: return
        updatePlayer(player.copy(withdrawn = false))
    }

    // ---- Team management ----

    fun addTeam(name: String) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        val team = TeamEntity(
            id = UUID.randomUUID().toString(),
            tournamentId = tid,
            name = name,
            playerIds = "[]"
        )
        viewModelScope.launch {
            dao.insertTeam(team)
            _teams.value = dao.getTeamsByTournament(tid)
        }
    }

    fun assignPlayerToTeam(teamId: String, playerId: String) {
        val team = _teams.value.find { it.id == teamId } ?: return
        val ids = converters.toStringList(team.playerIds).toMutableList()
        // Prevent duplicate assignment or cross-team assignment
        val alreadyOnAnotherTeam = _teams.value.any { t ->
            t.id != teamId && converters.toStringList(t.playerIds).contains(playerId)
        }
        if (alreadyOnAnotherTeam || ids.contains(playerId)) return
        ids.add(playerId)
        updateTeam(team.copy(playerIds = converters.fromStringList(ids)))
    }

    fun updateTeam(team: TeamEntity) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updateTeam(team)
            _teams.value = dao.getTeamsByTournament(tid)
        }
    }

    // ---- Tournament lifecycle ----

    fun startTournament() {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.SETUP.name) return
        var totalRounds = t.totalRounds
        val count = if (t.isTeamTournament) {
            _teams.value.count { converters.toStringList(it.playerIds).isNotEmpty() }
        } else {
            _players.value.count { it.active && !it.withdrawn }
        }

        if (count < 2) return

        if (!t.manualRoundOverride) {
            when (TournamentType.valueOf(t.type)) {
                TournamentType.KNOCKOUT -> {
                    totalRounds = maxOf(1, ceil(log2(count.toDouble())).toInt())
                }
                TournamentType.ROUND_ROBIN -> {
                    totalRounds = if (count % 2 == 0) count - 1 else count
                }
                TournamentType.SWISS -> if (t.autoCalculateRounds) {
                    totalRounds = maxOf(1, ceil(log2(count.toDouble())).toInt() + 1)
                }
            }
        }

        val updated = t.copy(
            status = TournamentStatus.ACTIVE.name,
            currentRound = 1,
            totalRounds = totalRounds
        )
        viewModelScope.launch {
            dao.updateTournament(updated)
            _tournaments.value = dao.getAllTournaments()
        }
    }

    fun generatePairings(round: Int) {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.ACTIVE.name) return
        val players = getTournamentPlayers()
        val matches = getTournamentMatches()
        val tournamentType = TournamentType.valueOf(t.type)

        val newMatches: List<Match> = when {
            t.isTeamTournament -> {
                val teams = getTournamentTeams()
                when (tournamentType) {
                    TournamentType.ROUND_ROBIN -> generateTeamRoundRobin(teams, round)
                    TournamentType.KNOCKOUT -> generateTeamKnockout(teams, matches, round)
                    TournamentType.SWISS -> generateTeamSwiss(teams, matches, round)
                }
            }
            else -> {
                when (tournamentType) {
                    TournamentType.ROUND_ROBIN -> generateRoundRobin(players, round)
                    TournamentType.KNOCKOUT -> generateKnockout(players, matches, round)
                    TournamentType.SWISS -> generateSwiss(
                        players = players,
                        matches = matches,
                        round = round,
                        totalRounds = t.totalRounds,
                        pointsForWin = t.pointsForWin,
                        pointsForDraw = t.pointsForDraw,
                        pointsForLoss = t.pointsForLoss,
                        avoidClubPairings = t.avoidClubPairings,
                        forcedPairings = emptyList()
                    )
                }
            }
        }

        val tid = t.id
        val matchEntities = newMatches.map { matchToEntity(it, tid) }

        viewModelScope.launch {
            dao.deleteMatchesByRound(tid, round)
            dao.insertMatches(matchEntities)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun updateMatchResult(matchId: String, result: MatchResult?) {
        val match = _matches.value.find { it.id == matchId } ?: return
        updateMatch(match.copy(result = result?.name))
    }

    fun updateMatchPgn(matchId: String, pgn: String?) {
        val match = _matches.value.find { it.id == matchId } ?: return
        updateMatch(match.copy(pgn = pgn))
    }

    private fun updateMatch(match: MatchEntity) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updateMatch(match)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun completeRound() {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.ACTIVE.name) return

        // Guard: all matches must have results
        val roundMatches = _matches.value.filter { it.round == t.currentRound }
        val incomplete = roundMatches.any { m -> m.blackId != null && m.result == null }
        if (incomplete) return

        val nextRound = t.currentRound + 1
        val status = if (nextRound > t.totalRounds) TournamentStatus.COMPLETED.name else TournamentStatus.ACTIVE.name
        val currentRound = if (status == TournamentStatus.COMPLETED.name) t.currentRound else nextRound
        updateTournament(t.copy(status = status, currentRound = currentRound))
    }

    fun rollbackRound() {
        val t = getActiveTournament() ?: return
        if (t.currentRound == 0) return

        if (t.status == TournamentStatus.COMPLETED.name) {
            viewModelScope.launch {
                _matches.value.filter { it.round == t.currentRound }.forEach { m ->
                    dao.updateMatch(m.copy(result = null))
                }
                _matches.value = dao.getMatchesByTournament(t.id)
            }
            updateTournament(t.copy(status = TournamentStatus.ACTIVE.name))
        } else {
            val newRound = t.currentRound - 1
            val newStatus = if (newRound == 0) TournamentStatus.SETUP.name else TournamentStatus.ACTIVE.name
            viewModelScope.launch {
                dao.deleteMatchesByRound(t.id, t.currentRound)
                _matches.value = dao.getMatchesByTournament(t.id)
            }
            updateTournament(t.copy(status = newStatus, currentRound = newRound))
        }
    }

    fun swapPlayers(matchId1: String, isWhite1: Boolean, matchId2: String, isWhite2: Boolean) {
        val tid = _activeTournamentId.value ?: return
        val m1 = _matches.value.find { it.id == matchId1 } ?: return
        val m2 = _matches.value.find { it.id == matchId2 } ?: return

        val p1Id = if (isWhite1) m1.whiteId else m1.blackId
        val p2Id = if (isWhite2) m2.whiteId else m2.blackId
        if (p1Id == null || p2Id == null) return

        val newM1 = if (isWhite1) m1.copy(whiteId = p2Id) else m1.copy(blackId = p2Id)
        val newM2 = if (isWhite2) m2.copy(whiteId = p1Id) else m2.copy(blackId = p1Id)

        viewModelScope.launch {
            dao.updateMatch(newM1)
            dao.updateMatch(newM2)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun addManualMatch(round: Int, whiteId: String?, blackId: String?) {
        val tid = _activeTournamentId.value ?: return
        val boardNum = (_matches.value.filter { it.round == round }.maxOfOrNull { it.boardNumber ?: 0 } ?: 0) + 1
        val match = MatchEntity(
            id = UUID.randomUUID().toString(),
            tournamentId = tid,
            round = round,
            whiteId = whiteId,
            blackId = blackId,
            result = if (blackId == null) MatchResult.BYE.name else null,
            boardNumber = boardNum,
            teamMatchId = null,
            isManual = true,
            pgn = null
        )
        viewModelScope.launch {
            dao.insertMatch(match)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun removeManualMatch(matchId: String) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.deleteMatch(matchId)
            _matches.value = dao.getMatchesByTournament(tid)
        }
    }

    fun activePlayerCount(): Int {
        return _players.value.count { it.active && !it.withdrawn }
    }

    // Entity converters
    fun entityToPlayer(e: PlayerEntity) = Player(
        id = e.id, name = e.name, rating = e.rating, title = e.title,
        club = e.club, active = e.active, withdrawn = e.withdrawn,
        requestedByes = converters.toIntList(e.requestedByes), penaltyPoints = e.penaltyPoints
    )

    fun entityToTeam(e: TeamEntity) = Team(
        id = e.id, name = e.name, playerIds = converters.toStringList(e.playerIds)
    )

    fun entityToMatch(e: MatchEntity) = Match(
        id = e.id, round = e.round, whiteId = e.whiteId, blackId = e.blackId,
        result = try { e.result?.let { MatchResult.valueOf(it) } } catch (_: Exception) { null },
        boardNumber = e.boardNumber, teamMatchId = e.teamMatchId,
        isManual = e.isManual, pgn = e.pgn
    )

    fun matchToEntity(m: Match, tournamentId: String) = MatchEntity(
        id = m.id, tournamentId = tournamentId, round = m.round,
        whiteId = m.whiteId, blackId = m.blackId, result = m.result?.name,
        boardNumber = m.boardNumber, teamMatchId = m.teamMatchId,
        isManual = m.isManual, pgn = m.pgn
    )

    // ---- Team pairing helpers ----

    private fun generateTeamSwiss(teams: List<Team>, matches: List<Match>, round: Int): List<Match> {
        val (teamMatchPoints, teamGamePoints) = calculateTeamScores(teams, matches)
        val resultMatches = mutableListOf<Match>()
        var boardNumber = 1

        var teamsToPair = teams.filter { it.playerIds.isNotEmpty() }
            .sortedWith(compareByDescending<Team> { teamMatchPoints[it.id] ?: 0.0 }
                .thenByDescending { teamGamePoints[it.id] ?: 0.0 })
            .toMutableList()
        if (teamsToPair.isEmpty()) return emptyList()

        val teamByes = mutableSetOf<String>()
        matches.filter { it.teamMatchId != null && it.result == MatchResult.BYE }.forEach { m ->
            teams.find { t -> t.playerIds.contains(m.whiteId) }?.let { teamByes.add(it.id) }
        }

        val teamPlayed = mutableMapOf<String, MutableSet<String>>()
        teams.forEach { teamPlayed[it.id] = mutableSetOf() }
        matches.filter { it.teamMatchId != null && it.whiteId != null && it.blackId != null }.forEach { m ->
            val t1 = teams.find { it.playerIds.contains(m.whiteId) }
            val t2 = teams.find { it.playerIds.contains(m.blackId) }
            if (t1 != null && t2 != null) {
                teamPlayed.getOrPut(t1.id) { mutableSetOf() }.add(t2.id)
                teamPlayed.getOrPut(t2.id) { mutableSetOf() }.add(t1.id)
            }
        }

        if (teamsToPair.size % 2 != 0) {
            var byeIdx = teamsToPair.size - 1
            while (byeIdx >= 0 && teamByes.contains(teamsToPair[byeIdx].id)) byeIdx--
            if (byeIdx < 0) byeIdx = teamsToPair.size - 1
            val byeTeam = teamsToPair[byeIdx]
            val teamMatchId = UUID.randomUUID().toString()
            byeTeam.playerIds.forEach { pid ->
                resultMatches.add(Match(
                    round = round, whiteId = pid, blackId = null,
                    result = MatchResult.BYE, boardNumber = 999, teamMatchId = teamMatchId
                ))
            }
            teamsToPair.removeAt(byeIdx)
        }

        while (teamsToPair.size > 1) {
            val t1 = teamsToPair[0]
            var t2Idx = -1
            val half = teamsToPair.size / 2
            for (j in half until teamsToPair.size) {
                if (teamPlayed[t1.id]?.contains(teamsToPair[j].id) != true) { t2Idx = j; break }
            }
            if (t2Idx == -1) {
                for (j in 1 until teamsToPair.size) {
                    if (teamPlayed[t1.id]?.contains(teamsToPair[j].id) != true) { t2Idx = j; break }
                }
            }
            if (t2Idx == -1) t2Idx = 1

            val t2 = teamsToPair[t2Idx]
            val teamMatchId = UUID.randomUUID().toString()
            val maxBoards = maxOf(t1.playerIds.size, t2.playerIds.size)
            for (b in 0 until maxBoards) {
                val pWhite = t1.playerIds.getOrNull(b)
                val pBlack = t2.playerIds.getOrNull(b)
                val actualWhiteId = if (b % 2 == 0) pWhite else pBlack
                val actualBlackId = if (b % 2 == 0) pBlack else pWhite
                when {
                    actualWhiteId != null && actualBlackId != null -> {
                        resultMatches.add(Match(
                            round = round, whiteId = actualWhiteId, blackId = actualBlackId,
                            boardNumber = boardNumber++, teamMatchId = teamMatchId
                        ))
                    }
                    actualWhiteId != null -> {
                        resultMatches.add(Match(
                            round = round, whiteId = actualWhiteId, blackId = null,
                            result = MatchResult.BYE, boardNumber = boardNumber++, teamMatchId = teamMatchId
                        ))
                    }
                    actualBlackId != null -> {
                        resultMatches.add(Match(
                            round = round, whiteId = actualBlackId, blackId = null,
                            result = MatchResult.BYE, boardNumber = boardNumber++, teamMatchId = teamMatchId
                        ))
                    }
                }
            }
            teamsToPair.removeAt(t2Idx)
            teamsToPair.removeAt(0)
        }
        return resultMatches
    }

    private fun generateTeamRoundRobin(teams: List<Team>, round: Int): List<Match> {
        val activeTeams = teams.filter { it.playerIds.isNotEmpty() }
        if (activeTeams.isEmpty()) return emptyList()
        val n = activeTeams.size
        val isOdd = n % 2 != 0
        val workingTeams = if (isOdd) activeTeams + Team(id = "dummy", name = "Bye") else activeTeams
        val totalTeams = workingTeams.size
        if (totalTeams == 0) return emptyList()

        val p = workingTeams.toMutableList()
        for (i in 1 until round) {
            if (p.isEmpty()) break
            val last = p.removeLast()
            p.add(1, last)
        }

        val resultMatches = mutableListOf<Match>()
        var boardNumber = 1

        for (i in 0 until totalTeams / 2) {
            val t1 = p[i]
            val t2 = p[totalTeams - 1 - i]
            if (t1.id == "dummy" || t2.id == "dummy") {
                val realTeam = if (t1.id == "dummy") t2 else t1
                val teamMatchId = UUID.randomUUID().toString()
                realTeam.playerIds.forEach { pid ->
                    resultMatches.add(Match(
                        round = round, whiteId = pid, blackId = null,
                        result = MatchResult.BYE, boardNumber = 999, teamMatchId = teamMatchId
                    ))
                }
            } else {
                val isEvenRound = round % 2 == 0
                val teamWhite = if (isEvenRound) t2 else t1
                val teamBlack = if (isEvenRound) t1 else t2
                val teamMatchId = UUID.randomUUID().toString()
                val maxBoards = maxOf(teamWhite.playerIds.size, teamBlack.playerIds.size)
                for (b in 0 until maxBoards) {
                    val pWhite = teamWhite.playerIds.getOrNull(b)
                    val pBlack = teamBlack.playerIds.getOrNull(b)
                    val actualWhiteId = if (b % 2 == 0) pWhite else pBlack
                    val actualBlackId = if (b % 2 == 0) pBlack else pWhite
                    when {
                        actualWhiteId != null && actualBlackId != null -> {
                            resultMatches.add(Match(
                                round = round, whiteId = actualWhiteId, blackId = actualBlackId,
                                boardNumber = boardNumber++, teamMatchId = teamMatchId
                            ))
                        }
                        actualWhiteId != null -> {
                            resultMatches.add(Match(
                                round = round, whiteId = actualWhiteId, blackId = null,
                                result = MatchResult.BYE, boardNumber = boardNumber++, teamMatchId = teamMatchId
                            ))
                        }
                        actualBlackId != null -> {
                            resultMatches.add(Match(
                                round = round, whiteId = actualBlackId, blackId = null,
                                result = MatchResult.BYE, boardNumber = boardNumber++, teamMatchId = teamMatchId
                            ))
                        }
                    }
                }
            }
        }
        return resultMatches
    }

    private fun generateTeamKnockout(teams: List<Team>, matches: List<Match>, round: Int): List<Match> {
        if (round == 1) {
            val activeTeams = teams.filter { it.playerIds.isNotEmpty() }
            if (activeTeams.isEmpty()) return emptyList()
            val n = activeTeams.size
            if (n == 0) return emptyList()
            val nextPowerOfTwo = 2.0.pow(ceil(log2(n.toDouble()))).toInt()
            val byesCount = nextPowerOfTwo - n

            val shuffled = activeTeams.shuffled().toMutableList()
            val resultMatches = mutableListOf<Match>()
            var boardNumber = 1

            val byeTeams = shuffled.take(byesCount)
            shuffled.removeAll(byeTeams)
            byeTeams.forEach { team ->
                val tmid = UUID.randomUUID().toString()
                team.playerIds.forEach { pid ->
                    resultMatches.add(Match(
                        round = 1, whiteId = pid, blackId = null,
                        result = MatchResult.BYE, boardNumber = 999, teamMatchId = tmid
                    ))
                }
            }

            for (i in shuffled.indices step 2) {
                val t1 = shuffled[i]
                val t2 = shuffled.getOrNull(i + 1)
                val tmid = UUID.randomUUID().toString()
                if (t2 != null) {
                    val maxBoards = maxOf(t1.playerIds.size, t2.playerIds.size)
                    for (b in 0 until maxBoards) {
                        val p1 = t1.playerIds.getOrNull(b)
                        val p2 = t2.playerIds.getOrNull(b)
                        val wid = if (b % 2 == 0) p1 else p2
                        val bid = if (b % 2 == 0) p2 else p1
                        if (wid != null || bid != null) {
                            resultMatches.add(Match(
                                round = round, whiteId = wid, blackId = bid,
                                boardNumber = boardNumber++, teamMatchId = tmid
                            ))
                        }
                    }
                } else {
                    t1.playerIds.forEach { pid ->
                        resultMatches.add(Match(
                            round = round, whiteId = pid, blackId = null,
                            result = MatchResult.BYE, boardNumber = 999, teamMatchId = tmid
                        ))
                    }
                }
            }
            return resultMatches
        }

        val prevRoundMatches = matches.filter { it.round == round - 1 }
        val teamMatchIds = prevRoundMatches.mapNotNull { it.teamMatchId }.distinct()
        val winners = mutableListOf<String>()

        teamMatchIds.forEach { tmid ->
            val tmatches = prevRoundMatches.filter { it.teamMatchId == tmid }
            val firstMatch = tmatches.firstOrNull() ?: return@forEach
            if (firstMatch.result == MatchResult.BYE) {
                firstMatch.whiteId?.let { wid ->
                    teams.find { it.playerIds.contains(wid) }?.let { winners.add(it.id) }
                }
                return@forEach
            }
            val twid = firstMatch.whiteId ?: return@forEach
            val tbid = firstMatch.blackId ?: return@forEach
            val teamWhite = teams.find { it.playerIds.contains(twid) }
            val teamBlack = teams.find { it.playerIds.contains(tbid) }
            if (teamWhite != null && teamBlack != null) {
                var matchGPW = 0.0
                var matchGPB = 0.0
                tmatches.forEach { m ->
                    val isW = teamWhite.playerIds.contains(m.whiteId)
                    when (m.result) {
                        MatchResult.WHITE_WIN -> if (isW) matchGPW++ else matchGPB++
                        MatchResult.BLACK_WIN -> if (isW) matchGPB++ else matchGPW++
                        MatchResult.DRAW -> { matchGPW += 0.5; matchGPB += 0.5 }
                        MatchResult.FORFEIT_WHITE -> if (isW) matchGPB++ else matchGPW++
                        MatchResult.FORFEIT_BLACK -> if (isW) matchGPW++ else matchGPB++
                        else -> {}
                    }
                }
                if (matchGPW > matchGPB) winners.add(teamWhite.id)
                else if (matchGPB > matchGPW) winners.add(teamBlack.id)
                else winners.add(teamWhite.id)
            }
        }

        val resultMatches = mutableListOf<Match>()
        var boardNumber = 1
        for (i in winners.indices step 2) {
            val t1 = teams.find { it.id == winners[i] } ?: continue
            val t2 = teams.find { it.id == winners.getOrNull(i + 1) ?: "" }
            val tmid = UUID.randomUUID().toString()
            if (t2 != null) {
                val maxBoards = maxOf(t1.playerIds.size, t2.playerIds.size)
                for (b in 0 until maxBoards) {
                    val p1 = t1.playerIds.getOrNull(b)
                    val p2 = t2.playerIds.getOrNull(b)
                    val wid = if (b % 2 == 0) p1 else p2
                    val bid = if (b % 2 == 0) p2 else p1
                    if (wid != null || bid != null) {
                        resultMatches.add(Match(
                            round = round, whiteId = wid, blackId = bid,
                            boardNumber = boardNumber++, teamMatchId = tmid
                        ))
                    }
                }
            } else {
                t1.playerIds.forEach { pid ->
                    resultMatches.add(Match(
                        round = round, whiteId = pid, blackId = null,
                        result = MatchResult.BYE, boardNumber = 999, teamMatchId = tmid
                    ))
                }
            }
        }
        return resultMatches
    }
}
