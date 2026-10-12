package com.example.chesspairmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.data.models.*
import com.example.chesspairmobile.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

class TournamentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.tournamentDao()
    private val converters = Converters()
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private fun notifyUser(message: String) { viewModelScope.launch { _messages.emit(message) } }

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

    // Cached model-layer lists derived from the entity flows. Updated whenever
    // the corresponding entity StateFlow changes. Screens that need typed models
    // call the get* helpers which read from these caches in O(1).
    private val _modelPlayers = MutableStateFlow<List<Player>>(emptyList())
    private val _modelTeams = MutableStateFlow<List<Team>>(emptyList())
    private val _modelMatches = MutableStateFlow<List<Match>>(emptyList())

    private var loadJob: kotlinx.coroutines.Job? = null
    private var pairingJob: kotlinx.coroutines.Job? = null
    private var playersJob: kotlinx.coroutines.Job? = null
    private var teamsJob: kotlinx.coroutines.Job? = null
    private var matchesJob: kotlinx.coroutines.Job? = null

    init {
        // Observe all tournaments reactively so the home screen updates when
        // tournaments are created, renamed, or deleted from any path.
        viewModelScope.launch {
            dao.observeAllTournaments().collect {
                _tournaments.value = it
                if (_activeTournamentId.value != null && it.none { t -> t.id == _activeTournamentId.value }) {
                    setActiveTournament(null)
                }
            }
        }
    }

    fun setActiveTournament(id: String?) {
        loadJob?.cancel()
        playersJob?.cancel()
        teamsJob?.cancel()
        matchesJob?.cancel()
        _activeTournamentId.value = id
        _players.value = emptyList()
        _teams.value = emptyList()
        _matches.value = emptyList()
        _modelPlayers.value = emptyList()
        _modelTeams.value = emptyList()
        _modelMatches.value = emptyList()
        if (id != null) {
            // Subscribe to per-tournament flows; cancellation in setActiveTournament
            // ensures we don't leak collectors across tournament switches.
            playersJob = viewModelScope.launch {
                dao.observePlayersByTournament(id).collect {
                    _players.value = it
                    _modelPlayers.value = it.map(::entityToPlayer)
                }
            }
            teamsJob = viewModelScope.launch {
                dao.observeTeamsByTournament(id).collect {
                    _teams.value = it
                    _modelTeams.value = it.map(::entityToTeam)
                }
            }
            matchesJob = viewModelScope.launch {
                dao.observeMatchesByTournament(id).collect {
                    _matches.value = it
                    _modelMatches.value = it.map(::entityToMatch)
                }
            }
        } else {
            _players.value = emptyList()
            _teams.value = emptyList()
            _matches.value = emptyList()
            _modelPlayers.value = emptyList()
            _modelTeams.value = emptyList()
            _modelMatches.value = emptyList()
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
        val t = activeTournamentOrNull
        if (t == null) {
            // Race: tournament just created, Flow hasn't emitted yet – assume SETUP
            return _activeTournamentId.value != null && TournamentStatus.SETUP in allowed
        }
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
        tiebreakOrder: List<TiebreakType> = defaultTiebreakOrderForType(type),
        accelerationMode: AccelerationMode = AccelerationMode.NONE,
        swissExtraRound: Boolean = true
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
            tiebreakOrder = converters.fromTiebreakList(tiebreakOrder),
            accelerationMode = accelerationMode.name,
            swissExtraRound = swissExtraRound
        )
        viewModelScope.launch {
            dao.insertTournament(t)
            // Optimistically update local list so requireStatus() works immediately
            // (the Flow collector will reconcile shortly)
            _tournaments.value = _tournaments.value + t
            setActiveTournament(t.id)
        }
    }

    fun updateTournament(updates: TournamentEntity) {
        viewModelScope.launch {
            dao.updateTournament(updates)
        }
    }

    fun deleteTournament(id: String) {
        viewModelScope.launch {
            dao.deletePlayersByTournament(id)
            dao.deleteTeamsByTournament(id)
            dao.deleteMatchesByTournament(id)
            dao.deleteTournamentById(id)
            if (_activeTournamentId.value == id) {
                setActiveTournament(null)
            }
        }
    }

    fun getTournamentPlayers(): List<Player> = _modelPlayers.value

    fun getTournamentTeams(): List<Team> = _modelTeams.value

    fun getTournamentMatches(): List<Match> = _modelMatches.value

    // ---- Player management (SETUP only) ----

    fun addPlayer(name: String, rating: Int? = null, title: String? = null, club: String? = null) {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
        if (getActiveTournament()?.let { it.isTeamTournament && it.status == "ACTIVE" } == true) return
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
            _messages.emit("${p.name} added")
        }
    }

    fun bulkAddPlayers(csvData: String) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        val preview = previewPlayerImport(csvData, _players.value.map { it.name })
        if (preview.errors.isNotEmpty()) { notifyUser("Review import errors before adding players"); return }
        val newPlayers = preview.players.map { player ->
            PlayerEntity(id = UUID.randomUUID().toString(), tournamentId = tid, name = player.name,
                rating = player.rating, title = player.title, club = player.club,
                active = true, withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0)
        }
        if (newPlayers.isNotEmpty()) viewModelScope.launch {
            dao.insertPlayers(newPlayers)
            _messages.emit("${newPlayers.size} players imported · ${preview.duplicates} duplicates skipped")
        }
    }
    fun updatePlayer(player: PlayerEntity) {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
        if (getActiveTournament()?.let { it.isTeamTournament && it.status == "ACTIVE" } == true) return
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updatePlayer(player)
        }
    }

    fun removePlayer(id: String) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                dao.deletePlayer(id)
                dao.deleteMatchesByPlayer(tid, id)
                dao.getTeamsByTournament(tid).forEach { team ->
                    dao.updateTeam(team.copy(playerIds = converters.fromStringList(converters.toStringList(team.playerIds).filterNot { it == id })))
                }
            }
            _messages.emit("Player removed")
        }
    }

    fun clearPlayers() {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                dao.deletePlayersByTournament(tid)
                dao.deleteMatchesByTournament(tid)
                dao.getTeamsByTournament(tid).forEach { dao.updateTeam(it.copy(playerIds = "[]")) }
            }
            _players.value = emptyList()
            _matches.value = emptyList()
        }
    }

    fun withdrawPlayer(id: String) {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
        val player = _players.value.find { it.id == id } ?: return
        updatePlayer(player.copy(withdrawn = true))
    }

    fun rejoinPlayer(id: String) {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
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
        }
    }

    fun assignPlayerToTeam(teamId: String, playerId: String) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        if (_players.value.none { it.id == playerId && it.active && !it.withdrawn }) return
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
        if (!requireStatus(TournamentStatus.SETUP)) return
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updateTeam(team)
        }
    }

    fun moveTeamPlayer(teamId: String, playerId: String, offset: Int) {
        if (!requireStatus(TournamentStatus.SETUP)) return
        val team = _teams.value.find { it.id == teamId } ?: return
        val ids = converters.toStringList(team.playerIds).toMutableList()
        val from = ids.indexOf(playerId)
        val to = from + offset
        if (from < 0 || to !in ids.indices) return
        java.util.Collections.swap(ids, from, to)
        updateTeam(team.copy(playerIds = converters.fromStringList(ids)))
    }

    // ---- Tournament lifecycle ----

    fun startTournament() {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.SETUP.name) return
        startRequirement(getTournamentPlayers(), getTournamentTeams(), t.isTeamTournament)?.let {
            notifyUser(it)
            return
        }
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
                    val base = maxOf(1, ceil(log2(count.toDouble())).toInt())
                    totalRounds = if (t.swissExtraRound) base + 1 else base
                }
            }
        }

        startRequirement(getTournamentPlayers(), getTournamentTeams(), t.isTeamTournament, totalRounds)?.let {
            notifyUser(it)
            return
        }
        val updated = t.copy(
            status = TournamentStatus.ACTIVE.name,
            currentRound = 1,
            totalRounds = totalRounds
        )
        viewModelScope.launch {
            dao.updateTournament(updated)
        }
    }

    fun generatePairings(round: Int) {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.ACTIVE.name) return
        if (round != t.currentRound || _matches.value.any { it.round == round }) return
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
                    TournamentType.ROUND_ROBIN -> generateRoundRobin(players, round, t.avoidClubPairings)
                    TournamentType.KNOCKOUT -> generateKnockout(players, matches, round)
                    TournamentType.SWISS -> generateFideDutch(
                        players = players,
                        matches = matches,
                        round = round,
                        totalRounds = t.totalRounds,
                        pointsForWin = t.pointsForWin,
                        pointsForDraw = t.pointsForDraw,
                        pointsForLoss = t.pointsForLoss,
                        avoidClubPairings = t.avoidClubPairings,
                        forcedPairings = getForcedPairingList(),
                        accelerationMode = try { AccelerationMode.valueOf(t.accelerationMode) } catch (_: Exception) { AccelerationMode.NONE }
                    )
                }
            }
        }

        val tid = t.id
        val matchEntities = newMatches.map { matchToEntity(it, tid) }

        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            dao.deleteMatchesByRound(tid, round)
            dao.insertMatches(matchEntities)
        }
    }

    fun updateMatchResult(matchId: String, result: MatchResult?, correctionRound: Int? = null) {
        val t = getActiveTournament() ?: return
        val match = _matches.value.find { it.id == matchId } ?: return
        if (!canUpdateResult(t.status, t.currentRound, match.round, correctionRound)) return
        updateMatch(match.copy(result = result?.name))
    }

    fun updateMatchPgn(matchId: String, pgn: String?) {
        if (!requireStatus(TournamentStatus.ACTIVE, TournamentStatus.COMPLETED)) return
        val match = _matches.value.find { it.id == matchId } ?: return
        updateMatch(match.copy(pgn = pgn))
    }

    private fun updateMatch(match: MatchEntity) {
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.updateMatch(match)
        }
    }

    fun completeRound() {
        val t = getActiveTournament() ?: return
        if (t.status != TournamentStatus.ACTIVE.name) return

        // Guard: all matches must have results (both regular and bye/manual matches)
        val roundMatches = _matches.value.filter { it.round == t.currentRound }
        if (roundMatches.isEmpty()) return
        val incomplete = roundMatches.any { m -> m.result == null }
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
            }
            updateTournament(t.copy(status = TournamentStatus.ACTIVE.name))
        } else {
            val newRound = t.currentRound - 1
            val newStatus = if (newRound == 0) TournamentStatus.SETUP.name else TournamentStatus.ACTIVE.name
            viewModelScope.launch {
                dao.deleteMatchesByRound(t.id, t.currentRound)
                dao.updateTournament(t.copy(status = newStatus, currentRound = newRound))
            }
        }
    }

    fun swapPlayers(matchId1: String, isWhite1: Boolean, matchId2: String, isWhite2: Boolean) {
        val tournament = getActiveTournament() ?: return
        if (tournament.status != "ACTIVE" || tournament.isTeamTournament) return
        val tid = _activeTournamentId.value ?: return
        val m1 = _matches.value.find { it.id == matchId1 } ?: return
        val m2 = _matches.value.find { it.id == matchId2 } ?: return
        if (m1.round != tournament.currentRound || m2.round != tournament.currentRound || m1.result != null || m2.result != null) return

        if (m1.blackId == null || m2.blackId == null) return
        val p1Id = if (isWhite1) m1.whiteId else m1.blackId
        val p2Id = if (isWhite2) m2.whiteId else m2.blackId
        if (p1Id == null || p2Id == null) return

        val newM1 = if (isWhite1) m1.copy(whiteId = p2Id) else m1.copy(blackId = p2Id)
        val newM2 = if (isWhite2) m2.copy(whiteId = p1Id) else m2.copy(blackId = p1Id)

        viewModelScope.launch {
            dao.updateMatch(newM1)
            dao.updateMatch(newM2)
        }
    }

    fun addManualMatch(round: Int, whiteId: String?, blackId: String?) {
        if (!requireStatus(TournamentStatus.ACTIVE, TournamentStatus.COMPLETED)) return
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
        }
    }

    fun removeManualMatch(matchId: String) {
        if (!requireStatus(TournamentStatus.ACTIVE, TournamentStatus.COMPLETED)) return
        val tid = _activeTournamentId.value ?: return
        viewModelScope.launch {
            dao.deleteMatch(matchId)
        }
    }

    // ---- Tournament editing ----
    fun updateTournamentSettings(
        name: String? = null,
        section: String? = null,
        timeControl: String? = null,
        pointsForWin: Double? = null,
        pointsForDraw: Double? = null,
        pointsForLoss: Double? = null,
        avoidClubPairings: Boolean? = null,
        accelerationMode: AccelerationMode? = null,
        liveBroadcastUrl: String? = null,
        tiebreakOrder: List<TiebreakType>? = null
    ) {
        val t = getActiveTournament() ?: return
        val updated = t.copy(
            name = name?.takeIf { it.isNotBlank() } ?: t.name,
            section = section?.takeIf { it.isNotBlank() } ?: t.section,
            timeControl = timeControl?.takeIf { it.isNotBlank() } ?: t.timeControl,
            pointsForWin = pointsForWin ?: t.pointsForWin,
            pointsForDraw = pointsForDraw ?: t.pointsForDraw,
            pointsForLoss = pointsForLoss ?: t.pointsForLoss,
            avoidClubPairings = avoidClubPairings ?: t.avoidClubPairings,
            accelerationMode = accelerationMode?.name ?: t.accelerationMode,
            liveBroadcastUrl = liveBroadcastUrl?.takeIf { it.isNotBlank() } ?: t.liveBroadcastUrl,
            tiebreakOrder = tiebreakOrder?.let { converters.fromTiebreakList(it) } ?: t.tiebreakOrder
        )
        updateTournament(updated)
    }

    // ---- Live broadcast ----
    fun broadcastResults(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val t = getActiveTournament() ?: return
        val url = t.liveBroadcastUrl?.takeIf { it.isNotBlank() } ?: run {
            onResult(false, "No broadcast URL configured")
            return
        }
        val standings = calculateStandings(
            getTournamentPlayers(), getTournamentMatches(),
            t.pointsForWin, t.pointsForDraw, t.pointsForLoss,
            converters.toTiebreakList(t.tiebreakOrder)
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = buildBroadcastJson(t, standings)
                    val broadcastUrl = java.net.URI(url).toURL()
                    val client = (broadcastUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    client.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    val code = client.responseCode
                    // Drain the response stream so the connection can be reused
                    (if (code in 200..299) client.inputStream else client.errorStream)?.use { it.copyTo(java.io.ByteArrayOutputStream()) }
                    code
                }
            }
            result.fold(
                onSuccess = { code ->
                    val ok = code in 200..299
                    onResult(ok, if (ok) "Results broadcast OK" else "Server returned $code")
                },
                onFailure = { e -> onResult(false, "Broadcast failed: ${e.message ?: e.javaClass.simpleName}") }
            )
        }
    }

    private fun buildBroadcastJson(t: TournamentEntity, standings: List<PlayerStanding>): String {
        val gson = com.google.gson.Gson()
        val data = mapOf(
            "tournament" to t.name,
            "round" to t.currentRound,
            "totalRounds" to t.totalRounds,
            "standings" to standings.mapIndexed { idx, s ->
                mapOf(
                    "rank" to (idx + 1),
                    "name" to s.player.name,
                    "rating" to (s.player.rating ?: 0),
                    "score" to s.score,
                    "buchholz" to s.buchholz,
                    "sonnebornBerger" to s.sonnebornBerger
                )
            }
        )
        return gson.toJson(data)
    }

    // ---- Forced pairings ----
    fun addForcedPairing(whiteId: String, blackId: String) {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
        val t = getActiveTournament() ?: return
        val current = t.forcedPairings
        val fps = converters.toStringList(current).toMutableList()
        fps.add("$whiteId:$blackId")
        updateTournament(t.copy(forcedPairings = converters.fromStringList(fps)))
    }

    fun clearForcedPairings() {
        if (!requireStatus(TournamentStatus.SETUP, TournamentStatus.ACTIVE)) return
        val t = getActiveTournament() ?: return
        updateTournament(t.copy(forcedPairings = "[]"))
    }

    fun getForcedPairingList(): List<ForcedPairing> {
        val t = getActiveTournament() ?: return emptyList()
        return converters.toStringList(t.forcedPairings).mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) ForcedPairing(parts[0], parts[1]) else null
        }
    }

    fun activePlayerCount(): Int {
        return _players.value.count { it.active && !it.withdrawn }
    }

    // Entity converters
    fun entityToPlayer(e: PlayerEntity) = Player(
        id = e.id, name = e.name, rating = e.rating, title = e.title,
        club = e.club, pairingNumber = e.pairingNumber,
        active = e.active, withdrawn = e.withdrawn,
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

    // ---- Standings CSV export ----
    fun exportStandingsCsv(round: Int? = null, teamView: Boolean? = null): String {
        val t = getActiveTournament() ?: return ""
        val throughRound = (round ?: t.currentRound).coerceIn(0, t.currentRound)
        return standingsCsv(
            getTournamentPlayers(), getTournamentTeams(), getTournamentMatches(), throughRound,
            t.pointsForWin, t.pointsForDraw, t.pointsForLoss,
            converters.toTiebreakList(t.tiebreakOrder), teamView ?: t.isTeamTournament
        )
    }
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
            val last = p.removeAt(p.lastIndex)
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
