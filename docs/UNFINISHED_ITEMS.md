# Unfinished items

What is still open in LBE, and why. Each entry says what it is, why it was left, and when it is
worth coming back to. Excluded and rejected ideas are not here; they are in
[`design/CASINO.md`](design/CASINO.md) under "Deliberately not ported", so nobody picks them up
by accident.

## Not yet seen in play

These are covered by unit tests, and the code around them was played on 2026-09-26, but the paths
themselves were not reached in the in-game pass. Worth watching for on a live server, or checking
directly in the next dev-client session.

| Item | Why it was not reached | How to check it |
|---|---|---|
| Blackjack splits | No pair was dealt during the pass | Play until a pair comes up, split, and check both hands settle and the ledger records both stakes |
| Leaderboard with a winner listed | Only players who are ahead are listed, and no test player was | Win a round with `leaderboardShowsNames` on and look at the board |
| Progressive pool rising from slot play | Checked by test and on the sign, not across real spins | Note the sign, play a few slot spins, confirm it rose by `progressiveShare` of each stake |
| Refund of an open hand on logout | Resume-on-reopen was played; logging out mid-hand was not | Open a blackjack or craps round, log out, log back in, check the balance and the server log's refund line |
| Raising a personal limit after 24 hours | The wait is real time | Raise a limit, come back a day later, confirm it took effect |
| An active self-exclusion | Excluding a test player blocks the rest of the pass | Exclude a spare account for a day, confirm every machine and the vendor refuse, then `/casino lift` |
| Redeeming comps | Test play does not earn the 2,000 points the cheapest box costs | Stake about $2,000 on a test account (the default rate is already the 0.5% cap), then `/casino redeem` |
| Jackpot loot box and the big-win, jackpot and nerves-of-steel advancements | Jackpots are about 1 in 14,000 spins | Force a jackpot in a dev build, or wait for one on a live server |
| Sounds, by ear | Checked by reading the code, not listened to | Play each game with sound on |

## Deferred work

| Item | Why deferred | Revisit when |
|---|---|---|
| Xtreme Hold'em and other player-vs-player tables | Needs a pot paid to another player, and a table whose mid-hand state survives a restart. See "Still to come" in `CASINO.md` | When someone wants PvP tables enough to design both, once, for all table games |
| Video poker double-or-nothing | Stakes an already-settled payout, which a single `Wager` cannot express | Now that `WagerSet` exists, check whether it can; if so, this is a game feature, not a model change |
| Custom `.ogg` sounds | Needs an Ogg Vorbis encoder or licensed audio; the generators are stdlib-only | When there is audio worth commissioning, or an encoder is acceptable as a tool dependency |
| Shaped table models from the bot's `activity/` tables | The current shaped models were built without them | Before any further model work on the tables |
