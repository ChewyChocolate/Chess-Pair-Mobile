# ChessPair UI/UX review

Reviewed October 12, 2026 against checkout `2c07912`. Implementation followed in the working tree.

## Implementation status — October 12, 2026

The main fixes are implemented across all seven screens:

- Four tournament navigation destinations, Players/Teams roster tabs, one Home app bar, and Settings available before selection.
- A contextual Overview action and progress based on completed rounds and entered results.
- Full-screen creation with essentials first, advanced settings, field validation, and saveable form state; configured tiebreak defaults are preserved, including an explicitly empty order.
- Dedicated player search, labeled add actions, overflow menus, withdrawal terminology, confirmation for removal/withdrawal, and setup-only import/clear controls.
- CSV file selection and preview with row errors, quoted-field handling, duplicate counts, and completion feedback.
- Team rosters displayed in stored board order, explicit reordering, searchable assignment, and locked roster controls after starting.
- Shared start readiness, protected completed/historical results, deliberate correction mode scoped to the selected round, explicit result clearing, three normal outcomes, pending-board filters, board/player search, contextual completion, and final-round wording.
- Team and individual standings, team scoring that waits for all boards before awarding match points, frozen identity columns, compact/default tiebreak display, readable podium text, and matching round boundaries for details and CSV export.
- Consistent database snapshots for backups, schema/integrity and record validation before restore, transactional restore without replacing the open file, a recoverable copy, persistent feedback, and no restart requirement.
- Noninteractive status badges, clearer tiebreak instructions, sufficient layout space for adjacent icon controls, and visible White/Black labels.

The findings and source line references below describe the baseline revision `2c07912`, before these changes. They remain as the rationale and device-validation checklist.

Validation: the normal debug app and separate Android test APKs build; all 65 local unit tests pass; `lintDebug` passes. `git diff --check` passes. Backup integration and screen interaction/screenshot tests compile. Device discovery succeeded, but Android rejected installation of the separate review package with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. No device tests or screenshots were completed, and the existing application was not replaced.

The normal debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. For isolated device checks, build the app and test APKs with the quoted Gradle option `'-PuiReviewApplicationId=com.example.chesspairmobile.uiuxreview'`; run `BackupManagerTest` and `UiUxScreensTest` in that package. The test app stores captured screens in its own external-files `ui-review` directory. Final device validation should include narrow layouts, long names, larger fonts, the keyboard, dark/dynamic themes, TalkBack, and restore/recovery on disposable tournament data.

## Scope and assessment

The initial source-based review covered all seven Compose screens, the navigation shell, theme, shared tiebreak controls, and the view-model behavior behind important actions. The existing audit was checked against the baseline source and expanded. Its implementation status is recorded above. Layout crowding, keyboard behavior, TalkBack output, and device-specific rendering still need verification on a device.

ChessPair has a useful foundation: Material 3 controls, consistent spacing in many places, light/dark/system themes, searchable players, empty states, repeated player entry, round history, and standings drill-down. The main opportunity is to organize these features around the arbiter's immediate task. Setup, managing a live round, and reviewing final results need different primary actions and different editing rules.

## Recommended priorities

