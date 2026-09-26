# The casino

LBE's second half. Loot boxes give you something for an item; the casino gives you
something for money — which means it needs a currency, and LBE does not have one.

Read this before adding a game.

---

## The one rule: SUM is optional

The casino runs on **SUM**'s economy (the Server Utility Mod), which owns wallets,
banks and escrow. LBE depends on it **at compile time only**. On a pack without
SUM, the mod loads, the loot boxes work exactly as before, and the casino blocks
place and say they are closed.

That property is easy to break and expensive to lose, so it is enforced by
structure rather than by care:

```
casino/economy/
  CasinoBank.java        interface — no SUM types
  Wager.java             interface — no SUM types
  LbeEconomy.java        the gate — no SUM types, holds a ClosedBank until connected
  SumEconomyBridge.java  THE ONLY FILE IN LBE THAT NAMES A SUM TYPE
```

`LbeEconomy` mentions `SumEconomyBridge` in exactly one place — inside
`connect(boolean)`, reached only after `Loader.isModLoaded("sum")` returns true.
The JVM does not load a class until something reaches it, so on a pack without SUM
that class is never loaded and its missing supertypes never have to resolve.

**Widen that and LBE breaks on every pack without SUM.** It will still compile,
still pass the tests, and still boot. It will throw `NoClassDefFoundError` the
first time a player right-clicks a machine. If you add a game that needs money,
it talks to `CasinoBank` — never to SUM.

Verify both paths after touching this layer. Both are one command:

```bash
# with SUM: expects "[casino] Connected to SUM's economy with [...]"
cp <sum>/build/libs/uia-server-utility-mod-*-dev.jar run/mods/sum-dev.jar
cp ~/.gradle/caches/modules-2/files-2.1/zone.rong/mixinbooter/*/*/mixinbooter-*.jar run/mods/
bash .github/scripts/server-smoke-test.sh && grep -a "casino" server-smoke.log

# without SUM: expects "[casino] SUM is not installed" and zero NoClassDefFoundError
rm run/mods/sum-dev.jar run/mods/mixinbooter-*.jar
bash .github/scripts/server-smoke-test.sh && grep -a "casino" server-smoke.log
```

SUM's coremod needs MixinBooter, which is why it comes along for the ride.

### Why the connection retries

SUM publishes its economy during `FMLServerStartingEvent`, and LBE asks for it
during the same event. Which runs first is FML's decision, and **`after:sum` in
the `@Mod` annotation did not settle it in practice** — the annotation reached the
class file and LBE still sorted first, leaving the casino shut on a correctly
configured server.

So the connection does not depend on ordering at all:

- `onServerStarting` tries **quietly**, catching the common case with no log noise
  if it loses the race;
- `onServerStarted` tries again and **reports**, because by then every mod's
  starting handler has run and whatever it says is the truth;
- `LbeEconomy.bank()` retries every 5 seconds while closed, which also covers an
  operator authorizing LBE and running `/sum econ api reload` on a live server.

The `after:sum` declaration stays — it is still correct, and it helps where FML
honours it. It is just not load-bearing.

---

## Money: how a game takes a bet

Through `CasinoBank`, in three calls:

```java
Wager wager = LbeEconomy.bank().stake(player, bet, game.displayName() + " wager");
if (wager == null) {
    tell(player, LbeEconomy.bank().lastFailure());   // already player-safe
    return;
}
// ... decide the game ...
boolean settled = won ? wager.payOut(totalReturn) : wager.loseToHouse();
```

**Exactly one of `payOut`, `loseToHouse` or `cancel` must be called, once.** An
unsettled wager is real money held with nothing coming to claim it.

`payOut` takes the **total return**, not the profit: `payOut(5 * bet)` on a $10 bet
hands back $50. The bridge works out how much of that is the returned stake and
how much is new money.

### Why escrow rather than spend-then-credit

A game is two money movements with a gap: take the bet, then pay or keep it. As a
spend followed by a credit, a crash in that gap deletes the stake with no record it
existed. SUM's escrow makes the gap safe — the stake lives in a ticket in the world
save, and SUM refunds it automatically if LBE is removed or de-authorized. The gap
is milliseconds wide, but it is a gap in somebody's real balance.

`loseToHouse` maps to `escrowForfeit`, which **destroys** the money. That is the
only thing LBE does that removes currency from a server's economy, and it is what
stops the casino being a faucet.

---

## Where a game's logic goes

**Pure, in its own package, with no Minecraft types** — the same rule `rarity/`
follows, for the same reason: it makes the interesting part testable in
milliseconds without a server.

