package com.micatechnologies.minecraft.lbe.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.highlow.HighLowGame;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotPaytable;
import com.micatechnologies.minecraft.lbe.casino.war.WarGame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What every game in the casino actually does to a server's money supply.
 *
 * <p>One place, one number per game, all computed in closed form. A casino is a pump between the
 * player base and nothing, and the direction it pumps is a property of arithmetic that nobody can
 * see by playing — a 104% game and a 96% game feel identical for the first thousand spins and end
 * up in opposite places.
 *
 * <p><b>Three of these games return 100% or more, and that is not an accident of this port.</b> They
 * are faithful to the Discord bot, where the currency is a score and inflation costs nobody
 * anything. Against a balance that also buys plots and shop goods it is a different proposition, so
 * the numbers are asserted here as they really are rather than asserted to be safe. Every one of
 * them is a deliberate, recorded fact instead of a surprise.
 */
class HouseEdgeTest {

    /** Anything at or above this returns players more than they stake, over any timescale. */
    private static final double BREAK_EVEN = 1.0;

    // ---------------------------------------------------------------------------------------------
    // Games with a real house edge
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("slots keep 16.0%")
    void slots() {
        assertEquals(0.8403710858105805, SlotPaytable.returnToPlayer(), 1.0e-12);
        assertTrue(SlotPaytable.returnToPlayer() < BREAK_EVEN);
    }

    @Test
    @DisplayName("roulette keeps 2.70% on every bet, which is what single-zero means")
    void roulette() {
        // The point is not the value, it is that it is the SAME for all eight bet types. A table
        // where one bet paid better would be one every player found within a day.
        for (RouletteGame.BetType type : RouletteGame.BetType.values()) {
            assertEquals(36.0 / 37.0, RouletteGame.returnToPlayer(type), 1.0e-12, type.name());
            assertTrue(RouletteGame.returnToPlayer(type) < BREAK_EVEN, type.name());
        }
    }

    @Test
    @DisplayName("plinko keeps between 2.4% and 8.6%, depending on risk level")
    void plinko() {
        // The bot's docstring claims ~97.6 / ~93.5 / ~91.4. Computed exactly here from the binomial
        // landing distribution: 97.58 / 93.52 / 91.41, which confirms the tables were copied
        // correctly and that its stated figures were rounded rather than wrong.
        assertEquals(0.9757812500, PlinkoGame.returnToPlayer(PlinkoGame.Risk.LOW), 1.0e-9);
        assertEquals(0.9351562500, PlinkoGame.returnToPlayer(PlinkoGame.Risk.MEDIUM), 1.0e-9);
        assertEquals(0.9140625000, PlinkoGame.returnToPlayer(PlinkoGame.Risk.HIGH), 1.0e-9);
        for (PlinkoGame.Risk risk : PlinkoGame.Risk.values()) {
            assertTrue(PlinkoGame.returnToPlayer(risk) < BREAK_EVEN, risk.name());
        }
    }

    @Test
    @DisplayName("plinko's landing slots are a proper distribution")
    void plinkoDistribution() {
        double total = 0.0;
        for (int slot = 0; slot <= PlinkoGame.ROWS; slot++) {
            total += PlinkoGame.slotProbability(slot);
        }
        assertEquals(1.0, total, 1.0e-12);
        // The centre carries more than a quarter of all drops, which is what makes the 22x edges
        // affordable. If this stops being true the payout tables need revisiting.
        assertEquals(70.0 / 256.0, PlinkoGame.slotProbability(4), 1.0e-12);
    }

    @Test
    @DisplayName("every keno pick count keeps a lot — 25% to 55% of turnover")
    void keno() {
        // Each pick count is its own bet with its own return. A single mistyped digit in one row
        // would make that row the only one anybody plays, so all ten are pinned.
        //
        // These are HARSH — 45% to 75%, against 70-80% for keno in a real casino, and the ten-pick
        // row keeps more than half of everything staked on it. That is the bot's paytable faithfully
        // ported, and it is the opposite failure to high-low's: nobody is going to farm it, but a
        // player who works out that ten picks is the worst bet on the board will be annoyed. Worth
        // revisiting with the same care as the break-even games, in the other direction.
        double[] expected = {
            0.7500000000,   // 1 pick
            0.5411392405,   // 2
            0.6243914314,   // 3
            0.6126784608,   // 4
            0.5621751666,   // 5
            0.6501409982,   // 6
            0.6541374295,   // 7
            0.5946777553,   // 8
            0.6207160069,   // 9
            0.4511890677,   // 10
        };
        for (int picks = 1; picks <= KenoGame.MAX_PICKS; picks++) {
            double rtp = KenoGame.returnToPlayer(picks);
            assertEquals(expected[picks - 1], rtp, 1.0e-9, picks + " picks");
            assertTrue(rtp < BREAK_EVEN, picks + " picks returns " + rtp);
        }
    }