| Priority | Area | Current presentation | Recommended change |
| --- | --- | --- | --- |
| First | Action reliability | Some enabled controls silently fail, and completed results remain editable | Align controls with allowed operations; show outcomes and explicit correction mode |
| First | Team event workflow | Team events have individual standings only; displayed roster numbers can differ from pairing order | Add team standings and team match summaries; display and edit the stored board order |
| First | Round snapshots | CSV export and player history do not follow the selected standings round | Use one round boundary for the table, details, and export; include it in labels and filenames |
| First | Backup restore | Any selected file of at least 16 bytes passes the only validation before database replacement | Validate the database and schema, explain replacement, and provide recovery and persistent restart feedback |
| First | Player management | Search also creates players; many actions share each row; withdrawal is called Forfeit | Separate search/add; use player details and an overflow menu; correct terminology |
| First | Round operation | Five result buttons per board; completion in the header; vague pending status | Make normal result entry dominant; move exceptions into a sheet; show remaining boards and one contextual primary action |
| Next | Navigation | Five destinations, six for teams, mixing the tournament list with tournament tools | Use Overview, Players, Rounds, Standings; put Teams inside Players and tournament switching in the header |
| Next | Tournament setup | A long dialog mixes basics, scoring, and specialized pairing rules | Use a full-screen form with essentials first and collapsed advanced settings |
| Next | Standings | Rank, name, points, and all tiebreak columns scroll together | Keep player identity visible; provide compact and detailed views |
| Throughout | Readability | Small secondary labels, constrained icons, fixed result/podium colors | Use accessible semantic colors, reserve sufficient touch space, and verify text scaling |

## Screen-by-screen recommendations

### Tournament list and navigation

The list provides recognizable tournament cards, status labels, and an active border. However, the global shell renders the selected tournament's app bar on Home while Home renders its own ChessPair app bar. This creates two title bars after selection. Settings is accessible only when a tournament is active, so a fresh installation cannot reach backup restore or theme/default preferences from Home.

Use one app bar per destination. Label the list “Tournaments,” make “Create tournament” a labeled action, and give it an obvious button within the empty state. Place settings on the tournament list as well. Inside a tournament, show a tournament switcher or back-to-list action in the header.

Use four persistent tournament destinations: Overview, Players, Rounds, and Standings. For team tournaments, provide Players/Teams tabs inside roster management. This keeps global navigation at one hierarchy level and avoids adding a sixth bottom item. Android recommends three to five equally important destinations in a navigation bar.

Replace status/type `SuggestionChip(onClick = {})` controls with noninteractive badges. Their current button affordance promises an action that does not happen. Move tournament deletion to a labeled overflow menu, retaining the existing confirmation.

Evidence: `MainActivity.kt:82`, `MainActivity.kt:99`, `HomeScreen.kt:48`, `HomeScreen.kt:161`, `HomeScreen.kt:196`.

### Overview / dashboard

The dashboard repeats the tournament name from the app bar, then presents progress, statistics, and quick links. Those statistics are useful, but they do not tell the organizer what needs attention. Setup can even say “Add 0 more player(s) ... then head to Rounds to start.” Round progress uses the current round, so the final round can read 100% while its games are still pending.

Make the first card answer “What should I do next?”:

- Setup: a checklist for active players, team readiness when applicable, and settings review; primary action “Review and start.”
- Active round without pairings: “Generate Round N pairings.”
- Pairings ready: “N of M results entered,” primary action “Enter remaining results.”
- All results entered: “Review and complete Round N.”
- Completed: final standings and export/share actions.

Keep player/team counts beneath that card. Show completed rounds separately from current-round result progress, and calculate readiness from the same rules used by `startTournament`. Use full-width stacked actions when team labels cannot fit comfortably side by side.

Evidence: `DashboardScreen.kt:47`, `DashboardScreen.kt:58`, `DashboardScreen.kt:85`, `DashboardScreen.kt:109`, `TournamentViewModel.kt:312`.

### Create tournament

The dialog contains name, section, format, team mode, round count, time control, club constraints, acceleration, scoring, and tiebreak configuration. Tiebreaks are already collapsible, which is helpful, but the surrounding configuration remains a lengthy modal form.

Use a full-screen setup form with a stable bottom Create button. Put name, format, individual/team mode, and round calculation first. Group optional scoring and pairing rules in “Advanced settings.” Add brief format descriptions and explanations for Baku/Lim acceleration. Show a concise summary before creation and provide field errors instead of silently substituting scoring defaults when parsing fails.

For round-robin and knockout, replace the disabled empty round field with “Calculated from the number of participants.” Currently `autoCalc` defaults to true, disables the field for all formats, and its checkbox is only rendered for Swiss. Also ensure the configured default tiebreak order survives opening the dialog: the initial `LaunchedEffect(type)` replaces it with the format default before customization.

