# Pairing repairs — 13 October 2026

The baseline audit is in `FIDE_PAIRING_AUDIT.md`. These changes address the demonstrated defects in the individual engines and the separate team paths. Recorded pairings and results are preserved; past rounds are not retroactively regenerated.

## Individual Swiss

`FideDutchEngine.kt` now searches score brackets using S1/S2 candidate generation, transpositions, resident exchanges and moved-down players. It compares bye, downfloat, colour and float-history criteria in priority order instead of combining weighted penalties. It checks completion in the following brackets before accepting a candidate. The nearby-rating preference and large-field greedy fallback were removed.

Repeat played opponents and same absolute colour preferences for non-topscorers are hard restrictions. The final-round topscorer exception is calculated from real scores. The engine searches for a legal bye assignment; previous pairing byes and full-point forfeit winners are ineligible. It never saves partial results or silently relaxes these restrictions.

Colour allocation includes strong and mild preferences, previous opposite colours, ranking, TPN parity and a persisted initial colour drawn by lot. Initial player numbers are assigned by rating, FIDE title and name. The selected initial colour is visible in tournament setup and can be set to the result of the arbiter's separate draw.

Baku now adds virtual points to pairing scores for the specified opening rounds while leaving standings points unchanged. Unsupported LIM acceleration and club avoidance are removed from the creation controls; legacy unsupported configurations produce an actionable pairing error rather than pretending to use a FIDE method.

`PairingValidation.kt` applies availability, participant uniqueness, opponent, bye and colour checks to generated rounds, player swaps, manual pairings and round completion. Manual/forced pairings remain identified as arbiter overrides: basic legality alone does not make them the unique prescribed Dutch output.

Rule reference: [FIDE Dutch](https://handbook.fide.com/chapter/C0403202602), [basic Swiss rules](https://handbook.fide.com/chapter/C0401202507), [Baku acceleration](https://handbook.fide.com/chapter/C0407202602).

## Round Robin

`bergerPairs` generates the official Berger tables. Numbers are drawn at tournament start and stored on the players/teams. The roster stays fixed after numbering, so requested absences and withdrawals affect the scheduled board through a forfeit instead of rebuilding everyone else's opponents. Club avoidance cannot remove required encounters.

Odd-field rests have their own `REST` result and score zero. Withdrawals after fewer than half the scheduled games cause those results to be excluded from individual standings, with the original games retained in storage. A player who completed at least half retains their standings results. The player list shows assigned pairing numbers for review.

References: [Berger tables](https://handbook.fide.com/chapter/C05Annex1), [competition regulations, article 6.6](https://handbook.fide.com/files/handbook/Competition_Rules.pdf).

## Knockout

The first round now creates a reproducible seeded bracket from participant order. Every adjacent pair of previous boards feeds a fixed next-round board, independent of database row order. Byes carry ordinary bracket board numbers. Later rounds refuse unfinished or drawn encounters, and unavailable winners do not advance. A completed final produces no artificial extra round.

The existing product still records one deciding result per knockout encounter. Multi-game matches, separate playoff game records and event-specific colour/draw procedures require a separately defined event format. This change does not claim compliance with a particular World Cup or other FIDE knockout event regulation.

## Team events and scoring

The separate `TeamPairingEngine.kt` uses stored team numbers and searches legal Swiss brackets with lower-score upfloaters. It applies the default Type A team colour preferences, rejects repeat opponents and repeated team byes, and uses draw-equivalent team-bye points. Missing boards are forfeits, not pairing byes. Unavailable board players are handled through forfeits; wholly unavailable teams are excluded from Swiss/knockout pairing. New team events require equal roster board counts, making default bye game points consistent.

Team Round Robin uses Berger tables and rests; Team Knockout uses fixed bracket paths and blocks unresolved ties. The default Swiss team order is alphabetical, with match points primary and game points secondary. Reference: [FIDE team system](https://handbook.fide.com/chapter/SwissTeamPairingSystem202602).

Scores and floats are calculated chronologically. Future requested byes do not affect current pairings, a recorded result overrides a requested absence, and unplayed forfeits do not enter played-opponent or colour history. Progressive tiebreaks now sum cumulative scores after each round. Other tiebreak implementations have not received a full FIDE conformity audit.

## Persistence and validation

Database version 4 adds the initial colour and team pairing number through a non-destructive 3→4 migration. Backup restore accepts versions 2–4 and uses both migrations. A migration instrumented test preserves v3 tournament/player/team/match records; the backup instrumented test now checks the new fields too.

Validation:

- **93 JVM unit tests passed**, including regression fixtures for Dutch openings through 64 players, multi-round Swiss legality, official Berger schedules and complete opponent/colour coverage for 3–24 players, bye/forfeit/absence scoring, fixed knockout paths, and team bye/rematch behaviour.
- Application, JVM test and Android instrumented-test Kotlin sources compile against the cached SDK/dependencies using the standalone runner.
- `git diff --check` is clean.
- The normal Gradle build remains blocked by `Unable to establish loopback connection`, including an offline retry with IPv4 and no persistent daemon. APK packaging, KSP generation and on-device instrumented tests were not run. The source compilation fallback does not replace those checks.

Reproduce:

```powershell
# Pure pairing/workflow regression tests
& .\docs\pairing-audit\Run-PairingAudit.ps1

# Compile app and test sources against cached dependencies, then run all JVM tests
& .\docs\pairing-audit\Run-PairingAudit.ps1 -AndroidSourcesOnly
```

## Practical limits

Searches have explicit work limits and stop with an arbiter-facing error if they cannot establish a result within those limits. They do not substitute an illegal or unverified fallback. Existing unsupported club/LIM configurations must be reviewed in setup before pairing; previously generated illegal rounds remain part of the historical record.

The new implementation has not been compared exhaustively with an independent 2026 FIDE pairing checker and is not FIDE-endorsed. The tests establish the repaired behaviours and absolute invariants; they are not certification of every Dutch/team priority interaction or every event regulation.
