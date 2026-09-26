# Unfinished items

What is still open in LBE, and why. Each entry says what it is, why it was left, and when it is
worth coming back to. Excluded and rejected ideas are not here; they are in
[`design/CASINO.md`](design/CASINO.md) under "Deliberately not ported", so nobody picks them up
by accident.

## Not yet seen in play

These are covered by unit tests, but have not been reached in a dev client. A second pass on
2026-09-26 played blackjack splits (with a double after), the leaderboard with a winner on both
columns, the progressive rising from slot play, the refund on logging out mid-hand, comp
redemption, and self-exclusion at a machine and the vendor, then lifted by an operator. What is
left:

| Item | Why it was not reached | How to check it |
|---|---|---|
| Raising a personal limit after 24 hours | The wait is real time | Raise a limit, come back a day later, confirm it took effect |
| Jackpot loot box, and the jackpot and nerves-of-steel advancements | Jackpots are about 1 in 14,000 spins | Force a jackpot in a dev build, or wait for one on a live server. The big-win advancement has been seen |
| Sounds, by ear | Checked by reading the code, not listened to | Play each game with sound on |

## Deferred work

| Item | Why deferred | Revisit when |
|---|---|---|
| Xtreme Hold'em and other player-vs-player tables | Needs a pot paid to another player, and a table whose mid-hand state survives a restart. See "Still to come" in `CASINO.md` | When someone wants PvP tables enough to design both, once, for all table games |
| Video poker double-or-nothing | Stakes an already-settled payout, which a single `Wager` cannot express | Now that `WagerSet` exists, check whether it can; if so, this is a game feature, not a model change |
| Custom `.ogg` sounds | Needs an Ogg Vorbis encoder or licensed audio; the generators are stdlib-only | When there is audio worth commissioning, or an encoder is acceptable as a tool dependency |
| Shaped table models from the bot's `activity/` tables | The current shaped models were built without them | Before any further model work on the tables |