Evidence: `HomeScreen.kt:226`, `HomeScreen.kt:236`, `HomeScreen.kt:242`, `HomeScreen.kt:279`, `HomeScreen.kt:290`, `HomeScreen.kt:348`.

### Players and teams

The player field filters existing entries while its Add button creates a player from the same text. Searching a club name, for example, also enables creating a player with that name. The separate add dialog is a better model and already supports repeated entry.

Use a dedicated search field and a labeled Add player action. Preserve the repeated-entry flow, but show “Player added” feedback and make it clear whether rating/title/club carry over to the next player. Offer a CSV file picker as well as paste import, with a preview, row errors, duplicate warnings, and an imported/skipped count.

Player rows currently accommodate name and metadata alongside Edit, Byes, Forfeit/Rejoin, and Remove. This leaves little room for long names on phones. Make the row open player details; use an overflow menu for secondary actions and reserve visible text for name, rating/club, and an explicit status badge. Label withdrawal “Withdraw,” because `onWithdraw` only sets `withdrawn = true`; game forfeits belong in match result entry. Provide undo or an appropriate confirmation for individual removal/withdrawal.

For teams, replace long assignment dropdowns with a searchable selection sheet and make board order explicit. Pass tournament status into the screen so controls reflect editing permissions. The current Add Team button is always shown although `addTeam` only accepts setup state.

Evidence: `PlayersScreen.kt:231`, `PlayersScreen.kt:278`, `PlayersScreen.kt:318`, `PlayersScreen.kt:331`, `PlayersScreen.kt:335`, `TeamsScreen.kt:37`, `TeamsScreen.kt:221`, `TournamentViewModel.kt:265`, `TournamentViewModel.kt:277`.

### Rounds and results

The board cards clearly group opponents and board numbers, but five result choices on every board compete for space. The forfeit symbols “+ -” and “- +” are only explained after selection. Selecting the same result again clears it, an interaction users may trigger accidentally.

Keep three common outcomes visible: White win, Draw, Black win, with chess notation as a secondary label. Put forfeits and an explicit Clear result action in a result sheet or exception menu. Selecting the already selected result should leave it selected. Use neutral selection styling rather than presenting Black's win as an error-red event.

Show “12 of 16 results entered” and an All / Pending filter, plus search by board or player. Present a stable contextual action below the working area: generate pairings, review remaining results, or complete the round. Move rollback and forced pairings to the overflow menu. Show a review summary before completion and use distinct final-round wording: “Finish tournament and show final standings.”

Completed and historical rounds should be read-only by default. If corrections are supported, make “Edit results” an explicit mode with a clear explanation of the affected standings. Show every supported result consistently in both editable and read-only cards.

Evidence: `RoundsScreen.kt:136`, `RoundsScreen.kt:251`, `RoundsScreen.kt:356`, `RoundsScreen.kt:383`, `RoundsScreen.kt:617`, `RoundsScreen.kt:718`.

### Standings

The striped table, round selection, export, and player history are useful. With five configured tiebreaks, the declared columns consume 524 dp before horizontal padding (36 rank + 160 name + 48 points + 5 × 56). A phone user must scroll horizontally; because every column scrolls, the player identity disappears while reading later tiebreaks.

Use a compact default showing rank, name, score, and the leading tiebreak. Offer a detailed table with frozen identity columns and selectable tiebreak columns. Explain abbreviations such as BH and S-B through column information controls. Add search and clearly label current-round standings as provisional when results remain pending. Make the player-row detail affordance discoverable.

Use dark, readable rank text with gold/silver/bronze as decoration. In the fallback light theme, gold text `#FFD700` over its 15% gold-tinted white background has approximately 1.32:1 contrast, calculated from the declared colors. That falls below the 4.5:1 normal-text benchmark. This is a source calculation, not a screenshot measurement.

