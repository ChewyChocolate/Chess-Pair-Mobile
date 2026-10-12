# Reusable test data

In a debug build, open **Tournaments → Load test tournament** and choose 8 players,
9 players (byes), 32 players, or 4 teams of 4 players. Each load creates and opens
a separate Swiss tournament in setup with automatic round calculation. Go to
**Rounds** and start the tournament to test pairings and enter results.

The rosters and settings are repeatable; IDs are fresh on every load. Existing
tournaments are preserved. The action is hidden and creation is disabled in
release builds. Delete unwanted test tournaments using their Actions menu.

For manual import in any build, copy `players-8.csv` to your device and use
**Players → Import → Choose CSV file** while the tournament is in setup.
