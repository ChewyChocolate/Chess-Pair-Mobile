# Visual UI/UX review

Reviewed October 12, 2026, against the current working tree after the workflow fixes.

These findings describe the layout before the subsequent visual implementation. See [implementation notes](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/docs/VISUAL_UI_IMPLEMENTATION.md>) for the applied changes and validation status.

## Scope and confidence

This review covers typography, visual hierarchy, density, surface treatment, color roles, and control presentation. It is based on the Compose layouts and theme definitions. No Android device was connected when checked, and no current screen captures were available. The implementation details below are verified in source; the resulting visual effects are design assessments that still need rendered validation. This is not a claim that clipping or contrast failures were observed on a device.

The app already uses a reasonably consistent 16 dp outer margin and familiar Material components. The main opportunity is to give names, scores, and the next action stronger emphasis while reducing competing borders, chips, and explanatory text.

## Findings

### 1. Heading hierarchy is compressed — high priority

Screen titles such as Overview, Players, Rounds, and Standings use `titleLarge`, defined as 17 sp. Card titles commonly use 15 sp; the dashboard's featured heading is 18 sp, while its description is 16 sp. Weight helps distinguish these roles, but the size differences are small. The larger 24 sp headline style exists and is not used for these screen headings.

**Visual consequence:** screen titles and important names can feel like ordinary labels. On the dashboard, a paragraph can have almost as much presence as its heading.

**Direction:** use a consistent 22–24 sp screen title, 17–18 sp section/card title, and 14–16 sp body. Reserve 11–12 sp for supporting metadata rather than information a tournament operator repeatedly reads. Keep the tournament name in the app bar distinct from the destination heading.

Sources: [Typography](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/theme/Type.kt:14>), [Dashboard](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/DashboardScreen.kt:49>).

### 2. Rounds spends too much visual space above and inside each board — high priority

The screen stacks round chips, a round/status row, a possible swap action, result count, format guidance, an outlined search field, and filter chips before the list. A match card then contains a board circle, two color labels, two color circles, names, rating lines, small score badges, result chips, and a result summary/actions row.

**Visual consequence:** controls and metadata compete with the board information. The combination is likely to leave relatively little room for boards, especially with knockout guidance or larger text. The actual number of visible boards must be checked on a device.

**Direction:** combine round selection and progress into one compact header; place supplementary guidance behind an information control or a dismissible notice. Make player names and the selected result the strongest elements in each board. Keep ratings visually subordinate and put the board number in a compact leading position. Avoid repeating both color words and decorative color indicators unless both are needed for clarity. Preserve comfortable result-selection targets when reducing padding.

Sources: [Rounds header](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/RoundsScreen.kt:264>), [Board card](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/RoundsScreen.kt:609>).

### 3. Standings gives player names too little room — high priority

The table reserves 36 dp for rank, 120 dp for name, 48 dp for points, and 24 dp for outer padding. This consumes 228 dp before the horizontally scrollable tiebreak area. At 320 dp width, only 92 dp remains for that area. Names wrap without a line limit, so long names can produce uneven row heights. Tiebreak values use 12 sp text. Withdrawn rows are faded as a whole to 55% opacity, including their scores.

**Visual consequence:** the leaderboard can look cramped and irregular. Small numerical data is harder to scan, and fading a whole row weakens information that remains relevant.

**Direction:** prioritize rank, flexible player name, and points in the default view. Move the full tiebreak table to an expanded view, or provide a clearer horizontal-scroll affordance. Use consistent name wrapping and align numerical columns consistently. Mark withdrawal with a label/icon and subdued name treatment while keeping numerical results readable.

Sources: [Table header](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/StandingsScreen.kt:245>), [Standing row](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/StandingsScreen.kt:404>).

### 4. Tournament status has the same visual treatment as metadata — medium priority

Setup, Active, and Completed all use the same neutral `StatusBadge`. Format and Team labels also use that component. `TournamentCard` calculates a status-specific color but does not apply it.

**Visual consequence:** the state of an event is difficult to identify at a glance; it blends into its descriptive tags.

**Direction:** give status a dedicated component with an explicit label plus a distinct icon and restrained semantic tint. Keep format and event-type tags neutral. Establish whether the card border communicates the currently selected tournament; do not rely on that border to communicate event status.

Sources: [Tournament card](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/HomeScreen.kt:181>), [Status badge](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/components/StatusBadge.kt:10>).

### 5. Overview looks like a stack of actions rather than a compact summary — medium priority