Evidence: `StandingsScreen.kt:185`, `StandingsScreen.kt:228`, `StandingsScreen.kt:233`, `StandingsScreen.kt:382`, `StandingsScreen.kt:411`, `StandingsScreen.kt:420`.

### Settings and visual consistency

Settings is organized into sensible cards. Use user-facing “Back up tournaments” and “Restore backup” labels instead of Export DB / Import DB. Explain that restore replaces existing data before the file operation, and present the restart requirement persistently after a successful restore.

Centralize visual choices for status badges, screen headers, empty states, and section spacing. The brown/tan chess palette is a useful identity, but Android 12+ dynamic color currently takes precedence; decide whether device personalization is intentional and provide a preference if both are desired. Result selectors and podium text use fixed colors, so they need separate light/dark treatment even when the main theme adapts.

Treat names and scores as primary text. Keep 11–12 sp text for genuinely secondary information, rather than essential actions or explanations. Reserve at least 48 × 48 dp layout space for adjacent interactive controls. Several icons have explicit 24–40 dp sizes; Compose can expand their hit regions automatically, so these are spacing/overlap risks to inspect rather than confirmed small-hit-target failures.

Evidence: `SettingsScreen.kt:276`, `SettingsScreen.kt:284`, `Theme.kt:15`, `Type.kt:21`, `PlayersScreen.kt:215`, `TeamsScreen.kt:200`, `TiebreakConfigPanel.kt:175`.

## Behavior issues to fix before visual polish

1. **Unavailable actions look usable.** Player Import and Clear All are rendered outside the setup guard, but the view model immediately returns outside setup. Add Team has a similar mismatch. Hide unavailable actions or disable them with a reason. Sources: `PlayersScreen.kt:77`, `PlayersScreen.kt:83`, `TournamentViewModel.kt:212`, `TournamentViewModel.kt:255`, `TeamsScreen.kt:37`.
2. **Completed final-round results remain editable.** `showResults` depends on round equality without checking tournament status; match updates have no completion guard. Introduce explicit correction mode. Sources: `RoundsScreen.kt:356`, `TournamentViewModel.kt:395`.
3. **Historical forfeit results lose their badge.** The read-only result mapping handles only WHITE_WIN, BLACK_WIN, and DRAW. Add every applicable result variant. Source: `RoundsScreen.kt:617`.
4. **Team start readiness disagrees across layers.** The screen enables Start based on two active players; the view model requires two nonempty teams for team events. Use shared readiness and show the unmet requirement. Sources: `RoundsScreen.kt:185`, `TournamentViewModel.kt:316`.
5. **Tiebreak editing in Standings is unreachable.** Setup returns before rendering, while the editing panel further down requires setup. Put tournament-specific settings on a reachable setup/details screen. Sources: `StandingsScreen.kt:60`, `StandingsScreen.kt:211`.
6. **Broadcast feedback has no rendered host.** The route creates a SnackbarHostState and queues messages, but does not connect that state to a SnackbarHost in either the parent scaffold or RoundsScreen. Render the host or pass the state into the round scaffold. Sources: `MainActivity.kt:219`, `MainActivity.kt:236`, `RoundsScreen.kt:124`.
7. **Instructions do not match the tiebreak controls.** The panel says to drag and tap to remove, but implements arrow buttons and a separate close button. Update the instructions or implement the described interactions. Source: `TiebreakConfigPanel.kt:57`.

These findings are traced through source code; runtime reproduction is still needed before calling them device-tested bugs.

## Additional findings from the current-source review

### Team tournaments lack an end-to-end team view — high priority

`StandingsScreen` always calls `calculateStandings` with players and matches; it receives no teams and does not branch on `isTeamTournament`. The CSV export does the same. A team ranking function, `calculateTeamStandings`, exists in the logic layer but is not connected to this screen. Organizers can create a team event without a way to see or export its team rankings.

Provide Team / Individual standings for team events, with match points and game points clearly labeled. In Rounds, group boards under the opposing team names and show the aggregate result. Current match cards receive players but no team names and do not expose the team-match grouping to the user.

