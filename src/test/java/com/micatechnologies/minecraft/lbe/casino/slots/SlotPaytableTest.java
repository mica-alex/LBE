package com.micatechnologies.minecraft.lbe.casino.slots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The paytable, and the one number that decides whether this machine is safe to put on a server.
 *
 * <p>A slot machine is a pump between the player base and nothing. Which direction it pumps is
 * decided entirely by {@link SlotPaytable#returnToPlayer()}: below 1.0 it slowly drains money out of
 * the economy, above 1.0 it prints money forever and any player who works that out will farm it. The
 * difference is a handful of integers in {@link SlotSymbol}, and nothing about editing them looks
 * dangerous, which is why the number is pinned here rather than left to be noticed in production.
 */
class SlotPaytableTest {

    /** Tight enough that changing any weight or multiplier trips it. */
    private static final double EPSILON = 1.0e-9;

    @Test
    @DisplayName("the machine keeps a house edge — it must never pay out more than it takes")
    void houseEdgeIsPositive() {
        double rtp = SlotPaytable.returnToPlayer();
        assertTrue(rtp < 1.0,
            "return to player is " + rtp + "; at or above 1.0 this machine mints money and a "
                + "player who notices will farm it until the server's economy is meaningless");
        assertTrue(rtp > 0.0, "a machine that never pays anything is not a game");
    }

    @Test
    @DisplayName("the house edge is the intended 16%, to nine decimal places")
    void houseEdgeIsPinned() {
        // Computed in closed form from the weights in SlotSymbol, and cross-checked against the
        // million-spin simulation below — the two are written independently, so agreement means
        // the arithmetic describes the machine SlotSpin actually implements.
        //
        // If this fails you changed a weight or a multiplier. That is allowed, but it is a change
        // to how fast the machine drains the server's economy: work out the new figure, decide it
        // is the one you want, and then update it here deliberately.
        assertEquals(0.8403710858105805, SlotPaytable.returnToPlayer(), 1.0e-12,
            "return to player moved — see the comment above before touching this figure");
        assertEquals(0.1596289141894195, SlotPaytable.houseEdge(), 1.0e-12);
    }

    @Test
    @DisplayName("the edge stays in the band players tolerate and servers survive")
    void houseEdgeIsReasonable() {
        // Real slot machines run 85–95% RTP. Much lower and it feels like theft and nobody plays;
        // much higher and the drain is too slow to offset the payouts a lucky streak causes.
        double rtp = SlotPaytable.returnToPlayer();
        assertTrue(rtp >= 0.75 && rtp <= 0.95, "return to player " + rtp + " is outside 75–95%");
    }

    @Test
    @DisplayName("a simulated million spins agrees with the closed form")
    void simulationAgreesWithTheory() {
        // The point is not the estimate — it is that the arithmetic in returnToPlayer() describes
        // the machine SlotSpin actually implements. The two are written independently and could
        // easily disagree; a divergence here means one of them is wrong about the game.
        Random random = new Random(20260811L);
        long spins = 1_000_000L;
        long wagered = 0;
        long returned = 0;
        for (long i = 0; i < spins; i++) {
            wagered += 100;
            returned += 100L * SlotSpin.roll(random).multiplier();
        }
        double observed = (double) returned / wagered;
        assertEquals(SlotPaytable.returnToPlayer(), observed, 0.02,
            "the simulated return " + observed + " disagrees with the computed "
                + SlotPaytable.returnToPlayer() + "; the paytable and the roller describe "
                + "different machines");
    }

    @Test
    @DisplayName("probabilities sum to one, so no reel stop is unreachable or double-counted")
    void probabilitiesSumToOne() {
        double total = 0.0;
        for (SlotSymbol symbol : SlotSymbol.values()) {
            total += SlotPaytable.probability(symbol);
        }
        assertEquals(1.0, total, EPSILON);
    }

    @Test
    @DisplayName("rarer symbols pay more, or the paytable makes no sense to a player")
    void rarerSymbolsPayMore() {
        SlotSymbol[] all = SlotSymbol.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i].weight() < all[i - 1].weight(),
                all[i] + " is not rarer than " + all[i - 1]);
            assertTrue(all[i].tripleMultiplier() > all[i - 1].tripleMultiplier(),
                all[i] + " does not pay more than " + all[i - 1]);
        }
    }

    @Test
    @DisplayName("three of a kind pays that symbol's multiplier")
    void triplePays() {
        for (SlotSymbol symbol : SlotSymbol.values()) {
            SlotSymbol[] reels = {symbol, symbol, symbol};
            assertEquals(symbol.tripleMultiplier(), SlotPaytable.multiplierFor(reels), symbol.name());
        }
    }

    @Test
    @DisplayName("exactly two cherries pays the consolation, three pays the triple instead")
    void twoCherriesPayConsolation() {
        assertEquals(SlotPaytable.TWO_CHERRY_MULTIPLIER, SlotPaytable.multiplierFor(
            new SlotSymbol[] {SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.BELL}));
        assertEquals(SlotPaytable.TWO_CHERRY_MULTIPLIER, SlotPaytable.multiplierFor(
            new SlotSymbol[] {SlotSymbol.BELL, SlotSymbol.CHERRY, SlotSymbol.CHERRY}));
        assertEquals(SlotSymbol.CHERRY.tripleMultiplier(), SlotPaytable.multiplierFor(
            new SlotSymbol[] {SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY}),
            "three cherries is a triple, not a doubled consolation");
    }

    @Test
    @DisplayName("two of anything else pays nothing")
    void twoOfAnythingElsePaysNothing() {
        for (SlotSymbol symbol : SlotSymbol.values()) {
            if (symbol == SlotSymbol.CHERRY) {
                continue;
            }
            assertEquals(0, SlotPaytable.multiplierFor(
                new SlotSymbol[] {symbol, symbol, SlotSymbol.LEMON == symbol
                    ? SlotSymbol.BELL : SlotSymbol.LEMON}), symbol.name());
        }
    }

    @Test
    @DisplayName("a malformed spin is refused rather than silently read as a loss")
    void wrongReelCountThrows() {
        // Silently returning 0 here would eat a paid-for bet and look exactly like bad luck.
        assertThrows(IllegalArgumentException.class,
            () -> SlotPaytable.multiplierFor(new SlotSymbol[] {SlotSymbol.SEVEN}));
        assertThrows(IllegalArgumentException.class, () -> SlotPaytable.multiplierFor(null));
    }

    @Test
    @DisplayName("a payout never rounds a fraction of a cent into existence")
    void payoutRoundsDown() {
        // $0.01 at 5x is exactly $0.05, but $0.033 at 5x is $0.165 — which must pay $0.16, not
        // $0.17. Rounding up would mint a fraction of a cent on a large share of all wins.
        SlotSpin cherries = SlotSpin.of(
            new SlotSymbol[] {SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY});
        assertEquals(0.16, cherries.payoutFor(0.033), 1.0e-9);
        assertEquals(0.05, cherries.payoutFor(0.01), 1.0e-9);
    }

    @Test
    @DisplayName("a loss pays exactly nothing")
    void lossPaysNothing() {
        SlotSpin loss = SlotSpin.of(
            new SlotSymbol[] {SlotSymbol.LEMON, SlotSymbol.BELL, SlotSymbol.STAR});
        assertFalse(loss.isWin());
        assertEquals(0.0, loss.payoutFor(1000.0), 1.0e-9);
    }

    @Test
    @DisplayName("the jackpot is rare enough to stay special")
    void jackpotIsRare() {
        double odds = SlotPaytable.jackpotProbability();
        assertTrue(odds < 0.0001, "three sevens comes up every " + Math.round(1.0 / odds)
            + " spins, which is too often for a jackpot");
        SlotSpin jackpot = SlotSpin.of(
            new SlotSymbol[] {SlotSymbol.SEVEN, SlotSymbol.SEVEN, SlotSymbol.SEVEN});
        assertTrue(jackpot.isJackpot());
        assertTrue(jackpot.isTriple());
    }

    @Test
    @DisplayName("a spin reports a win exactly when it pays")
    void winFlagMatchesMultiplier() {
        Random random = new Random(7L);
        for (int i = 0; i < 10_000; i++) {
            SlotSpin spin = SlotSpin.roll(random);
            assertEquals(spin.multiplier() > 0, spin.isWin());
            assertEquals(SlotPaytable.multiplierFor(spin.reels()), spin.multiplier());
        }
    }

    @Test
    @DisplayName("the reels a spin reports cannot be edited from outside")
    void reelsAreDefensivelyCopied() {
        SlotSpin spin = SlotSpin.of(
            new SlotSymbol[] {SlotSymbol.SEVEN, SlotSymbol.SEVEN, SlotSymbol.SEVEN});
        SlotSymbol[] stolen = spin.reels();
        stolen[0] = SlotSymbol.LEMON;
        assertEquals(SlotSymbol.SEVEN, spin.reel(0), "a caller must not be able to rewrite a result");
        assertEquals(SlotSymbol.SEVEN.tripleMultiplier(), spin.multiplier());
    }

    @Test
    @DisplayName("every symbol actually comes up")
    void everySymbolIsReachable() {
        // A weight small enough to be unreachable would be a symbol on the reel strip that no
        // player can ever hit, which quietly changes the paytable from what is printed on it.
        Random random = new Random(99L);
        boolean[] seen = new boolean[SlotSymbol.values().length];
        for (int i = 0; i < 200_000; i++) {
            seen[SlotSpin.roll(random).reel(0).index()] = true;
        }
        for (SlotSymbol symbol : SlotSymbol.values()) {
            assertTrue(seen[symbol.index()], symbol + " never came up in 200,000 spins");
        }
    }

    @Test
    @DisplayName("reel-strip indexing wraps in both directions")
    void indexWraps() {
        // The client animation walks the strip by index and runs past both ends of it.
        int count = SlotSymbol.values().length;
        assertEquals(SlotSymbol.CHERRY, SlotSymbol.byIndex(0));
        assertEquals(SlotSymbol.CHERRY, SlotSymbol.byIndex(count));
        assertEquals(SlotSymbol.CHERRY, SlotSymbol.byIndex(-count));
        assertEquals(SlotSymbol.SEVEN, SlotSymbol.byIndex(-1));
    }
}
