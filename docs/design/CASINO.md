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

## The games

Ten, all sharing one block class, one tile entity, one screen and one pair of packets. Adding
another is: pure logic in its own package, a constant in `CasinoGame`, a branch in
`TileEntityCasinoMachine.resolve`, a branch in `GuiCasinoMachine.drawReveal`, and a motif in
`tools/gen_casino_textures.py`. Nothing that moves money is touched.

| Game | Returns | Ported from | Rules changed? |
|---|---|---|---|
| Slots | 84.0% | `slots_game.py` | no |
| Roulette | 97.3% | `roulette_game.py` | no |
| Plinko | 91.4–97.6% | `plinko_game.py` | no |
| Coin flip | 97.0% | `!coinflip` | **yes** — paid 2×, which is exactly break-even |
| War | 97.2% | `war_game.py` | **yes** — priced the free push |
| High-low | 96.9–97.3% | `highlow_game.py` | **yes** — odds-weighted; see below |
| Keno | 91.3–92.5% | `keno_game.py` + `KENO_PAYTABLE` | **yes** — rescaled from 45–75% |
| Baccarat | 98.6 / 98.9 / 85.6% | `baccarat_game.py` | no — the 5% banker commission is its edge |
| Video poker | 70% naive → ~99.5% optimal | `video_poker_game.py` (9/6 Jacks or Better) | no |
| Mines | 96% at every stopping point | `mines_game.py` | no |

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

## Still to come

| Game | Bot source | What it needs first |
|---|---|---|
| Blackjack | `blackjack_game.py` | Hit/stand/double/split. Splits turn one wager into several, which `Wager` does not model yet |
| Craps | `craps_game.py` | Many simultaneous bets across several rolls — needs a wager *set* |
| Xtreme Hold'em | `xtreme_holdem_game.py` | Player-vs-player. Needs a pot, and a table whose state survives a restart |
| Scratch cards | `scratch_game.py` | The big one left: 8 tiers across 3 game types, with 384 lines of pre-determined grid generation behind them. Its own session |
| Craps | `craps_game.py` | Many simultaneous bets across several rolls — needs a wager *set* |

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