Team roster numbering also needs correction: `assignedPlayers` filters the global player list and then numbers that order; pairing generation indexes the stored `team.playerIds` list. Assign players in an order different from the global roster and the displayed board numbers can disagree with pairing order. Display players by stored IDs and provide explicit board reordering. Team assignment/removal also remains available after completion, with no status guards in `assignPlayerToTeam` or `updateTeam`; define and enforce the intended editing policy.

Evidence: `MainActivity.kt:247`, `StandingsScreen.kt:121`, `RoundsScreen.kt:335`, `TeamsScreen.kt:124`, `TeamsScreen.kt:175`, `TournamentViewModel.kt:291`, `TournamentViewModel.kt:303`, `TournamentViewModel.kt:640`, `TournamentViewModel.kt:727`, `Tiebreaks.kt:244`.

### Standings, details, and export disagree about the selected round — high priority

Selecting an earlier round correctly passes `selectedRound` into the displayed standings calculation. Export instead calls a zero-argument callback and calculates all results without `upToRound`. Player detail history likewise uses all matches and requested byes through `tournament.currentRound`, while the score in its title comes from the selected-round row. A Round 1 table can therefore open later-round history and export later-round scores.

Use the selected round consistently for the table, player history, and export. Label the output “Standings through Round N,” include the round in the filename, and mark active-round data provisional. There is a further score risk in export: with no round boundary, `calculateScores` awards points for every requested bye, including future scheduled byes. Limit export to the chosen round even when it is the current one.

Evidence: `StandingsScreen.kt:121`, `StandingsScreen.kt:138`, `StandingsScreen.kt:177`, `StandingsScreen.kt:257`, `StandingsScreen.kt:263`, `TournamentViewModel.kt:640`, `PairingEngine.kt:34`.

### Backup restore can report success for an invalid file — high priority

“Import DB” opens a picker accepting any file type. Its only validation checks that the temporary file has at least 16 bytes; it then replaces the database and reports success. A text file can pass that check. The UI provides no explanation or confirmation that existing tournaments will be replaced, and the restart instruction is only a transient snackbar.

Validate the database structure and expected schema before replacement, preserve a recoverable copy, and explain the effect before the user commits. After success, show a persistent restart action or instruction. Backup/export success should also require an actual output stream: the nullable stream calls currently fall through to success when no stream is returned. Database backup consistency requires a separate engineering check; copying an open database file alone is not sufficient evidence that a backup contains every current write.

Evidence: `SettingsScreen.kt:53`, `SettingsScreen.kt:66`, `SettingsScreen.kt:80`, `SettingsScreen.kt:85`, `SettingsScreen.kt:94`, `SettingsScreen.kt:279`; nullable CSV output also appears in `StandingsScreen.kt:145`.

### Form validation and draft continuity need attention — medium priority

Requested-bye help says rounds must be within the tournament, but saving filters against `1..maxOf(totalRounds, 99)`. In a five-round tournament, round 99 is accepted. Invalid tokens are silently discarded. Validate against the known round limit, explain invalid entries, and show the saved schedule. When rounds are not calculated yet, explain that the schedule needs validation before start.

Creation silently substitutes scoring defaults for invalid numbers. Settings commits scoring after each parseable keystroke and rebuilds its input state whenever settings change, while giving no validation or saved-state feedback. Use local draft values with an explicit save or a clear blur-based commit and inline errors.

Creation, player-entry, import, and bye drafts use `remember` without saveable or view-model draft storage. They are at risk of disappearing on activity recreation, including rotation. Add draft persistence and make add/edit forms scrollable when the keyboard or large fonts reduce available space; the player dialogs currently use a plain Column.

Evidence: `PlayersScreen.kt:423`, `PlayersScreen.kt:447`, `PlayersScreen.kt:464`, `PlayersScreen.kt:476`, `HomeScreen.kt:222`, `HomeScreen.kt:354`, `SettingsScreen.kt:185`, `SettingsScreen.kt:189`.

