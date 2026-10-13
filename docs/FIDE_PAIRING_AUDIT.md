# FIDE pairing audit

This is the **baseline review before the 13 October repairs**. Its source locations and observed failures describe the original implementation. See [FIDE_PAIRING_REPAIR.md](FIDE_PAIRING_REPAIR.md) for the current changes, validation and remaining limits.

Reviewed 12 October 2026. Scope: the three tournament types exposed by this application, their individual pairing engines, workflow guards, and the separate team helpers. Application code was not changed.

| System | Assessment |
| --- | --- |
| Individual Swiss / advertised FIDE Dutch | **Does not comply with the current FIDE Dutch rules.** Several absolute constraints and the prescribed pairing process are missing. |
| Individual Round Robin | **Does not follow the FIDE Berger schedule.** Opponent coverage works for a fixed roster with club avoidance disabled, but colours are incorrect; optional settings can also break opponent coverage. |
| Individual Knockout | **Basic elimination works; FIDE compliance is not established.** The applicable event regulations must define the draw, seeding, match format, colours and tie resolution. Random seeding alone is not a universal FIDE violation. |
| Team variants | **Cannot inherit the individual verdicts.** They use different code; team Swiss also has identifiable departures from C.04.6. |

The Swiss comparison uses the rules effective from **1 February 2026**. This is a source and behaviour review, not FIDE endorsement or certification.

## Swiss: confirmed failures

