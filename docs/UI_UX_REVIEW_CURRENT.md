# Current UI/UX review

Reviewed October 12, 2026 at revision `dc0bd95`. This report describes the current source, including the earlier UI/UX fixes. The historical audit in `UI_UX_AUDIT.md` describes an older baseline.

## Implementation — October 12, 2026

The seven findings below have now been addressed in the working tree. Their source references describe the reviewed revision before these changes.

- Shared UI/view-model completion checks block unresolved individual knockout draws and tied team knockout matches. Individual knockout entry offers decisive outcomes and explains that the deciding result must be entered after a playoff. A match records the deciding round outcome; this change does not add separate playoff-game records.
- Reopening keeps every recorded result, including automatic byes. A current-round bye with a missing result can be restored explicitly. Header and bottom completion states use the same checks.
- Forced-pairing actions appear only for individual Swiss events, reject overlapping/unavailable players, show existing pairs, and consume the list transactionally when pairings are generated. Final-round requests are unavailable once pairings exist.
- Bye cards have no swap affordance. Invalid swap attempts report a reason, and selection highlights identify the selected color.
- Undrawn rounds are labeled explicitly. Standings scores, player history, and CSV filenames/exports follow the last round with pairings, preventing future requested-bye points from appearing early. Export is unavailable before any round has pairings.
- Team ranking shows its fixed match-points/game-points/Sonneborn-Berger order and each team's Sonneborn-Berger value. Team setup labels the configurable order as individual tiebreaks.
- Overview and the setup review provide an Edit tournament action. The form loads the existing tournament configuration and saves setup changes without replacing the roster; optional section/time-control fields can be cleared.
- Checkbox/switch rows include their labels as toggle targets, long player names reserve space for scores, tiebreak actions have their own row, history result text uses theme colors, repeated player entry shows feedback inside its dialog, and restored rating tiebreaks are editable and visible.

Validation: all 72 local unit tests pass; `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` succeed; `git diff --check` passes. Local regression tests cover completion, forced-pairing validation, and standings boundaries. Device tests for reopening without data loss, blocked knockout advancement, setup persistence, and screen interactions compile and are packaged, but have not run. Android-device execution and visual checks remain unavailable; the phone was locked during the review and is no longer connected at the latest discovery check.