    @Test
    @DisplayName("keno's match probabilities are a proper distribution")
    void kenoDistribution() {
        for (int picks = 1; picks <= KenoGame.MAX_PICKS; picks++) {
            double total = 0.0;
            for (int matches = 0; matches <= picks; matches++) {
                total += KenoGame.matchProbability(picks, matches);
            }
            assertEquals(1.0, total, 1.0e-9, picks + " picks");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Games that keep nothing — faithful to the bot, and a hazard on a live economy
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("coin flip returns exactly 100% — the house makes nothing, ever")
    void coinFlipHasNoEdge() {
        // A fair coin paying 2-for-1. Not an approximation and not a rounding artefact: it is
        // exactly break-even, so the casino cannot fund itself from this table and cannot lose to
        // it either. Pure variance.
        assertEquals(1.0, CoinFlipGame.returnToPlayer(), 0.0,
            "coin flip is break-even by construction; see the class note if this ever changes");
    }

    @Test
    @DisplayName("war returns exactly 100%, because a tie pushes instead of going to war")
    void warHasNoEdge() {
        // The real game resolves a tie by doubling the stake and dealing again, and that single
        // rule is its entire ~2.9% edge. The bot drops it, so the tie cancels out of the arithmetic
        // completely and the game is exactly fair whatever the tie rate happens to be.
        assertEquals(1.0, WarGame.returnToPlayer(), 1.0e-12);
        assertEquals(3.0 / 51.0, WarGame.tieProbability(), 1.0e-12);
    }

    @Test
    @DisplayName("HIGH-LOW PAYS 150% TO ANY PLAYER WHO LOOKS AT THE CARD")
    void highLowPaysPlayersToPlay() {
        // The single most important number in this file.
        //
        // The player sees the base card before choosing, and both directions pay the same, so
        // "call the side with more cards left" is always correct and always available. On a base of
        // 2 that wins 48 times in 51. It is not an exploit that has to be discovered; it is the
        // obvious way to play.
        //
        // 1.5068 means every dollar staked returns a dollar fifty. At the default $100 maximum bet
        // that is $50 a click, limited only by how fast somebody can press a button.
        double optimal = HighLowGame.returnToPlayerWithOptimalPlay();
        assertEquals(1.5067873303167421, optimal, 1.0e-12);
        assertTrue(optimal > 1.5,
            "high-low returns " + optimal + " to a player using the obvious strategy");

        // And the floor: even choosing at random costs the house nothing.
        assertEquals(1.0, HighLowGame.returnToPlayerWithRandomPlay(), 1.0e-12,
            "there is no way to play high-low badly enough to lose money over time");
    }

    @Test
    @DisplayName("the three break-even games are exactly the three known ones")
    void nothingElseSlippedThroughAtBreakEven() {
        // A canary. If a fourth game ever joins this list, that is a decision somebody should be
        // making on purpose rather than discovering from a balance graph.
        assertTrue(CoinFlipGame.returnToPlayer() >= BREAK_EVEN);
        assertTrue(WarGame.returnToPlayer() >= BREAK_EVEN);
        assertTrue(HighLowGame.returnToPlayerWithOptimalPlay() >= BREAK_EVEN);

        assertTrue(SlotPaytable.returnToPlayer() < BREAK_EVEN);
        assertTrue(RouletteGame.returnToPlayer(RouletteGame.BetType.STRAIGHT) < BREAK_EVEN);
        assertTrue(PlinkoGame.returnToPlayer(PlinkoGame.Risk.LOW) < BREAK_EVEN);
        assertTrue(KenoGame.returnToPlayer(1) < BREAK_EVEN);
    }
}