```
casino/               CasinoGame (the list), GameResult, CasinoOdds
casino/slots/         pure. Tested. One package per game, likewise:
casino/coinflip/  casino/war/  casino/highlow/
casino/roulette/  casino/plinko/  casino/keno/
casino/cards/         Card, Rank, Suit, Deck — shared by the card games
casino/block/         BlockCasinoMachine, TileEntityCasinoMachine, CasinoBlocks
client/gui/           GuiCasinoMachine — one screen, all seven games
network/              PacketCasinoPlay (C→S), PacketCasinoResult (S→C)
```

### The house edge is a number, not a feeling

Every game must be able to answer *"what fraction of money wagered comes back?"*
in closed form, and a test must pin it. `SlotPaytable.returnToPlayer()` is the
model: 0.8404, cross-checked against a million-spin simulation written
independently of it. `HouseEdgeTest` holds every game's figure and enforces a
80–100% band across all of them.

Above 1.0 the game prints money and any player who notices will farm it until the
server's economy is meaningless. That is not a bug you want to discover from a
balance graph three weeks later. It is a handful of integers, nothing about editing
them looks dangerous, and **that** is why the number is a test.

### The server decides; the client is told

`PacketCasinoPlay` carries a position, an amount and a few option numbers, and
nothing else. No reels, no cards, no payout, no balance. Everything that decides money is worked out server-side,
because anything a client sends is a number an attacker chose.

The GUI animates toward a result it was given. A client that tampers with the
animation changes what one person sees and not one cent of what they are paid.

`PacketCasinoPlay` is LBE's **only** client → server message, which makes it the
only one that has to treat its contents as hostile — position is a real machine,
the chunk is loaded, the player is within reach, the amount is finite and within
limits, and every array length is bounded before anything is allocated.

### GUI note that costs an afternoon

`BlockCasinoMachine.onBlockActivated` **must not** guard on `world.isRemote`. The
screen is a plain `GuiScreen` with no `Container` behind it, so the client is the
side that opens the window. Guarding there is the classic 1.12.2 mistake that
produces a block which does nothing at all.

---

## Presentation: what the rest of the floor sees and hears

None of this touches money, and nothing in it is read back by anything that does.

**The reveal is the moment, and everything waits for it.** The server settles a round the
instant the bet arrives, but the player's screen animates for `CasinoFanfare.REVEAL_TICKS` before
it shows them how it went. Anything that announces the result — a sound other players hear,
particles, the jackpot chat line — is queued in `CasinoEffects` and fired when that runs out.
Announcing at settlement tells the player the outcome before their own reels stop. (Mines' tile
turns are shown instantly, so they queue with no delay.)

**Who plays which sound.** The player's own sounds come from `client/gui/CasinoSounds`, in step
with the screen's animation, because only the screen knows when a reel lands. Everyone else
hears the win from the server, at the machine, with the player excluded so nothing plays twice.
`CasinoFanfare` classifies each round (loss, push, win, big win, jackpot) for both sides. It is a
shared constant rather than config because config is never synced, and a server-side threshold
could not reach the screen that plays the player's sting.

**Display state is a one-way copy.** When a round settles, the machine's tile entity keeps its
reveal and fanfare and sends them to every client watching the chunk, and
`TileEntityCasinoMachineRenderer` draws them: reels on a slot machine, the wheel on a roulette
table, cards on the card tables, and a lamp over the machine after a win. It is never saved (a
restarted server has nothing to show until someone plays) and never read by the server. It shows
only what the player was already sent, once the round is over. Mines' layout, for instance, is
never shown mid-round.

**The lamp is drawn, not lit.** Making the block emit light on a win would relight the area every
time, which is the most expensive thing a block can ask for.

**Also at the reveal:**
- **Redstone.** Both halves of the machine give a redstone and comparator signal of 6 for a win,
  11 for a big win and 15 for a jackpot, for one to five seconds. Builders do the rest.
- **The payout.** "WIN $94.00" floats over the machine, formatted by the server, which also
  decides whether to send it at all (`showPayouts`).
- **Advancements.** The win-dependent ones are granted then; a toast mid-spin would give the
  result away. They use vanilla's `impossible` trigger and are granted from `CasinoAdvancements`.
- **Jackpot loot box.** A legendary box, seeded like any other, pops out of the machine
  (`jackpotLootBox`). It is items rather than money, so no game's return changes.

**Idle machines attract.** A machine with no round to show runs a loop: reels drift, a plinko ball
drops, a lit tile wanders the mines board, keno flashes PLAY. The last round stays up for thirty
seconds before this starts.

**The progressive jackpot.** Slot machines share one pool (`casino/slots/ProgressiveJackpot`,
saved with the ledger). `progressiveShare` of each slot stake (default 1%, capped at 5%) feeds
it, and three sevens win it on top of the spin's payout; it then reseeds at `progressiveSeed`.
Every unit fed in is eventually paid back out, so the share is added straight to slots' return,
and `HouseEdgeTest` pins both the default (85.0%) and the cap. The paytable was deliberately not
retuned to absorb it. The pool is only fed and emptied **after** a payout succeeds, so a failed
settlement leaves it untouched. A progressive sign block shows the live pool.