The updated app is `app/build/outputs/apk/debug/app-debug.apk`; the device-test APK is `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Neither was installed on the user's phone. Device tests should be run in the existing isolated review package using `-PuiReviewApplicationId=com.example.chesspairmobile.uiuxreview`. No database schema changes are required.

## Scope and evidence

Reviewed all seven Compose screens, navigation, shared controls, theme, and the behavior behind setup, roster management, pairings, results, and standings. Findings below are traced through source code; they have not been reproduced on a device.

Android device discovery succeeded and ChessPair is installed. Opening the app did not make its UI inspectable: the phone remained locked, and the capture contained a black screen with a System UI hierarchy. No app screenshot, TalkBack test, large-font check, or interaction test was completed. The installed APK was not replaced, and tournament data was not edited. No automated tests were run for this analysis.

The current UI already addresses several earlier problems: four tournament destinations, separate search and add actions, setup-only import, explicit result corrections, three primary outcomes, frozen standings identity columns, and safer backup flows. The highest remaining priorities are workflow correctness and making available controls match actual behavior.

## Findings in priority order

### 1. High: knockout draws can advance the round and silently eliminate both players

**Trigger:** In an individual knockout tournament with another round to play, enter a draw on a board, enter the other results, complete the round, and generate the next pairings.

**Behavior:** The same Draw selector is available in every tournament format. The completion button treats any non-null result as complete, and the view model uses the same condition. The knockout generator then skips drawn matches when collecting winners. Both players disappear from the next round. If every match is drawn, the next round can have no pairings and repeatedly offer Generate pairings.

**Fix:** Require an advancing player for every knockout match before allowing completion. Present a draw-resolution or playoff-winner flow that preserves the original game result and identifies who advances. Validate this in the view model as well as the UI.

**Evidence:** `RoundsScreen.kt:138`, `RoundsScreen.kt:759`; `TournamentViewModel.kt:484`; `PairingEngine.kt:297–306`.

### 2. High: reopening a tournament can leave bye results impossible to restore

**Trigger:** Finish a tournament whose final round includes a pairing bye, then choose More → Reopen tournament and confirm.

**Behavior:** Reopen clears the result of every final-round match, including BYE entries. Bye cards have no result selector. Completion requires all matches to have a result, so the cleared bye blocks finishing the tournament. The header can even say All results in after ordinary games are re-entered because its check excludes bye cards, while the bottom button still reports a remaining result.

**Fix:** Preserve automatic bye results when reopening, or provide an explicit, valid way to restore them. Use one completeness rule for the header, bottom action, and view model. Consider reopening without erasing existing results; the separate correction mode already provides deliberate editing.

**Evidence:** `TournamentViewModel.kt:504–510`, `TournamentViewModel.kt:488–492`; `RoundsScreen.kt:252`, `RoundsScreen.kt:669`.

### 3. Medium: forced-pairing controls promise behavior unsupported by most formats

**Trigger:** Create a round-robin, knockout, or team tournament; choose Forced pairings; add a pair; generate pairings.

**Behavior:** The menu enables forced pairings in any setup or active event. Only the individual Swiss generator receives the stored forced-pairing list. Other generators ignore it. The dialog closes after Add without explaining that the request cannot take effect. Existing forced pairings are summarized only as a count, and players already in those pairs remain selectable.

**Fix:** Show this feature only for supported events, explain which round it affects, display the saved pairs, and reject overlapping player assignments. Provide visible confirmation when the request is saved.

**Evidence:** `RoundsScreen.kt:174–182`, `RoundsScreen.kt:499–505`, `RoundsScreen.kt:517`, `RoundsScreen.kt:532`; `TournamentViewModel.kt:425–448`.

### 4. Medium: bye cards offer swap actions that silently do nothing

**Trigger:** Generate an odd-player individual round before any ordinary result is entered. Enter Swap Players mode and select the bye recipient and another player.

**Behavior:** Swap mode is shown because its readiness condition ignores bye matches. The bye card displays the White player's swap button, but the view model rejects a swap involving a match without a Black player. Selection is cleared after the attempt with no explanation.

**Fix:** Support changing the bye recipient safely, or disable that card's swap action with a clear reason. Report unsuccessful swaps instead of resetting the selection silently.

**Evidence:** `RoundsScreen.kt:326`, `RoundsScreen.kt:360–372`, `RoundsScreen.kt:608–615`, `RoundsScreen.kt:734–746`; `TournamentViewModel.kt:527–529`.

### 5. Medium: standings imply a round is represented before it has pairings

**Trigger:** Start a tournament and open Standings before generating Round 1, or complete a round and open Standings before drawing the next round.

**Behavior:** The selected round follows `currentRound`. The label says Standings through Round N, but provisional is appended only when an existing match has a null result. An undrawn round has no such matches, so the screen presents the previous results—or all zeroes—as standings through the new round without qualification.

**Fix:** Default to the latest round with pairings/results, or clearly say Round N has not started and identify the completed round represented. Use the same distinction in export labels.

**Evidence:** `StandingsScreen.kt:110`, `StandingsScreen.kt:118–120`, `StandingsScreen.kt:187`; `TournamentViewModel.kt:494–497`.

### 6. Medium: team tiebreak configuration does not control team ranking

**Trigger:** During team setup, change tiebreak order in creation or the setup Standings screen. Later view team rankings.

**Behavior:** The shared tiebreak editor does not explain that its order is used for individual standings. Team ranking always sorts by match points, game points, then Sonneborn-Berger. The team view shows match/game points but hides Sonneborn-Berger, so organizers cannot inspect the criterion that can decide an otherwise tied ranking.

**Fix:** Label the existing editor Individual tiebreaks for team events. Explain the team ranking order and display its decisive values, or implement a separate supported team-tiebreak configuration.

**Evidence:** `HomeScreen.kt:329`; `StandingsScreen.kt:73–75`, `StandingsScreen.kt:114`, `StandingsScreen.kt:220–226`; `Tiebreaks.kt:309–312`.

### 7. Medium: setup review lacks a configuration summary and an edit path

**Trigger:** Create a tournament, then use the Overview action Review and start to verify its settings.

**Behavior:** The destination shows a Start Tournament button and any readiness error. It does not summarize format, scoring, pairing constraints, acceleration, or tiebreak order. Overview displays rounds, roster count, section, and time control but no edit action. Global Settings changes defaults for future creation; only tiebreak order has a tournament-specific editing route. A mistaken round count or scoring value generally requires recreating the tournament through the current UI.

**Fix:** Add a setup summary and Edit tournament action before starting. Make the Review and start destination provide the review it promises, and clearly distinguish tournament settings from defaults.

**Evidence:** `DashboardScreen.kt:37`, `DashboardScreen.kt:53–62`; `RoundsScreen.kt:193–215`; `MainActivity.kt:192–206`, `MainActivity.kt:275–283`; `SettingsScreen.kt:153–161`.

## Accessibility and layout risks requiring device verification

- **Unassociated toggle labels:** Create-form checkboxes and the forfeit switch are separate interactive nodes from their adjacent explanatory Text. Their rows do not provide combined toggle semantics. A screen reader may focus the control without its purpose, and tapping the text does not activate the toggle. Use a labeled toggleable row with one accessible target. Sources: `HomeScreen.kt:306–307`, `HomeScreen.kt:321`; `SettingsScreen.kt:135–149`.
- **Long names can squeeze out scores:** The name in `PlayerRow` is an unweighted Text inside the same Row as the score. It can consume the remaining width before the score is measured. Reserve width for the score and constrain or weight the name. Also test the fixed 44 dp White/Black label at larger font sizes. Sources: `RoundsScreen.kt:694`, `RoundsScreen.kt:707–725`.
- **Tiebreak editing is crowded on phones:** Each row reserves 144 dp for three icon controls, plus number, spacing, and padding. In a dialog's nested card, little room remains for the criterion name and description. Consider a single overflow action or an adaptive stacked layout. Source: `TiebreakConfigPanel.kt:145–191`.
- **History result colors remain fixed:** The player-history dialog still uses fixed green, red, and gray text on tinted surfaces with 11 sp labels. These need inspection in dark/dynamic themes even though podium and ordinary result-card text were improved. Source: `StandingsScreen.kt:358–375`.
- **Repeated-entry feedback is outside the dialog:** Add & Next clears the name, while the added message is rendered by the app's underlying SnackbarHost. Verify visibility while the modal remains open; an in-dialog success message would make repeated entry easier to trust. Sources: `PlayersScreen.kt:350–416`; `TournamentViewModel.kt:252`; `MainActivity.kt:100`, `MainActivity.kt:112`.

## Additional edge case

Restored or older tournaments can contain the `RATING` tiebreak. Sorting still honors it, but the current add-tiebreak list and standings-column mapping omit it. Such a tournament can be ranked by a criterion absent from the detailed table; once removed, it cannot be added back through the editor. Sources: `Tiebreaks.kt:169–173`; `TiebreakConfigPanel.kt:25–33`; `StandingsScreen.kt:492–502`.

## Recommended order

1. Fix knockout advancement and reopening with byes; cover these complete workflows with focused tests.
2. Align forced-pairing and swap controls with supported operations and show rejection feedback.
3. Correct standings round labels and explain team ranking criteria.
4. Add a reachable setup review/edit screen.
5. Verify toggles, long names, modal feedback, dark themes, narrow screens, keyboard behavior, and increased font sizes on an unlocked device.
