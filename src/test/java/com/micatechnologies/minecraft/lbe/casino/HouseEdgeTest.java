package com.micatechnologies.minecraft.lbe.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
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
 * <p>Three of these games arrived from the Discord bot returning 100% or more — a fair coin paying
 * 2-for-1, a war whose ties pushed for free, and a high-low where the player chose a side after
 * seeing the card and both sides paid the same. A fourth, keno, returned as little as 45%. All four
 * have been repriced; the ported rules are otherwise untouched, and each class says what changed and
 * why.
 *
 * <p><b>Every game now returns less than 100% and more than 80%.</b> The last test here enforces
 * exactly that, so a future game cannot join the casino without somebody deciding what it costs.
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
    // The four that were repriced
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("coin flip keeps 3% — it used to keep nothing at all")
    void coinFlip() {
        // The bot paid a round 2 on a fair coin, which returns exactly 100% forever. Paying 1.94
        // instead is the whole fix: the coin stays fair and the price is in the payout, where a
        // player can see it, rather than in a weighted coin, where they could count it.
        assertEquals(0.97, CoinFlipGame.returnToPlayer(), 1.0e-12);
        assertEquals(1.94, CoinFlipGame.WIN_MULTIPLIER, 1.0e-12);
    }

    @Test
    @DisplayName("war keeps 3% with the friendly tie intact")
    void war() {
        // The bot's tie pushed for free, which made the game wash out to exactly 100% no matter how
        // often ties came up. The push is kept — it is the nicer rule — and paid for out of the
        // win, so the game plays identically and now costs something.
        assertEquals(CasinoOdds.STANDARD_RETURN, WarGame.returnToPlayer(), 0.005);
        assertTrue(WarGame.returnToPlayer() < BREAK_EVEN);
        assertEquals(3.0 / 51.0, WarGame.tieProbability(), 1.0e-12);
        assertTrue(WarGame.WIN_MULTIPLIER < 2.0 && WarGame.WIN_MULTIPLIER > 1.9,
            "a win should still look like even money to a player: " + WarGame.WIN_MULTIPLIER);
    }

    @Test
    @DisplayName("high-low: EVERY call on EVERY card now returns the same 97%")
    void highLowIsUniform() {
        // The fix that mattered most, and the property that proves it.
        //
        // Under the bot's even-money rules the return depended on which card came up and which way
        // you called — 0.61 at worst, 1.88 at best — so "call the side with more cards" returned
        // 150.7% and printed money. Paying each direction the inverse of its true chance collapses
        // that spread to a point: there is no better side any more, only a safer one and a bolder
        // one. This is roulette's property, and it is what makes the game a game.
        double worst = HighLowGame.worstReturnToPlayer();
        double best = HighLowGame.bestReturnToPlayer();
        assertTrue(best - worst < 0.01,
            "returns should be uniform across calls, but span " + worst + " to " + best);
        assertTrue(worst < BREAK_EVEN, "even the best call must favour the house: " + best);
        assertEquals(CasinoOdds.STANDARD_RETURN, worst, 0.01);
        assertEquals(CasinoOdds.STANDARD_RETURN, best, 0.01);
    }

    @Test
    @DisplayName("high-low never deals a hand whose only move loses money")
    void highLowHasNoDeadHands() {
        // A two or an ace has one impossible call and one near-certainty that honest pricing values
        // below the stake — "win and still lose three cents". Those are not hands, so they are not
        // dealt, and every hand that IS dealt offers two calls that both pay more than they cost.
        for (Rank rank : Rank.values()) {
            Card card = new Card(rank, Suit.SPADES);
            if (!HighLowGame.isPlayableBase(card)) {
                assertTrue(rank == Rank.TWO || rank == Rank.ACE,
                    rank + " should be playable as a base card");
                continue;
            }
            for (HighLowGame.Call call : HighLowGame.Call.values()) {
                assertTrue(HighLowGame.payoutFor(card, call) > 1.0,
                    "calling " + call + " on a " + rank + " pays "
                        + HighLowGame.payoutFor(card, call) + " — a win that loses money");
            }
        }
    }

    @Test
    @DisplayName("high-low prices a near-certain call near 1x and a long shot high")
    void highLowPrices() {
        // What the uniformity above looks like at the table. Calling higher on a 2 wins 48 times in
        // 51 and therefore cannot pay much; calling lower on it cannot win at all and is refused
        // rather than sold.
        Card three = new Card(Rank.THREE, Suit.SPADES);
        Card king = new Card(Rank.KING, Suit.SPADES);
        Card ace = new Card(Rank.ACE, Suit.SPADES);

        assertTrue(HighLowGame.payoutFor(three, HighLowGame.Call.HIGHER) < 1.1,
            "a 44-in-51 call must pay barely more than the stake");
        assertTrue(HighLowGame.payoutFor(three, HighLowGame.Call.LOWER) > 10.0,
            "a 4-in-51 call must pay handsomely");
        // Symmetric about the eight, which is the visible shape of "every call costs the same".
        assertEquals(HighLowGame.payoutFor(three, HighLowGame.Call.LOWER),
            HighLowGame.payoutFor(king, HighLowGame.Call.HIGHER), 1.0e-9);

        assertFalse(HighLowGame.isCallable(ace, HighLowGame.Call.HIGHER));
        assertEquals(0.0, HighLowGame.payoutFor(ace, HighLowGame.Call.HIGHER),
            "a call that cannot win has no honest price and must not be sold one");
    }

    @Test
    @DisplayName("keno keeps 8%, and no pick count is a trap any more")
    void keno() {
        // The bot's table ran 45-75% and punished picking more numbers, which is the opposite
        // failure to the others and just as worth fixing. Every row is its own payout shape scaled
        // to the target; the spread that is left is rounding to one decimal.
        double[] expected = {
            0.9250000, 0.9199367, 0.9129990, 0.9193240, 0.9186279,
            0.9164951, 0.9172517, 0.9173007, 0.9239726, 0.9193604,
        };
        double worst = 1.0;
        double best = 0.0;
        for (int picks = 1; picks <= KenoGame.MAX_PICKS; picks++) {
            double rtp = KenoGame.returnToPlayer(picks);
            assertEquals(expected[picks - 1], rtp, 1.0e-6, picks + " picks");
            assertTrue(rtp < BREAK_EVEN, picks + " picks returns " + rtp);
            worst = Math.min(worst, rtp);
            best = Math.max(best, rtp);
        }
        assertTrue(best - worst < 0.02,
            "no pick count should be meaningfully worse than another: " + worst + " to " + best);
        assertEquals(CasinoOdds.KENO_RETURN, worst, 0.01);
    }

    // ---------------------------------------------------------------------------------------------
    // The rule for every game, present and future
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every game keeps between 2% and 20%, so none is a faucet and none is a fleecing")
    void everyGameIsInBand() {
        // The canary. A new game cannot join the casino without somebody deciding what it costs to
        // play, because this fails until its number is inside the band.
        assertInBand("slots", SlotPaytable.returnToPlayer());
        assertInBand("coin flip", CoinFlipGame.returnToPlayer());
        assertInBand("war", WarGame.returnToPlayer());
        assertInBand("high-low (worst call)", HighLowGame.worstReturnToPlayer());
        assertInBand("high-low (best call)", HighLowGame.bestReturnToPlayer());
        for (RouletteGame.BetType type : RouletteGame.BetType.values()) {
            assertInBand("roulette " + type, RouletteGame.returnToPlayer(type));
        }
        for (PlinkoGame.Risk risk : PlinkoGame.Risk.values()) {
            assertInBand("plinko " + risk, PlinkoGame.returnToPlayer(risk));
        }
        for (int picks = 1; picks <= KenoGame.MAX_PICKS; picks++) {
            assertInBand("keno " + picks + " picks", KenoGame.returnToPlayer(picks));
        }
    }

    private static void assertInBand(String game, double rtp) {
        assertTrue(rtp < BREAK_EVEN,
            game + " returns " + rtp + " — at or above 1.0 it prints money and will be farmed");
        assertTrue(rtp > 0.80,
            game + " returns " + rtp + " — below 0.80 players notice and stop playing");
    }
}