**The loot box vendor** sells a tier of box for money: the one place the casino's currency buys
into the mod's other half. Sneak-right-click shows the next tier; right-click asks, and a second
right-click within three seconds buys. So a stray click never spends money, and
`PacketCasinoPlay` stays the only message a client can send the casino. The price
(`boxPrice_<tier>`) is staked through `CasinoBank` and forfeited, exactly like a lost bet, so it
passes through SUM's escrow and leaves the economy: a money sink with no new SUM surface. The
server formats the price the vendor displays, because config is never synced.

**The ledger and the leaderboard.** Every settled round is added to a per-player, per-game ledger
(`casino/stats/CasinoLedger`, pure; saved as `data/lbe_casino_stats.dat` by `CasinoStatsData`). It
records money that has already moved, and nothing that decides a game reads it. A big win joins
the public "recent big wins" list at the reveal, not at settlement, so a leaderboard cannot spoil a
spin. Only players who are ahead are ever listed as winners, and `leaderboardShowsNames` can hide
names. The leaderboard block and `/casino top` show exactly the same view.

**The decor set** (`casino/decor/`) has nothing to do with money: casino carpet in two
colourways, bar stools, velvet rope posts that rope themselves to neighbouring posts and block
walking through at fence height, and neon signs. A neon sign cycles through a fixed set of words on
right-click and takes its colour from dye, so it needs no text screen and no new packet. All of
them are generated by the same two scripts as the machines.

**Dye is the one thing a machine saves.** Right-clicking with dye sets a neon trim, which is stored
in the tile entity's NBT because it is decoration somebody chose, not game state.

---

## The games

Thirteen, all sharing one block class, one screen and one pair of packets. Adding
another is: pure logic in its own package, a constant in `CasinoGame`, a branch in
`TileEntityCasinoMachine.resolve`, a branch in `GuiCasinoMachine.drawReveal`, and a motif in
`tools/gen_casino_textures.py`. Nothing that moves money is touched.

| Game | Returns | Ported from | Rules changed? |
|---|---|---|---|
| Slots | 84.0% + 1% progressive = 85.0% | `slots_game.py` | no; the progressive is added on top |
| Roulette | 97.3% | `roulette_game.py` | no |
| Plinko | 91.4–97.6% | `plinko_game.py` | no |
| Coin flip | 97.0% | `!coinflip` | **yes** — paid 2×, which is exactly break-even |
| War | 97.2% | `war_game.py` | **yes** — priced the free push |
| High-low | 96.9–97.3% | `highlow_game.py` | **yes** — odds-weighted; see below |
| Keno | 91.3–92.5% | `keno_game.py` + `KENO_PAYTABLE` | **yes** — rescaled from 45–75% |
| Baccarat | 98.6 / 98.9 / 85.6% | `baccarat_game.py` | no — the 5% banker commission is its edge |
| Video poker | 70% naive → ~99.5% optimal | `video_poker_game.py` (9/6 Jacks or Better) | no |
| Mines | 96% at every stopping point | `mines_game.py` | no |
| Big wheel | 96.0% on every segment | new | shared rounds; counts x payouts are 48 for every segment |
| Pig race | 90.0% on every pig | new | shared rounds; fixed odds, not a pool |
| Blackjack | 99.43% perfect play, less otherwise | `blackjack_game.py` | rules pinned: S17, 3:2, DAS, split once, infinite deck, no insurance/surrender |

### Why four games needed repricing

The bot's currency is a score. Inflating it costs nobody anything, so a game returning 100% is
fine there and a game returning 150% is just generous. Against a SUM balance that also buys plots
and shop goods, neither is.

**High-low was the serious one.** The player saw the base card before choosing, and both directions
paid the same — so "call the side with more cards left" was always right and always available. On a
base of 2 that wins 48 times in 51. It returned **150.7%**: $50 a click at the default maximum bet,
limited only by clicking speed. Not an exploit anyone had to find; the obvious way to play.

Each direction now pays the inverse of its true chance, so **every call on every card returns the
same 97%**. That is roulette's property, and it is what makes a game a game rather than a lever:
there is no better side any more, only a safer one and a bolder one. Both multipliers are shown on
the buttons before the player commits.

Working that through exposed a second problem the even-money rules had hidden: a two or an ace has
one impossible call and one near-certainty, and honest pricing values that near-certainty *below*
the stake — calling higher on a two can only pay 0.97×. A hand whose only move is "win and still
lose three cents" is not a hand, so those eight cards are never dealt as a base.

`HouseEdgeTest` computes all of this in closed form and pins it, and enforces a band: **every game
must return between 80% and 100%**. A new game cannot join the casino without somebody deciding
what it costs to play.

