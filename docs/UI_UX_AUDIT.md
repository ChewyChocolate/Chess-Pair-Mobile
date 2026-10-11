# ChessPair UI/UX review

Reviewed October 11, 2026.

## Scope and assessment

This is a source-based review of all seven Compose screens, the navigation shell, theme, shared tiebreak controls, and the view-model behavior behind important actions. No app screenshots or running Android session were available. Layout crowding, keyboard behavior, TalkBack output, and device-specific rendering still need verification on a device. No application code was changed.

ChessPair has a useful foundation: Material 3 controls, consistent spacing in many places, light/dark/system themes, searchable players, empty states, repeated player entry, round history, and standings drill-down. The main opportunity is to organize these features around the arbiter's immediate task. Setup, managing a live round, and reviewing final results need different primary actions and different editing rules.

## Recommended priorities

| Priority | Area | Current presentation | Recommended change |
| --- | --- | --- | --- |
| First | Action reliability | Some enabled controls silently fail, and completed results remain editable | Align controls with allowed operations; show outcomes and explicit correction mode |
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

## Suggested validation

After the first changes, walk through an individual Swiss tournament and a team tournament from empty state to completion. Include a past-round forfeit, correcting a result, and attempting actions in each tournament status. Verify that completing the last round produces a final summary rather than promising another round.

Check a narrow phone layout, long tournament/player names, the keyboard open on forms, larger system fonts, light/dark/dynamic themes, and TalkBack. In a larger roster, confirm that pending-board filtering and searchable team assignment remain usable. Measure setup completion time, steps to record a result, and accidental edits during task-based user testing; these are proposed success measures, not measured improvements.

## Reference guidance

- [Android navigation bar guidance](https://developer.android.com/develop/ui/compose/components/navigation-bar): three to five destinations at the same level.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): minimum touch targets and automatic expansion behavior.
- [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum): 4.5:1 for normal text and 3:1 for qualifying large text. Used here as a readability benchmark, not a claim of a complete accessibility audit.