1. **Wrong Dutch first-round opponents.** With four players ranked 1–4, the engine returns 1–2 and 3–4. The initial homogeneous bracket must split into S1/S2; with no constraints, its candidate is 1–3 and 2–4. A 20-player example also fails to pair the top half against the bottom half. The implementation rewards nearby rating/seed values instead. See `FideDutchEngine.kt:285–382, 454–459`. Its bracket helper is unused by the public engine. [Dutch articles 3.2–3.6](https://handbook.fide.com/chapter/C0403202602).

2. **Rematches are explicitly allowed as a fallback.** `generateFideDutch` retries with `allowRematch=true`. A concrete three-player history has p1 defeating p2 and p3 in earlier rounds while the other player is absent. The engine gives p3 the bye and repeats p1–p2; giving the eligible p1 the bye would allow p2–p3. Selecting the bye before testing completion creates an avoidable illegal pairing. See `FideDutchEngine.kt:79–113`. Repeat played opponents are an absolute prohibition; impossible pairings must go to the Chief Arbiter. [Basic rule 2](https://handbook.fide.com/chapter/C0401202507), [Dutch articles 1.9.3 and 2.1–2.2](https://handbook.fide.com/chapter/C0403202602).

3. **Absolute colour preferences are soft penalties.** Two players both due Black after WW remain eligible to meet, so one can receive WWW. `totalRounds` is unused, and the engine has no final-round topscorer classification to limit the permitted exception. See `FideDutchEngine.kt:435–446`. [Dutch articles 1.8 and 2.1.3](https://handbook.fide.com/chapter/C0403202602).

4. **Colour allocation ignores mild preferences.** With histories p1=BW and p2=WB, both balances are zero. The engine assigns p1 White and p2 Black, refusing both preferences even though the opposite assignment satisfies both. No drawn initial-colour setting exists; the TPN parity rule is also absent. Strong preferences are incorrectly conditional on the last colour. See `FideDutchEngine.kt:390–420` and `Models.kt`. [Dutch articles 1.7 and 5](https://handbook.fide.com/chapter/C0403202602).

5. **Bye eligibility is incomplete.** Only `MatchResult.BYE` enters `data.byes`; a full-point forfeit winner remains eligible. If every available player already had a bye, the fallback selects one anyway, giving a second pairing-allocated bye. See `PairingEngine.kt:91–114` and `FideDutchEngine.kt:82`. [Basic rule 4](https://handbook.fide.com/chapter/C0401202507).

6. **Unplayed forfeits affect played-opponent and colour history.** These histories are updated before the result type is inspected. A white forfeit records a White game and a played opponent. This can generate false colour restrictions and falsely prohibit a later meeting. See `PairingEngine.kt:52–60`. [General handling articles 3.4–3.5](https://handbook.fide.com/chapter/GeneralHandlingRulesForSwissTournaments202602).

7. **Future requested byes inflate current pairing scores.** `generateFideDutch` calls `calculateScores` without a round cutoff. Consequently, a requested round-7 bye adds 0.5 before round 1. Requested byes also do not contribute to the separate `roundScores` used to infer floats. See `PairingEngine.kt:34–44` and `FideDutchEngine.kt:40`. This is a chronological scoring defect regardless of whether the event authorises half-point requested byes.

8. **The Dutch priority hierarchy and float history are not implemented.** The public engine maximises a weighted sum of score/rating/club/colour penalties rather than comparing the prescribed criteria in order. `floatHistory` is collected but never consulted by the engine; bye downfloats are not recorded. Above 16 available players, it switches to greedy matching without global completion checks. See `FideDutchEngine.kt:285–382` and `PairingEngine.kt:62–77`. [Dutch articles 1.4, 2–4](https://handbook.fide.com/chapter/C0403202602).

9. **BAKU and LIM currently do not change first-round opponents.** Their list rearrangement is immediately undone by the global pairer's score/seed sort; both produced identical opponent sets to NONE. Acceleration is applied only in round 1 and adds no virtual points. Baku requires scheduled virtual points over multiple rounds. LIM here is only a list rearrangement, not an implementation of the separate FIDE Lim Swiss system. See `PairingEngine.kt:368–423` and `FideDutchEngine.kt:75–76, 294–297`. [FIDE acceleration articles 1.2–1.5](https://handbook.fide.com/chapter/C0407202602), [FIDE Lim system](https://handbook.fide.com/chapter/C040403202602).

10. **Initial ranking and manual overrides need validation.** Normal add/import paths leave `pairingNumber=0`. The fallback sorts by rating without title/name tie resolution. Forced pairs are inserted before any opponent or colour legality check; `forcedPairingIssue` only checks active/distinct players and duplicate assignments. Player swaps and manual matches also bypass pairing legality. See `TournamentViewModel.kt:234–264, 535–572, 707`, `TournamentWorkflow.kt:63–72`, `FideDutchEngine.kt:53–66`. [General handling articles 1.4–1.5, 2.2–2.3 and 4.4](https://handbook.fide.com/chapter/GeneralHandlingRulesForSwissTournaments202602).

## Round Robin: confirmed failures

The general competition regulations call for Berger tables. This engine uses a different rotation and assigns colours by swapping every pair on even rounds. [Competition article 5.1](https://handbook.fide.com/files/handbook/Competition_Rules.pdf), [official Berger tables](https://handbook.fide.com/chapter/C05Annex1).

| Four-player example | Application | Official Berger table |
| --- | --- | --- |
| Round 1 | 1–4, 2–3 | 1–4, 2–3 |
| Round 2 | 3–1, 2–4 | 4–3, 1–2 |
| Round 3 | 1–2, 3–4 | 2–4, 3–1 |
| Player 4 colours | BBB | BWB |

Here the first ID denotes White. Even where a reordered schedule could be acceptable, the colour defect is independent of round ordering.

- **Fixed roster, club avoidance off:** all six unordered pairs occur once in the four-player test. This part works.
- **Club avoidance on:** with clubs A,A,B,B, opponents repeat across the three rounds. With four players from the same club, round 1 contains four win-point byes and no games. Local swaps have no tournament-wide history check. See `PairingEngine.kt:207–244`.
- **Requested absence or withdrawal:** rebuilding the roster each round changes the rotation. With p2 requesting round 2 off, p1–p4 repeats. Preserve the original schedule and handle the unavailable player's scheduled game instead. See `PairingEngine.kt:148–164`; withdrawals also require the standings treatment in [competition article 6.6](https://handbook.fide.com/files/handbook/Competition_Rules.pdf).
- **Inactive players:** the filter omits `active`; the engine still pairs a player marked inactive. This is an application eligibility inconsistency.
- **Odd-player empty slot:** the same full-win `BYE` representation used for Swiss is awarded to the round-robin rest player. With a stable odd roster this adds equally to everyone's score, but it misrepresents a scheduled rest as a win and should not be used as a played game. See `PairingEngine.kt:172–181, 107–114`.

## Knockout: works in part, requires event rules

The first round creates a power-of-two elimination field with the expected number of byes. Subsequent rounds advance recorded winners and forfeit winners. The focused example advances p1 and p4 correctly.

The normal application workflow also refuses to complete an individual knockout round containing a draw. Do not mistake the engine's silent draw omission for an unguarded normal workflow: `roundCompletionIssue` blocks it. See `TournamentWorkflow.kt:39–42`.

Remaining limitations:

- The initial draw is randomly reshuffled every time the function is called; there is no stored seed or persistent bracket-slot model.
- Later pairings are derived from the order of previous match records. They are not defined by explicit bracket paths.
- A winner marked withdrawn still advances and receives another pairing; the focused check confirms this.
- There is no match-level model for multiple classical games or tie-break games; the workflow asks the operator to replace the game result with a deciding result.

These are implementation limitations to resolve against the selected event's regulations. FIDE's Swiss no-rematch, single-bye and scoregroup rules should not be imposed on knockout. A random draw or multiple first-round byes can be valid. No specific FIDE knockout event was selected, so a universal compliant/non-compliant claim would be unsupported.

## Separate team paths

Source inspection shows `TournamentViewModel.kt:774–863` uses a separate team Swiss algorithm. It permits repeat team opponents when its search fails, permits a second bye when all teams already had one, has no team TPN field, and never evaluates team colour or float history. Missing opponent boards are represented by the same `BYE` result as an actual team bye, so they can wrongly mark a team as already byed.

`calculateTeamScores` awards a team bye 2 match points and one game point per listed player. Current C.04.6 defaults to draw-equivalent match/game points unless the event regulations specify otherwise; the application does not expose a team-bye scoring policy. Team colour preferences are weaker than individual preferences and are not absolute prohibitions. [Team articles 1.1, 1.4, 1.7, 2.1 and 3.4](https://handbook.fide.com/chapter/SwissTeamPairingSystem202602).

Team Round Robin repeats the individual rotation/colour design. Team Knockout has a tied-score fallback advancing the team with White on board one, but the normal workflow blocks tied team matches first. These team helpers were inspected, not executed by the standalone audit.

## Validation and recommended order of work

The standalone runner compiles the actual `Models.kt`, `PairingEngine.kt`, `FideDutchEngine.kt`, and existing `PairingEngineTest.kt`, without changing them. **All 22 existing pairing tests passed.** The additional diagnostics reproduced the failures listed as confirmed above. The existing rematch test does not assert opponent uniqueness, and the club test permits byes; passing these tests is not evidence of FIDE compliance.

The full Gradle suite could not run in this environment: the first attempt hit a cache lock access restriction; an escalated retry failed to establish a loopback connection. The standalone Kotlin/JUnit checks completed successfully.

Reproduce from the repository root with the cached Kotlin 2.0.21/JUnit dependencies and Java:

```powershell
& .\docs\pairing-audit\Run-PairingAudit.ps1
```

The original diagnostic harness has been replaced by regression tests in `FidePairingRulesTest.kt`. The runner now verifies the repaired implementation; baseline observations above are retained as the audit record.

Recommended priority:

1. Correct chronological scores, unplayed-game history and bye eligibility; add round-level legality validation covering forced/manual pairings.
2. Replace the Swiss heuristic with a faithful Dutch implementation and compare complete rounds against an independent checker/reference implementation. Keep hard constraints absolute and report impossibility to the arbiter.
3. Generate and preserve actual Berger schedules; remove club avoidance that eliminates required encounters.
4. Implement Baku virtual scores and explicit initial-colour/TPN policies.
5. Define event-specific knockout rules and persist bracket slots; separately implement the team system and scoring policy.