## What has actually been played

Distinct from what has been computed, and worth keeping distinct: the returns in the table above
are closed-form arithmetic pinned by tests, which is a claim about the maths and not about the
software. As of **2026-08-12 all ten games have been played in a dev client against SUM's economy**,
with real stakes taken and settled — wins, losses and pushes — and the escrow log checked afterwards
for stranded money. None was found.

Verified by play, not only by test:

- money moves through the whole chain: screen → packet → tile entity → `CasinoBank` → SUM escrow;
- a loss forfeits (destroys) the stake and a win releases it plus a separate credit for the
  winnings, so the house's money and the player's own are never confused;
- mines' derived multiplier settles correctly — a cash-out mid-board paid 1.7x, which is not a
  figure in any table and could only come from the formula;
- the two-step games (high-low, video poker) and the open-ended one (mines) all settle their stake
  exactly once despite taking it several packets earlier.

**Still unverified, and the reason this is not a 1.0:** every one of those runs was against SUM's
*local* backend. Nothing here has ever talked to a remote OMCE economy, which is the claim SUM's own
plan flags as the one most likely to be quietly false. Until that happens, a release is a
pre-release.

## Deliberately not ported

**Scratch cards.** Excluded with Alex on 2026-08-11, not deferred — do not pick this up.

The bot's version is three games rather than one: eight tiers across matching, bingo and quick-rip,
backed by 384 lines of pre-determined grid generation, a symbol weight table, per-tier paytable
multipliers, wilds and an all-match bonus. Those tier tables *are* its economics, so a partial port
would mean inventing them, and a faithful one is a session's work for a game whose whole appeal is
the physical act of scratching a card — which does not survive the translation to a block and a
screen. The machines here are things you walk up to and play; a scratch card is a thing you buy and
take away, and LBE already has loot boxes for that.

**Video poker's double-or-nothing.** Deferred rather than excluded. It stakes a payout that has
already settled, which `Wager` cannot express — that is a wager-model change, not a game.

## Shared rounds: the big wheel and the pig race

Everyone at the machine shares one round. The first bet opens a 20-second window; anyone can add
bets until it closes; then one spin or race settles every bet, each through the bank like any
other, and each bettor gets their own result while the room watches the wheel or the race board.
`TileEntitySharedTable` holds the round in memory and refunds it if the machine unloads. A player
who logs off stays in the round: the bank pays a winner only while online and otherwise leaves
the stake held, so nothing is lost.

**Every bet is the same bet**, as everywhere else here. The wheel's segment counts and payouts are
chosen together so every segment returns 96%. The race uses **fixed odds** priced to return 90% on
every pig, rather than a pari-mutuel pool, because a pool only works with a crowd: a lone winner
would get back less than they staked.

The race's look is generated on each client from the round's seed, so everyone sees the same race;
only the winner comes from the server, and the renderer guarantees it crosses first.

## Several stakes in one round: `WagerSet`

Blackjack's splits and doubles put more money on the table mid-round. Each extra stake is an
ordinary `Wager`, taken through `CasinoBank` exactly like a bet, so SUM sees nothing new and
`SumEconomyBridge` is unchanged. `WagerSet` (pure) tracks which hand each stake rides on and
settles every one exactly once by its own hand's result; an abandoned round refunds them all. If a
player cannot cover a double or split, the action is refused and the hand carries on as it was.

Blackjack's return is computed in closed form (`BlackjackMath`: the optimal-strategy value of
every hand against the dealer's peek-conditioned outcomes, infinite deck) and pinned by
`HouseEdgeTest`; `BlackjackTest` plays the real engine a million rounds with that same strategy and
checks it lands on the figure. Mid-round the client is sent only the dealer's upcard.

## Still to come

| Game | Bot source | What it needs first |
|---|---|---|
| Craps | `craps_game.py` | Many simultaneous bets across several rolls. `WagerSet` now covers the money side |
| Xtreme Hold'em | `xtreme_holdem_game.py` | Player-vs-player. Needs a pot, and a table whose state survives a restart |

Two things to settle before the multiplayer tables:

- **A pot is several wagers resolved together.** SUM's escrow models it well — several tickets
  released to one winner — but `Wager` is one stake with one outcome. It wants a sibling type, not
  a hack.
- **A table mid-hand has state that must survive a restart.** Every machine here is deliberately
  stateless except the games that take the stake up front — high-low's dealt card, video poker's
  hand, mines' board — whose state is held in memory and refunded if the player leaves or the chunk
  unloads. That is fine for a round somebody is sitting at. A hold'em table cannot do it. Where that state lives (tile entity NBT)
  should be decided once, for all table games.

The bot's `activity/` has 3D tables for several of these. Worth reading for layout
and proportion before modelling a table block — the geometry problem is already
solved there.