### Round tools can act on a different round than the one displayed — medium priority

“Complete Round” and Rollback remain in the header when a historical round is selected, but act on `tournament.currentRound`. Editing state is a single `editingPastRound` boolean and is not reset when the user switches historical round chips, so unlocking one old round also makes another old round editable. Swap state is similarly independent of the selected round. The completion dialog always promises the next round, including the final round, and says results will be locked although later edits remain possible.

Label lifecycle actions with their target round or show them only while viewing that round. Reset correction and swap state when the round or tournament changes. Require a deliberate correction action per historical round and explain whether changes affect later pairings. Use a final-tournament review and completion message after the last round.

Evidence: `RoundsScreen.kt:57`, `RoundsScreen.kt:59`, `RoundsScreen.kt:130`, `RoundsScreen.kt:143`, `RoundsScreen.kt:215`, `RoundsScreen.kt:285`, `RoundsScreen.kt:340`, `RoundsScreen.kt:356`, `RoundsScreen.kt:383`.

### Match color identity needs accessible text — medium priority

In normal match cards, White and Black are conveyed by colored circles and vertical position. The `colorName` argument is only used in the swap-button description, so normal cards provide no explicit color label or matching accessibility description. Add visible White / Black labels or merged semantics identifying each player's color, and give result controls meaningful outcome labels within their board context. Generic repeated actions such as “Remove” and “Move up” should identify the player or tiebreak when necessary for accessibility navigation. Verify the final announcement order with TalkBack.

Evidence: `RoundsScreen.kt:576`, `RoundsScreen.kt:587`, `RoundsScreen.kt:659`, `RoundsScreen.kt:707`, `PlayersScreen.kt:336`, `TiebreakConfigPanel.kt:176`.

## Recommended delivery order and acceptance checks

1. **Reliable tournament operation:** align visible controls and lifecycle rules; make completed results read-only until explicit correction; repair team start readiness; render action feedback. Check setup, active, historical, and completed states for both individual and team events.
2. **Consistent results:** connect team standings; fix stored board order; apply the same selected round to standings, detail history, and exports. Verify with later results and future requested byes present.
3. **Clear mobile workflow:** one app bar, four tournament destinations, contextual dashboard action, separate search/add, reduced player-row actions, and simpler result entry with a pending filter.
4. **Forms and data confidence:** full-screen tournament setup, inline validation, persistent drafts, import preview, and validated recoverable backup restore.
5. **Visual and accessibility QA:** confirm readable semantic colors, noninteractive status badges, sufficient control spacing, text scaling, keyboard visibility, and explicit match color labels on a device.

These are acceptance criteria for subsequent implementation, not completed fixes or measured usability improvements.

## Suggested validation

After the first changes, walk through an individual Swiss tournament and a team tournament from empty state to completion. Include a past-round forfeit, correcting a result, and attempting actions in each tournament status. Verify that completing the last round produces a final summary rather than promising another round.

Assign team members in reverse roster order and compare displayed boards with generated pairings. View an earlier standings round, inspect a player, and export; all three should share the same round boundary. Include a future requested bye to catch premature scoring. Try an invalid backup and an out-of-range bye, and rotate the device with an unsaved form open.

Check a narrow phone layout, long tournament/player names, the keyboard open on forms, larger system fonts, light/dark/dynamic themes, and TalkBack. In a larger roster, confirm that pending-board filtering and searchable team assignment remain usable. Measure setup completion time, steps to record a result, and accidental edits during task-based user testing; these are proposed success measures, not measured improvements.

## Reference guidance

- [Android navigation bar guidance](https://developer.android.com/develop/ui/compose/components/navigation-bar): three to five destinations at the same level.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): minimum touch targets and automatic expansion behavior.
- [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum): 4.5:1 for normal text and 3:1 for qualifying large text. Used here as a readability benchmark, not a claim of a complete accessibility audit.
