package com.example.chesspairmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.example.chesspairmobile.BuildConfig
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
    private val _isLoadingTestTournament = MutableStateFlow(false)
    val isLoadingTestTournament = _isLoadingTestTournament.asStateFlow()
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
            // Race: tournament just created, Flow hasn't emitted yet Ã¢â‚¬â€œ assume SETUP
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
        swissExtraRound: Boolean = true,
        initialColour: String = "W"
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
            swissExtraRound = swissExtraRound,
            initialColour = initialColour
        )
        viewModelScope.launch {
            dao.insertTournament(t)
            // Optimistically update local list so requireStatus() works immediately
            // (the Flow collector will reconcile shortly)
            _tournaments.value = _tournaments.value + t
            setActiveTournament(t.id)
        }
    }

    fun loadTestTournament(preset: TestTournamentPreset, onLoaded: () -> Unit) {
        if (!BuildConfig.DEBUG || _isLoadingTestTournament.value) return
        _isLoadingTestTournament.value = true
        viewModelScope.launch {
            try {
                val data = buildTestTournament(preset)
                db.withTransaction {
                    dao.insertTournament(data.tournament)
                    dao.insertPlayers(data.players)
                    data.teams.forEach { dao.insertTeam(it) }
                }
                _tournaments.update { current ->
                    current.filterNot { it.id == data.tournament.id } + data.tournament
                }
                setActiveTournament(data.tournament.id)
                onLoaded()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _messages.emit("Could not load test tournament: ${error.message ?: "Try again"}")
            } finally {
                _isLoadingTestTournament.value = false
            }
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
            _messages.emit("${newPlayers.size} players imported Ã‚Â· ${preview.duplicates} duplicates skipped")
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
            db.withTransaction {
                val eligible = _players.value.filter { it.active && !it.withdrawn }
                val starters = if (t.type == "ROUND_ROBIN") eligible.shuffled() else eligible.sortedWith { a, b -> initialPlayerOrder.compare(entityToPlayer(a), entityToPlayer(b)) }
                starters.forEachIndexed { index, player -> dao.updatePlayer(player.copy(pairingNumber = index + 1)) }
                _players.value.filter { !it.active || it.withdrawn }.forEach { dao.updatePlayer(it.copy(pairingNumber = 0)) }
                val teamOrder = if (t.type == "ROUND_ROBIN") getTournamentTeams().filter { it.playerIds.isNotEmpty() }.shuffled()
                    .mapIndexed { index, team -> team.copy(pairingNumber = index + 1) }
                    else withTeamPairingNumbers(getTournamentTeams().filter { it.playerIds.isNotEmpty() })
                teamOrder.forEach { team ->
                    _teams.value.find { it.id == team.id }?.let { dao.updateTeam(it.copy(pairingNumber = team.pairingNumber)) }
                }
                dao.updateTournament(updated)
            }
        }
    }

    fun generatePairings(round: Int) {
        val t = getActiveTournament() ?: return
        if (t.status != "ACTIVE" || round != t.currentRound || _matches.value.any { it.round == round }) return
        val playerSnapshot = _players.value.toList()
        val teamSnapshot = _teams.value.toList()
        val history = getTournamentMatches().toList()
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            try {
                val seedPool = playerSnapshot.map(::entityToPlayer).filter {
                    if (t.type == "ROUND_ROBIN" && playerSnapshot.any { p -> p.pairingNumber > 0 }) it.pairingNumber > 0
                    else it.pairingNumber > 0 || (it.active && !it.withdrawn) || t.type == "ROUND_ROBIN"
                }
                val seeded = withPairingNumbers(seedPool)
                val players = playerSnapshot.map { entity -> seeded.find { it.id == entity.id } ?: entityToPlayer(entity) }
                val teams = withTeamPairingNumbers(teamSnapshot.map(::entityToTeam).filter { it.playerIds.isNotEmpty() })
                val generated = withContext(Dispatchers.Default) {
                    if (t.isTeamTournament) {
                        when (t.type) {
                            "ROUND_ROBIN" -> generateTeamRoundRobin(teams, round, players)
                            "KNOCKOUT" -> generateTeamKnockout(teams, history, round, players)
                            else -> generateTeamSwiss(teams, history, round, t.totalRounds, t.initialColour.first(), players)
                        }
                    } else when (t.type) {
                        "ROUND_ROBIN" -> generateRoundRobin(players, round, t.avoidClubPairings)
                        "KNOCKOUT" -> generateKnockout(players, history, round)
                        else -> generateFideDutch(players, history, round, t.totalRounds,
                            t.pointsForWin, t.pointsForDraw, t.pointsForLoss, t.avoidClubPairings,
                            getForcedPairingList(), converters.toAccelerationMode(t.accelerationMode), t.initialColour.first())
                    }
                }
                if (generated.isEmpty()) throw PairingException("No pairings available. Review the roster and previous-round results.")
                if (!t.isTeamTournament && t.type == "SWISS") {
                    swissRoundIssue(players, history, generated, round, t.totalRounds, t.pointsForWin, t.pointsForDraw, t.pointsForLoss)?.let { throw PairingException(it) }
                }
                db.withTransaction {
                    val current = dao.getTournament(t.id)
                    if (current != t || dao.getPlayersByTournament(t.id) != playerSnapshot || dao.getTeamsByTournament(t.id) != teamSnapshot || dao.getMatchesByRound(t.id, round).isNotEmpty()) {
                        throw PairingException("Tournament data changed during pairing. Generate this round again.")
                    }
                    playerSnapshot.forEach { entity ->
                        seeded.find { it.id == entity.id }?.let { dao.updatePlayer(entity.copy(pairingNumber = it.pairingNumber)) }
                    }
                    teamSnapshot.forEach { entity ->
                        teams.find { it.id == entity.id }?.let { dao.updateTeam(entity.copy(pairingNumber = it.pairingNumber)) }
                    }
                    dao.insertMatches(generated.map { matchToEntity(it, t.id) })
                    if (supportsForcedPairings(t.type, t.isTeamTournament)) dao.updateForcedPairings(t.id, "[]")
                }
            } catch (error: PairingException) { notifyUser(error.message ?: "The arbiter must resolve this round.") }
        }
    }

    fun updateMatchResult(matchId: String, result: MatchResult?, correctionRound: Int? = null) {
        val t = getActiveTournament() ?: return
        val match = _matches.value.find { it.id == matchId } ?: return
        if (!canUpdateResult(t.status, t.currentRound, match.round, correctionRound)) return
        if (match.whiteId == null || match.blackId == null) return // Scheduled rests/byes and missing-board forfeits are automatic.
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

    fun completeRound(): Boolean {
        val t = getActiveTournament() ?: return false
        if (t.status != TournamentStatus.ACTIVE.name) return false

        // Guard: all matches must have results (both regular and bye/manual matches)
        val roundMatches = _matches.value.filter { it.round == t.currentRound }
        roundCompletionIssue(t.type, t.isTeamTournament, roundMatches.map(::entityToMatch), getTournamentTeams())?.let {
            notifyUser(it)
            return false
        }

        if (t.type == "SWISS" && !t.isTeamTournament) {
            swissRoundIssue(getTournamentPlayers(), getTournamentMatches(), roundMatches.map(::entityToMatch), t.currentRound,
                t.totalRounds, t.pointsForWin, t.pointsForDraw, t.pointsForLoss)?.let { notifyUser(it); return false }
        }

        val nextRound = t.currentRound + 1
        val status = if (nextRound > t.totalRounds) TournamentStatus.COMPLETED.name else TournamentStatus.ACTIVE.name
        val currentRound = if (status == TournamentStatus.COMPLETED.name) t.currentRound else nextRound
        updateTournament(t.copy(status = status, currentRound = currentRound))
        return true
    }

    fun rollbackRound() {
        val t = getActiveTournament() ?: return
        if (t.currentRound == 0) return

        if (t.status == TournamentStatus.COMPLETED.name) {
            // Reopening changes editing permissions; it must preserve recorded games and automatic byes.
            viewModelScope.launch {
                dao.updateTournament(t.copy(status = TournamentStatus.ACTIVE.name))
            }
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
        if (tournament.status != "ACTIVE" || tournament.isTeamTournament || tournament.type != "SWISS") return
        val tid = _activeTournamentId.value ?: return
        val m1 = _matches.value.find { it.id == matchId1 } ?: return
        val m2 = _matches.value.find { it.id == matchId2 } ?: return
        if (m1.round != tournament.currentRound || m2.round != tournament.currentRound || m1.result != null || m2.result != null) {
            notifyUser("Only pending boards in the current round can be swapped.")
            return
        }

        if (m1.blackId == null || m2.blackId == null) {
            notifyUser("Bye recipients cannot be swapped.")
            return
        }
        val p1Id = if (isWhite1) m1.whiteId else m1.blackId
        val p2Id = if (isWhite2) m2.whiteId else m2.blackId
        if (p1Id == null || p2Id == null) return

        if (m1.id == m2.id && isWhite1 == isWhite2) return
        val changed = if (m1.id == m2.id) listOf(m1.copy(whiteId = m1.blackId, blackId = m1.whiteId, isManual = true)) else listOf(
            (if (isWhite1) m1.copy(whiteId = p2Id) else m1.copy(blackId = p2Id)).copy(isManual = true),
            (if (isWhite2) m2.copy(whiteId = p1Id) else m2.copy(blackId = p1Id)).copy(isManual = true))
        val proposed = getTournamentMatches().filter { it.round == tournament.currentRound }.map { current ->
            changed.find { it.id == current.id }?.let(::entityToMatch) ?: current
        }
        swissRoundIssue(getTournamentPlayers(), getTournamentMatches(), proposed, tournament.currentRound, tournament.totalRounds,
            tournament.pointsForWin, tournament.pointsForDraw, tournament.pointsForLoss)?.let { notifyUser(it); return }
        viewModelScope.launch {
            db.withTransaction { changed.forEach { dao.updateMatch(it) } }
        }
    }

    fun addManualMatch(round: Int, whiteId: String?, blackId: String?) {
        val t = getActiveTournament() ?: return
        if (t.status != "ACTIVE" || t.type != "SWISS" || t.isTeamTournament || round != t.currentRound) {
            notifyUser("Manual pairing is available only for the current individual Swiss round.")
            return
        }
        val match = Match(round = round, whiteId = whiteId, blackId = blackId,
            result = if (blackId == null) MatchResult.BYE else null,
            boardNumber = (_matches.value.filter { it.round == round }.maxOfOrNull { it.boardNumber ?: 0 } ?: 0) + 1, isManual = true)
        val proposed = getTournamentMatches().filter { it.round == round } + match
        swissRoundIssue(getTournamentPlayers(), getTournamentMatches(), proposed, round, t.totalRounds,
            t.pointsForWin, t.pointsForDraw, t.pointsForLoss, requireComplete = false)?.let { notifyUser(it); return }
        viewModelScope.launch { dao.insertMatch(matchToEntity(match, t.id)) }
    }

    fun removeManualMatch(matchId: String) {
        val t = getActiveTournament() ?: return
        val match = _matches.value.find { it.id == matchId } ?: return
        if (t.status != "ACTIVE" || t.type != "SWISS" || !match.isManual || match.round != t.currentRound || match.result != null) return
        viewModelScope.launch {
            dao.deleteMatch(matchId)
        }
    }

    // ---- Tournament editing ----
    fun saveTournamentSetup(edited: TournamentEntity): Boolean {
        val current = getActiveTournament() ?: return false
        if (current.status != "SETUP" || edited.id != current.id) return false
        val scores = listOf(edited.pointsForWin, edited.pointsForDraw, edited.pointsForLoss)
        if (edited.name.isBlank() || scores.any { !it.isFinite() || it < 0 } ||
            (edited.type == "SWISS" && !edited.autoCalculateRounds && edited.totalRounds < 1)) return false
        val updated = current.copy(
            name = edited.name.trim(), section = edited.section, type = edited.type,
            isTeamTournament = edited.isTeamTournament, totalRounds = edited.totalRounds,
            autoCalculateRounds = edited.autoCalculateRounds, manualRoundOverride = edited.manualRoundOverride,
            timeControl = edited.timeControl, pointsForWin = edited.pointsForWin,
            pointsForDraw = edited.pointsForDraw, pointsForLoss = edited.pointsForLoss,
            avoidClubPairings = edited.avoidClubPairings, accelerationMode = edited.accelerationMode, initialColour = edited.initialColour,
            tiebreakOrder = edited.tiebreakOrder, swissExtraRound = edited.swissExtraRound,
            forcedPairings = if (supportsForcedPairings(edited.type, edited.isTeamTournament)) current.forcedPairings else "[]"
        )
        viewModelScope.launch { dao.updateTournament(updated); _messages.emit("Tournament settings saved") }
        return true
    }

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
        if (t.status != "SETUP" && (pointsForWin != null || pointsForDraw != null || pointsForLoss != null || avoidClubPairings != null || accelerationMode != null)) {
            notifyUser("Pairing and scoring policies are fixed when the tournament starts.")
            return
        }
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
            converters.toTiebreakList(t.tiebreakOrder), upToRound = t.currentRound,
            tournamentType = if (t.isTeamTournament) null else TournamentType.valueOf(t.type)
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
        if (!supportsForcedPairings(t.type, t.isTeamTournament)) {
            notifyUser("Forced pairings are available for individual Swiss tournaments.")
            return
        }
        if (t.status == "ACTIVE" && t.currentRound == t.totalRounds && _matches.value.any { it.round == t.currentRound }) {
            notifyUser("The final round already has pairings.")
            return
        }
        forcedPairingIssue(ForcedPairing(whiteId, blackId), getForcedPairingList(), getTournamentPlayers())?.let {
            notifyUser(it)
            return
        }
        val current = t.forcedPairings
        val fps = converters.toStringList(current).toMutableList()
        fps.add("$whiteId:$blackId")
        updateTournament(t.copy(forcedPairings = converters.fromStringList(fps)))
        notifyUser("Forced pairing saved for the next unpaired round")
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
        id = e.id, name = e.name, playerIds = converters.toStringList(e.playerIds), pairingNumber = e.pairingNumber
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
            converters.toTiebreakList(t.tiebreakOrder), teamView ?: t.isTeamTournament,
            if (t.isTeamTournament) null else TournamentType.valueOf(t.type)
        )
    }
}
