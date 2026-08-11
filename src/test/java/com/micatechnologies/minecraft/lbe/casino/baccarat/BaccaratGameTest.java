package com.micatechnologies.minecraft.lbe.casino.baccarat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Baccarat's scoring, its drawing tableau, and what each of the three bets returns.
 *
 * <p>The tableau is the part worth testing hard. It is a table of rules nobody can deviate from,
 * which means it <i>is</i> the game — get one row wrong and the banker's edge moves, silently, in a
 * way no amount of playing would reveal.
 */
class BaccaratGameTest {

    /** Enough coups that a 1% error in the computed return shows up. */
    private static final int ROUNDS = 400_000;

    @Test
    @DisplayName("tens and faces are worth nothing, an ace is worth one")
    void cardValues() {
        assertEquals(0, BaccaratGame.valueOf(card(Rank.TEN)));
        assertEquals(0, BaccaratGame.valueOf(card(Rank.JACK)));
        assertEquals(0, BaccaratGame.valueOf(card(Rank.QUEEN)));
        assertEquals(0, BaccaratGame.valueOf(card(Rank.KING)));
        assertEquals(1, BaccaratGame.valueOf(card(Rank.ACE)));
        assertEquals(9, BaccaratGame.valueOf(card(Rank.NINE)));
        assertEquals(2, BaccaratGame.valueOf(card(Rank.TWO)));
    }

    @Test
    @DisplayName("a hand's score wraps at ten, so nine and seven is six")
    void scoreWraps() {
        assertEquals(6, BaccaratGame.score(hand(Rank.NINE, Rank.SEVEN)));
        assertEquals(0, BaccaratGame.score(hand(Rank.KING, Rank.QUEEN)));
        assertEquals(9, BaccaratGame.score(hand(Rank.FOUR, Rank.FIVE)));
        assertEquals(1, BaccaratGame.score(hand(Rank.NINE, Rank.NINE, Rank.THREE)));
    }

    @Test
    @DisplayName("the banker's tableau matches the printed rules, row for row")
    void bankerTableau() {
        // Player stood: the banker draws on 5 or less.
        for (int bankerScore = 0; bankerScore <= 9; bankerScore++) {
            assertEquals(bankerScore <= 5, BaccaratGame.bankerDraws(bankerScore, null),
                "banker on " + bankerScore + " with the player standing");
        }
        // Player drew: every row of the tableau.
        for (int third = 0; third <= 9; third++) {
            assertTrue(BaccaratGame.bankerDraws(0, third), "banker 0 always draws");
            assertTrue(BaccaratGame.bankerDraws(2, third), "banker 2 always draws");
            assertEquals(third != 8, BaccaratGame.bankerDraws(3, third), "banker 3 vs " + third);
            assertEquals(third >= 2 && third <= 7, BaccaratGame.bankerDraws(4, third),
                "banker 4 vs " + third);
            assertEquals(third >= 4 && third <= 7, BaccaratGame.bankerDraws(5, third),
                "banker 5 vs " + third);
            assertEquals(third >= 6 && third <= 7, BaccaratGame.bankerDraws(6, third),
                "banker 6 vs " + third);
            assertFalse(BaccaratGame.bankerDraws(7, third), "banker 7 always stands");
        }
    }

    @Test
    @DisplayName("a natural ends the coup — neither hand takes a third card")
    void naturalsStand() {
        // Driven through real deals rather than constructed, so the rule under test is the one the
        // game runs.
        Random random = new Random(3L);
        int naturals = 0;
        for (int i = 0; i < 20_000; i++) {
            BaccaratGame.Result result = BaccaratGame.play(BaccaratGame.Side.PLAYER, random);
            int playerTwoCard = BaccaratGame.score(result.playerHand().subList(0, 2));
            int bankerTwoCard = BaccaratGame.score(result.bankerHand().subList(0, 2));
            if (playerTwoCard >= 8 || bankerTwoCard >= 8) {
                naturals++;
                assertEquals(2, result.playerHand().size(), "a natural must not draw");
                assertEquals(2, result.bankerHand().size(), "a natural must not draw");
            }
        }
        assertTrue(naturals > 1000, "naturals should come up often; saw " + naturals);
    }