There is a clear featured primary action, followed by separate lines for round progress, a progress bar, player/team count, section, and time control. Below that are up to four full-width outlined buttons, each receiving the same visual treatment. Several destinations are already available in the bottom navigation.

**Visual consequence:** the page is vertically long and repetitive. Statistics are embedded in prose rather than forming an easily scanned summary.

**Direction:** retain the featured next action. Group players, teams, and round progress into compact labeled statistics, with section/time control as secondary metadata. Present secondary destinations as compact rows or links, reserving a full-width button for an important setup action. Show the progress bar when progress is meaningful rather than automatically displaying an empty bar during setup.

Source: [Dashboard content](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/DashboardScreen.kt:49>).

### 6. The same chip shape serves several different interaction roles — medium priority

Filter chips are used for Players/Teams navigation, Teams/Individuals view selection, individual/team event choice, round selection, and All boards/Pending filtering. Each has a similar selected appearance despite performing a different kind of change.

**Visual consequence:** the interface lacks a clear visual distinction between changing a section, choosing a setting, and filtering existing content.

**Direction:** use tabs for Players/Teams sections, segmented controls for mutually exclusive modes/settings, and chips for list filters. Give round selection a recognizable, consistent presentation in both Rounds and Standings.

Sources: [Player section selector](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/PlayersScreen.kt:84>), [Standings controls](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/StandingsScreen.kt:193>).

### 7. Team cards duplicate information and carry too much repeated chrome — medium priority

A team header shows its player count underneath the name and again in a badge. Each card includes a divider, the full roster, individual action menus when editable, and a full-width assignment button. The count badge also changes tint at four players, which can visually imply a readiness threshold.

**Visual consequence:** repeated count labels, borders, and controls add noise as the number of teams grows. Color appears to communicate more meaning than the badge text explains.

**Direction:** keep a single player count in the header. Use a compact roster layout or expandable roster for larger events. Present assignment as one clear team action. Keep the count neutral unless the badge explicitly describes a real readiness condition.

Source: [Team card](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/TeamsScreen.kt:157>).

### 8. Advanced settings accumulate nested surfaces — lower priority

Settings wraps each section in a card. The tiebreak editor adds its own card and a surface for each criterion, plus separate action rows, a divider, suggestion chips, and reset control. Tournament setup has many outlined fields and explanatory lines with similar emphasis.

**Visual consequence:** the configuration area can look heavy and administrative, with containment competing against the actual settings.

**Direction:** use one surface per section, simple dividers between criteria, and stronger section headings. Retain the current advanced disclosure and readable action targets. Summarize defaults when collapsed and expose detailed descriptions near the specific setting that needs them.

Sources: [Settings sections](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/screens/SettingsScreen.kt:256>), [Tiebreak rows](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/components/TiebreakConfigPanel.kt:134>).

### 9. The intended color identity is not guaranteed — design decision

The source defines a brown/green light palette and gold-accented dark palette, but dynamic color is enabled by default on Android 12 and newer. MainActivity does not override it. Devices using dynamic color therefore derive the main palette from their wallpaper instead of these custom schemes.

**Visual consequence:** screenshots on different devices may have very different accents. This is a legitimate personalization choice, not inherently a defect; it matters if the goal is a recognizable chess-themed visual identity.

**Direction:** decide whether personalized Material color or a fixed brand palette is the intended default. If both are offered, make the preference explicit. Define status colors separately from decorative brand accents, and validate both light and dark appearance on actual screens.

Sources: [Theme selection](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/ui/theme/Theme.kt:14>), [Theme call](<C:/Users/Shalie Manaois/OneDrive/Desktop/Chess-Pair-Mobile/app/src/main/java/com/example/chesspairmobile/MainActivity.kt:49>).

## Suggested order

1. Establish the typography hierarchy and shared heading/control styles.
2. Simplify the Rounds header and match card.
3. Rebalance Standings around names and points.
4. Differentiate statuses and navigation/filter controls.
5. Refine Overview, team cards, and configuration surfaces.

## Rendered checks still needed

- Light and dark theme, including dynamic-color palettes if retained.
- Narrow and typical phone widths, larger system font, and long player/tournament names.
- Busy Rounds states: long names, knockout guidance, corrections, team labels, and a selected result.
- Standings with many tiebreaks, withdrawn players, and tied scores.
- Settings/setup with the keyboard visible and advanced options expanded.
- Actual contrast, clipping, inset spacing, and number of visible list items.

No application code was changed for this visual review.
