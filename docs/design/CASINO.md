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
Wager wager = LbeEconomy.bank().stake(player, bet, "slot machine wager");
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
casino/slots/    SlotSymbol, SlotPaytable, SlotSpin   ← pure. Tested.
casino/block/    BlockSlotMachine, TileEntitySlotMachine, CasinoBlocks
client/gui/      GuiSlotMachine
network/         PacketSlotSpin (C→S), PacketSlotResult (S→C)
```

### The house edge is a number, not a feeling

Every game must be able to answer *"what fraction of money wagered comes back?"*
in closed form, and a test must pin it. `SlotPaytable.returnToPlayer()` is the
model: 0.8404, cross-checked against a million-spin simulation written
independently of it.

Above 1.0 the game prints money and any player who notices will farm it until the
server's economy is meaningless. That is not a bug you want to discover from a
balance graph three weeks later. It is a handful of integers, nothing about editing
them looks dangerous, and **that** is why the number is a test.

### The server decides; the client is told

`PacketSlotSpin` carries a position and an amount, and nothing else. No reels, no
payout, no balance. Everything that decides money is worked out server-side,
because anything a client sends is a number an attacker chose.

The GUI animates toward a result it was given. A client that tampers with the
animation changes what one person sees and not one cent of what they are paid.

`PacketSlotSpin` is LBE's **only** client → server message, which makes it the only
one that has to treat its contents as hostile — position is a real machine, the
chunk is loaded, the player is within reach, the amount is finite and within limits.

### GUI note that costs an afternoon

`BlockSlotMachine.onBlockActivated` **must not** guard on `world.isRemote`. The
screen is a plain `GuiScreen` with no `Container` behind it, so the client is the
side that opens the window. Guarding there is the classic 1.12.2 mistake that
produces a block which does nothing at all.

---

## Roadmap

The Discord bot at `Discord_bot/utils/*_game.py` already has all of these as pure
Python, complete with tested payout logic. That is the reference: port the maths,
compute the RTP, pin it in a test, then build a block and a screen around it.

| Game | Bot source | Shape in Minecraft | Notes |
|---|---|---|---|
| **Slots** | `slots_game.py` | ✅ **Done** — 1×2 cabinet | RTP 84.0% |
| Coin flip | `casino_views/coinflip_view.py` | Small block or item | Simplest possible second game; near 50/50, so the edge has to be deliberate |
| High-low | `highlow_game.py` | 1×2 cabinet | Streak-based; decide whether a streak can be banked |
| Wheel | — (`plinko_game.py` is close) | 1×3, wheel on the front | Wants a real animation; good TESR candidate |
| Roulette | `roulette_game.py` | Table, multi-block | Multi-bet UI is the hard part, not the wheel |
| Video poker | `video_poker_game.py` | 1×2 cabinet | Hold/draw needs two round trips — first game with real state between packets |
| Blackjack | `blackjack_game.py` | Table | Dealer logic is well-defined; splits and doubles complicate the wager |
| Craps | `craps_game.py` | Table, multi-block | Many simultaneous bets; needs a wager *set*, not one `Wager` |
| Xtreme Hold'em | `xtreme_holdem_game.py` | Table, multiplayer | Player-vs-player. Needs a pot, which escrow already models well |
| Baccarat, keno, war, mines | `baccarat_game.py`, `keno_game.py`, `war_game.py`, `mines_game.py` | Various | Straight ports |

Two things to settle before the multiplayer ones:

- **A pot needs several wagers resolved together.** Escrow handles this — several
  tickets, released to one winner — but `Wager` is currently one stake with one
  outcome. It will want a sibling type rather than a hack.
- **A table with seats needs state that survives a restart.** The slot machine
  deliberately has none. A hold'em table mid-hand does, and where that lives (tile
  entity NBT) should be decided once, for all table games, rather than per game.

The bot's `activity/` has 3D tables for several of these. Worth reading for layout
and proportion before modelling a table block — the geometry problem is already
solved there.