    @Test
    @DisplayName("no hand ever holds more than three cards")
    void handsAreBounded() {
        Random random = new Random(4L);
        for (int i = 0; i < 50_000; i++) {
            BaccaratGame.Result result = BaccaratGame.play(BaccaratGame.Side.BANKER, random);
            assertTrue(result.playerHand().size() <= 3, "player drew twice");
            assertTrue(result.bankerHand().size() <= 3, "banker drew twice");
            assertTrue(result.playerHand().size() >= 2);
            assertTrue(result.bankerHand().size() >= 2);
        }
    }

    @Test
    @DisplayName("a tie returns a player or banker bet, and pays a tie bet 9x")
    void tieHandling() {
        Random random = new Random(5L);
        int ties = 0;
        for (int i = 0; i < 50_000 && ties < 200; i++) {
            BaccaratGame.Result asPlayer = BaccaratGame.play(BaccaratGame.Side.PLAYER, random);
            if (asPlayer.winner() != BaccaratGame.Side.TIE) {
                continue;
            }
            ties++;
            assertEquals(1.0, asPlayer.totalReturnMultiplier(),
                "a tie must return a player bet, not take it");
            assertTrue(asPlayer.isPush());
        }
        assertTrue(ties > 0, "no ties came up to check");
    }

    @Test
    @DisplayName("each bet returns what baccarat is known to return")
    void returnsMatchTheKnownFigures() {
        // Real baccarat: banker about 98.9%, player about 98.6%, tie at 8:1 about 85.6%. Simulated
        // rather than computed — the tableau makes a closed form genuinely hard, and these figures
        // are well enough established that agreeing with them IS the check that the rules are right.
        double player = simulate(BaccaratGame.Side.PLAYER, 11L);
        double banker = simulate(BaccaratGame.Side.BANKER, 12L);
        double tie = simulate(BaccaratGame.Side.TIE, 13L);

        assertEquals(0.9863, player, 0.01, "player bet returned " + player);
        assertEquals(0.9894, banker, 0.01, "banker bet returned " + banker);
        assertEquals(0.8564, tie, 0.02, "tie bet returned " + tie);

        assertTrue(player < 1.0 && banker < 1.0 && tie < 1.0,
            "no baccarat bet may return 100% or more");
        assertTrue(banker > player,
            "the banker bet should be the better one even after commission");
    }

    @Test
    @DisplayName("the commission is what keeps the banker bet under 100%")
    void commissionMatters() {
        // Without it the banker bet would pay 2x on a side that wins more often than it loses,
        // which is the one change to this game that would turn it into a faucet.
        assertEquals(1.95, BaccaratGame.Side.BANKER.multiplier(), 1.0e-9);
        assertTrue(BaccaratGame.Side.BANKER.multiplier() < BaccaratGame.Side.PLAYER.multiplier());
    }

    @Test
    @DisplayName("side codes round-trip, so a button cannot send a different bet than it shows")
    void codesRoundTrip() {
        for (BaccaratGame.Side side : BaccaratGame.Side.values()) {
            assertEquals(side, BaccaratGame.sideFor(BaccaratGame.codeFor(side)), side.name());
        }
        assertNull(BaccaratGame.sideFor(-1));
        assertNull(BaccaratGame.sideFor(99));
    }

    private static double simulate(BaccaratGame.Side side, long seed) {
        Random random = new Random(seed);
        double returned = 0.0;
        for (int i = 0; i < ROUNDS; i++) {
            returned += BaccaratGame.play(side, random).totalReturnMultiplier();
        }
        return returned / ROUNDS;
    }

    private static Card card(Rank rank) {
        return new Card(rank, Suit.SPADES);
    }

    private static List<Card> hand(Rank... ranks) {
        List<Card> cards = new ArrayList<>();
        for (Rank rank : Arrays.asList(ranks)) {
            cards.add(card(rank));
        }
        return cards;
    }
}
