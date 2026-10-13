package com.example.chesspairmobile

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.data.preferences.AppPreferences
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.ui.screens.*
import com.example.chesspairmobile.ui.theme.ChessPairMobileTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiUxScreensTest {
    @get:Rule val compose = createComposeRule()
    private val players = listOf(
        PlayerEntity("a", "t", "Alexandra Montgomery", 1800, "WFM", "Northside Chess Club", active = true, withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0),
        PlayerEntity("b", "t", "Benjamin Fischer", 1750, null, "Westside", active = true, withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0)
    )
    private val tournament = TournamentEntity("t", "Autumn Open Championship", "Open", "SWISS", false, false, false, "90+30", "ACTIVE", 1, 5, 1.0, 0.5, 0.0, false, "[\"BUCHHOLZ\",\"SONNEBORN_BERGER\"]")
    private val teams = listOf(TeamEntity("one", "t", "Northside", "[\"b\",\"a\"]"), TeamEntity("two", "t", "Westside", "[]"))
    private val matches = listOf(MatchEntity("match", "t", 1, "a", "b", "WHITE_WIN", 1, "tm", false, null))

    @Test fun knockoutDrawBlocksCompletionAndOffersDecidingOutcomes() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
            RoundsScreen(tournament.copy(type = "KNOCKOUT"), players, matches = matches.map { it.copy(result = "DRAW") },
                onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> },
                onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> })
        } }
        compose.onNodeWithText("Resolve knockout draws").assertIsNotEnabled()
        compose.onNodeWithText("½–½").assertDoesNotExist()
        compose.onNodeWithText("1–0").assertIsDisplayed()
        compose.onNodeWithText("0–1").assertIsDisplayed()
    }

    @Test fun unsupportedFormatsDoNotOfferForcedPairings() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
            RoundsScreen(tournament.copy(type = "ROUND_ROBIN"), players, matches = matches,
                onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> },
                onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> })
        } }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Forced pairings…").assertDoesNotExist()
    }

    @Test fun setupEditorPreservesExistingConfigurationAndSavesChanges() {
        var savedName: String? = null
        var savedPoints: Triple<Double, Double, Double>? = null
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
            CreateTournamentDialog(onDismiss = {}, initialTournament = tournament.copy(status = "SETUP", pointsForWin = 3.0),
                onCreate = { name, _, _, _, _, points, _, _, _, _, _, _, _, _ -> savedName = name; savedPoints = points })
        } }
        compose.onNodeWithText("Tournament name").performTextReplacement("Edited Open")
        compose.onNodeWithText("Save tournament").performClick()
        assertEquals("Edited Open", savedName)
        assertEquals(Triple(3.0, 0.5, 0.0), savedPoints)
    }

    @Test fun checkboxLabelIsTheToggleTarget() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
            CreateTournamentDialog({}, { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> })
        } }
        compose.onNodeWithText("Calculate rounds from roster").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Number of rounds").assertIsDisplayed()
    }

    @Test fun missingByeResultCanBeRecoveredInCurrentRound() {
        var result: MatchResult? = null
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
            RoundsScreen(tournament, players, matches = listOf(matches.single().copy(blackId = null, result = null)),
                onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, selected, _ -> result = selected },
                onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> })
        } }
        compose.onNodeWithText("Restore bye result").performClick()
        assertEquals(MatchResult.BYE, result)
    }

    @Test fun roundPickerOpensRecordedRoundsForReview() {
        compose.setContent { ChessPairMobileTheme(darkTheme = false) {
            RoundsScreen(tournament.copy(currentRound = 2), players, matches = matches,
                onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> },
                onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> })
        } }
        compose.onNodeWithText("Round 2 / 5").performClick()
        compose.onNodeWithText("Round 1").performClick()
        compose.onNodeWithText("Round 1 / 5").assertIsDisplayed()
        compose.onNodeWithText("Correct results").assertIsDisplayed()
        compose.onNodeWithText("Alexandra Montgomery").assertIsDisplayed()
    }

    @Test fun closingBoardSearchRestoresTheUnfilteredList() {
        compose.setContent { ChessPairMobileTheme(darkTheme = false) {
            RoundsScreen(tournament, players, matches = matches,
                onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> },
                onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> })
        } }
        compose.onNodeWithContentDescription("Find board or player").performClick()
        compose.onNodeWithText("Find board or player").performTextInput("Unknown player")
        compose.onNodeWithText("No boards match this filter").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close board search").performClick()
        compose.onNodeWithText("Alexandra Montgomery").assertIsDisplayed()
        compose.onNodeWithText("No boards match this filter").assertDoesNotExist()
    }

    @Test fun compactStandingsKeepsTiebreaksAvailableInTableAndHistory() {
        compose.setContent { ChessPairMobileTheme(darkTheme = false) {
            StandingsScreen(tournament, players, matches, onExportCsv = { _, _ -> "csv" })
        } }
        compose.onNodeWithText("BH").assertDoesNotExist()
        compose.onNodeWithText("All tiebreaks").performClick()
        compose.onNodeWithText("BH").assertExists()
        compose.onNodeWithText("All tiebreaks").performClick()
        compose.onNodeWithText("BH").assertDoesNotExist()
        compose.onNodeWithText("Alexandra Montgomery").performClick()
        compose.onNodeWithText("BH: Buchholz").assertIsDisplayed()
    }

    @Test fun compactResultOptionsStillSupportForfeitAndClear() {
        var selected: MatchResult? = null
        compose.setContent { ChessPairMobileTheme(darkTheme = false) {
            var current by remember { mutableStateOf<MatchResult?>(null) }
            ResultSelector(current, { selected = it; current = it })
        } }
        compose.onNodeWithContentDescription("More result options").performClick()
        compose.onNodeWithText("White wins by forfeit").performClick()
        assertEquals(MatchResult.FORFEIT_BLACK, selected)
        compose.onNodeWithContentDescription("More result options").performClick()
        compose.onNodeWithText("Clear result").performClick()
        assertNull(selected)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
        val roots = compose.onAllNodes(isRoot())
        File(folder, "$name.png").outputStream().use { roots[roots.fetchSemanticsNodes().lastIndex].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeAndCreationForm() {
        compose.setContent {
            var create by remember { mutableStateOf(false) }
            ChessPairMobileTheme(dynamicColor = false, darkTheme = false) {
                if (create) CreateTournamentDialog({}, { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> })
                else HomeScreen(listOf(tournament), onSettings = {}, activeId = "t", onSelectTournament = {}, onCreateTournament = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> create = true }, onDeleteTournament = {})
            }
        }
        capture("home")
        compose.onNodeWithText("Create tournament").performClick()
        // Home owns its full-screen creation dialog.
        compose.onNodeWithText("Tournament basics").assertIsDisplayed()
        capture("create-tournament")
    }

    @Test fun overviewShowsPendingTask() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { DashboardScreen(tournament, players, emptyList(), matches.map { it.copy(result = null) }) } }
        compose.onNodeWithText("Enter remaining results").assertIsDisplayed()
        capture("overview")
    }

    @Test fun completedRosterHasNoImportOrAddAction() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { PlayersScreen(players, tournamentStatus = "COMPLETED", tournamentRound = 1, onAddPlayer = { _, _, _, _ -> }, onBulkAdd = {}, onRemovePlayer = {}, onWithdrawPlayer = {}, onRejoinPlayer = {}, onClearPlayers = {}) } }
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Add player").assertDoesNotExist()
        capture("players-completed")
    }

    @Test fun teamRosterCanRenderStoredBoardOrder() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { TeamsScreen(teams, players, onAddTeam = {}, onAssignPlayer = { _, _ -> }) } }
        compose.onNodeWithText("Benjamin Fischer").assertIsDisplayed()
        capture("teams")
    }

    @Test fun completedResultsRequireExplicitCorrectionAndNeverClearOnRepeatSelection() {
        var corrected: Int? = null
        var selected: MatchResult? = null
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { RoundsScreen(tournament.copy(status = "COMPLETED"), players, matches = matches, onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, result, round -> selected = result; corrected = round }, onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> }) } }
        compose.onNodeWithText("Correct results").assertIsDisplayed()
        capture("rounds-completed")
        compose.onNodeWithText("Correct results").performClick()
        compose.onNodeWithText("Edit results").performClick()
        compose.onNodeWithText("1–0").performClick()
        assertEquals(1, corrected)
        assertEquals(MatchResult.WHITE_WIN, selected)
        capture("rounds-correction")
    }

    @Test fun activeResultEntry() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { RoundsScreen(tournament, players, matches = matches, onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> }, onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> }) } }
        compose.onNodeWithText("Review and complete Round 1").assertIsDisplayed()
        capture("rounds-active")
    }

    @Test fun standingsRenderTeamAndIndividualViews() {
        val roster = listOf(TeamEntity("one", "t", "Northside", "[\"a\"]"), TeamEntity("two", "t", "Westside", "[\"b\"]"))
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { StandingsScreen(tournament.copy(isTeamTournament = true), players, matches, roster, { _, _ -> "csv" }) } }
        compose.onNodeWithText("2.0 match points · 1.0 game points").assertIsDisplayed()
        capture("standings-teams")
        compose.onNodeWithText("Individuals").performClick()
        capture("standings-individual")
    }

    @Test fun settingsAreUsableWithoutTournament() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { SettingsScreen(AppPreferences(context), {}, {}) } }
        capture("settings")
    }

    @Test fun teamStartRequiresAssignedTeams() {
        compose.setContent { ChessPairMobileTheme(dynamicColor = false, darkTheme = false) { RoundsScreen(tournament.copy(status = "SETUP", currentRound = 0, isTeamTournament = true), players, matches = emptyList(), onStartTournament = {}, onGeneratePairings = {}, onUpdateResult = { _, _, _ -> }, onCompleteRound = {}, onRollbackRound = {}, onSwapPlayers = { _, _, _, _ -> }) } }
        compose.onNodeWithText("Start Tournament").assertIsNotEnabled()
        compose.onNodeWithText("Create at least two teams and assign active players to each.").assertIsDisplayed()
    }
}
