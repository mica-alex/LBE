package com.micatechnologies.minecraft.lbe.casino.videopoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import com.micatechnologies.minecraft.lbe.casino.poker.PokerHand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The hand evaluator and the 9/6 paytable.
 *
 * <p>The evaluator is where a video poker machine is most likely to be quietly wrong, because most
 * of its categories are rare: a straight flush turns up once in about 9,000 hands, so an evaluator
 * that never recognised one would look fine for a very long time. Every category is therefore
 * constructed and checked directly rather than waited for.
 */
class VideoPokerGameTest {

    // ---------------------------------------------------------------------------------------------
    // Hand ranking
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every hand category is recognised")
    void categories() {
        assertEquals(PokerHand.Category.ROYAL_FLUSH, evaluate(
            card(Rank.TEN, Suit.SPADES), card(Rank.JACK, Suit.SPADES),
            card(Rank.QUEEN, Suit.SPADES), card(Rank.KING, Suit.SPADES),
            card(Rank.ACE, Suit.SPADES)));

        assertEquals(PokerHand.Category.STRAIGHT_FLUSH, evaluate(
            card(Rank.FIVE, Suit.HEARTS), card(Rank.SIX, Suit.HEARTS),
            card(Rank.SEVEN, Suit.HEARTS), card(Rank.EIGHT, Suit.HEARTS),
            card(Rank.NINE, Suit.HEARTS)));

        assertEquals(PokerHand.Category.FOUR_OF_A_KIND, evaluate(
            card(Rank.SEVEN, Suit.SPADES), card(Rank.SEVEN, Suit.HEARTS),
            card(Rank.SEVEN, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
            card(Rank.TWO, Suit.SPADES)));

        assertEquals(PokerHand.Category.FULL_HOUSE, evaluate(
            card(Rank.FOUR, Suit.SPADES), card(Rank.FOUR, Suit.HEARTS),
            card(Rank.FOUR, Suit.DIAMONDS), card(Rank.NINE, Suit.CLUBS),
            card(Rank.NINE, Suit.SPADES)));

        assertEquals(PokerHand.Category.FLUSH, evaluate(
            card(Rank.TWO, Suit.CLUBS), card(Rank.FIVE, Suit.CLUBS),
            card(Rank.NINE, Suit.CLUBS), card(Rank.JACK, Suit.CLUBS),
            card(Rank.KING, Suit.CLUBS)));

        assertEquals(PokerHand.Category.STRAIGHT, evaluate(
            card(Rank.FOUR, Suit.SPADES), card(Rank.FIVE, Suit.HEARTS),
            card(Rank.SIX, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
            card(Rank.EIGHT, Suit.SPADES)));

        assertEquals(PokerHand.Category.THREE_OF_A_KIND, evaluate(
            card(Rank.QUEEN, Suit.SPADES), card(Rank.QUEEN, Suit.HEARTS),
            card(Rank.QUEEN, Suit.DIAMONDS), card(Rank.TWO, Suit.CLUBS),
            card(Rank.FIVE, Suit.SPADES)));

        assertEquals(PokerHand.Category.TWO_PAIR, evaluate(
            card(Rank.THREE, Suit.SPADES), card(Rank.THREE, Suit.HEARTS),
            card(Rank.EIGHT, Suit.DIAMONDS), card(Rank.EIGHT, Suit.CLUBS),
            card(Rank.KING, Suit.SPADES)));

        assertEquals(PokerHand.Category.JACKS_OR_BETTER, evaluate(
            card(Rank.JACK, Suit.SPADES), card(Rank.JACK, Suit.HEARTS),
            card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
            card(Rank.NINE, Suit.SPADES)));

        assertEquals(PokerHand.Category.NOTHING, evaluate(
            card(Rank.TWO, Suit.SPADES), card(Rank.FIVE, Suit.HEARTS),
            card(Rank.NINE, Suit.DIAMONDS), card(Rank.JACK, Suit.CLUBS),
            card(Rank.KING, Suit.SPADES)));
    }

    @Test
    @DisplayName("the game's namesake rule: tens do not pay, jacks do")
    void jacksOrBetterThreshold() {
        // The single most important boundary in the paytable, and the easiest to get wrong by one.
        assertEquals(PokerHand.Category.NOTHING, evaluate(
            card(Rank.TEN, Suit.SPADES), card(Rank.TEN, Suit.HEARTS),
            card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
            card(Rank.NINE, Suit.SPADES)));
        for (Rank rank : new Rank[] {Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE}) {
            assertEquals(PokerHand.Category.JACKS_OR_BETTER, evaluate(
                card(rank, Suit.SPADES), card(rank, Suit.HEARTS),
                card(Rank.THREE, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
                card(Rank.NINE, Suit.SPADES)), "a pair of " + rank + " must pay");
        }
    }

    @Test
    @DisplayName("the wheel counts as a straight, with the ace playing low")
    void wheelStraight() {
        // A-2-3-4-5. The one place an ace is not the high card; leaving it out would silently
        // refuse to pay a legitimate straight.
        assertEquals(PokerHand.Category.STRAIGHT, evaluate(
            card(Rank.ACE, Suit.SPADES), card(Rank.TWO, Suit.HEARTS),
            card(Rank.THREE, Suit.DIAMONDS), card(Rank.FOUR, Suit.CLUBS),
            card(Rank.FIVE, Suit.SPADES)));
        assertEquals(PokerHand.Category.STRAIGHT_FLUSH, evaluate(
            card(Rank.ACE, Suit.SPADES), card(Rank.TWO, Suit.SPADES),
            card(Rank.THREE, Suit.SPADES), card(Rank.FOUR, Suit.SPADES),
            card(Rank.FIVE, Suit.SPADES)));
    }

    @Test
    @DisplayName("a hand with a pair in it is never a straight")
    void pairsAreNotStraights() {
        // 5,5,6,7,8 spans four but repeats — a naive high-minus-low check calls it a straight.
        assertEquals(PokerHand.Category.NOTHING, evaluate(
            card(Rank.FIVE, Suit.SPADES), card(Rank.FIVE, Suit.HEARTS),
            card(Rank.SIX, Suit.DIAMONDS), card(Rank.SEVEN, Suit.CLUBS),
            card(Rank.EIGHT, Suit.SPADES)));
    }

    @Test
    @DisplayName("a malformed hand is refused rather than scored")
    void malformedHandThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> PokerHand.evaluate(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> PokerHand.evaluate(null));
    }

    // ---------------------------------------------------------------------------------------------
    // Paytable
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the paytable is the published 9/6 schedule")
    void nineSixPaytable() {
        // "9/6" names the full house and flush rows, and it is the schedule every strategy chart
        // in the world is written against. Shortening either is the honest way to take more edge;
        // changing anything else makes the published charts wrong for this machine.
        assertEquals(9.0, VideoPokerGame.payout(PokerHand.Category.FULL_HOUSE));
        assertEquals(6.0, VideoPokerGame.payout(PokerHand.Category.FLUSH));
        assertEquals(250.0, VideoPokerGame.payout(PokerHand.Category.ROYAL_FLUSH));
        assertEquals(50.0, VideoPokerGame.payout(PokerHand.Category.STRAIGHT_FLUSH));
        assertEquals(25.0, VideoPokerGame.payout(PokerHand.Category.FOUR_OF_A_KIND));
        assertEquals(4.0, VideoPokerGame.payout(PokerHand.Category.STRAIGHT));
        assertEquals(3.0, VideoPokerGame.payout(PokerHand.Category.THREE_OF_A_KIND));
        assertEquals(2.0, VideoPokerGame.payout(PokerHand.Category.TWO_PAIR));
        assertEquals(0.0, VideoPokerGame.payout(PokerHand.Category.NOTHING));
    }

    @Test
    @DisplayName("a jacks-or-better pair returns the stake and nothing more")
    void lowestWinIsAPush() {
        // Worth stating: it reads as a win on screen and is not one. A player who does not realise
        // that will think the machine short-paid them.
        assertEquals(1.0, VideoPokerGame.payout(PokerHand.Category.JACKS_OR_BETTER));
    }

    @Test
    @DisplayName("better hands never pay less than worse ones")
    void paytableIsMonotonic() {
        PokerHand.Category[] all = PokerHand.Category.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(VideoPokerGame.payout(all[i]) >= VideoPokerGame.payout(all[i - 1]),
                all[i] + " pays less than " + all[i - 1]);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Playing
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("held cards stay and the rest are replaced")
    void holdsAreRespected() {
        Random random = new Random(2L);
        for (int round = 0; round < 2000; round++) {
            VideoPokerGame game = new VideoPokerGame(random);
            List<Card> dealt = game.hand();
            boolean[] holds = {true, false, true, false, true};
            VideoPokerGame.Result result = game.draw(holds);
            for (int i = 0; i < VideoPokerGame.HAND_SIZE; i++) {
                if (holds[i]) {
                    assertEquals(dealt.get(i), result.finalHand().get(i), "a held card moved");
                }
            }
            assertEquals(dealt, result.dealtHand(), "the opening hand should be reported as dealt");
        }
    }

    @Test
    @DisplayName("holding everything settles the hand exactly as dealt")
    void holdingAllKeepsTheHand() {
        Random random = new Random(3L);
        VideoPokerGame game = new VideoPokerGame(random);
        List<Card> dealt = game.hand();
        VideoPokerGame.Result result = game.draw(new boolean[] {true, true, true, true, true});
        assertEquals(dealt, result.finalHand());
        assertEquals(PokerHand.evaluate(dealt), result.category());
    }

    @Test
    @DisplayName("a null or short hold array holds nothing rather than failing")
    void malformedHoldsDrawFive() {
        // The client decides what to keep, so a malformed choice must not be able to strand a stake
        // that is already down.
        Random random = new Random(4L);
        assertEquals(VideoPokerGame.HAND_SIZE,
            new VideoPokerGame(random).draw(null).finalHand().size());
        assertEquals(VideoPokerGame.HAND_SIZE,
            new VideoPokerGame(random).draw(new boolean[] {true}).finalHand().size());
    }

    @Test
    @DisplayName("a hand cannot be drawn twice")
    void drawOnce() {
        VideoPokerGame game = new VideoPokerGame(new Random(5L));
        game.draw(new boolean[5]);
        assertThrows(IllegalStateException.class, () -> game.draw(new boolean[5]));
    }

    @Test
    @DisplayName("no card is ever dealt twice in one hand")
    void noDuplicateCards() {
        Random random = new Random(6L);
        for (int round = 0; round < 20_000; round++) {
            VideoPokerGame game = new VideoPokerGame(random);
            VideoPokerGame.Result result = game.draw(new boolean[] {true, false, true, false, true});
            List<Card> finalHand = result.finalHand();
            for (int i = 0; i < finalHand.size(); i++) {
                for (int j = i + 1; j < finalHand.size(); j++) {
                    assertTrue(!finalHand.get(i).equals(finalHand.get(j)),
                        "the same card appeared twice: " + finalHand.get(i));
                }
            }
        }
    }

    @Test
    @DisplayName("skill is worth about 29% of turnover, which is what makes this game different")
    void skillMovesTheReturn() {
        // Video poker is the only game in the casino where the player's choices move the number,
        // so there is no single return to pin — only a range.
        //
        // The bottom of it is measured here: a deliberately naive strategy returns about 70%. The
        // top is roughly 99.5%, the published figure for optimal play on a 9/6 paytable, which is
        // the thinnest margin in the building.
        //
        // That ~29-point spread is the interesting fact. Every other game returns what it returns
        // whoever is playing; this one pays attention. It also means a server cannot state one
        // "return to player" for it honestly, which is why the screen says the range.
        Random random = new Random(7L);
        int rounds = 300_000;
        double returned = 0.0;
        for (int i = 0; i < rounds; i++) {
            VideoPokerGame game = new VideoPokerGame(random);
            returned += game.draw(naiveHolds(game.hand())).totalReturnMultiplier();
        }
        double rtp = returned / rounds;
        assertEquals(0.706, rtp, 0.02, "the naive baseline moved: " + rtp);
        assertTrue(rtp < 1.0, "even bad play must not beat the house");
    }

    /**
     * Keep a paying hand whole; otherwise keep the high cards. Deliberately naive.
     *
     * <p>Not a strategy anybody should use — it throws away low pairs and four-card flushes alike,
     * which is most of where the missing 29% goes. It exists to measure the floor.
     */
    private static boolean[] naiveHolds(List<Card> hand) {
        boolean[] holds = new boolean[VideoPokerGame.HAND_SIZE];
        if (PokerHand.evaluate(hand) != PokerHand.Category.NOTHING) {
            Arrays.fill(holds, true);
            return holds;
        }
        for (int i = 0; i < hand.size(); i++) {
            holds[i] = hand.get(i).value() >= Rank.JACK.value();
        }
        return holds;
    }

    private static PokerHand.Category evaluate(Card... cards) {
        return PokerHand.evaluate(Arrays.asList(cards));
    }

    private static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
