package com.micatechnologies.minecraft.lbe.casino.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Blackjack's maths is right, the engine plays by the same rules as the maths, and the house keeps
 * an edge even against perfect play.
 */
class BlackjackTest {

    @Test
    @DisplayName("a perfect player gets back a little under what they stake")
    void returnToPlayer() {
        double rtp = BlackjackMath.returnToPlayer();
        // Infinite deck, S17, 3:2, DAS, one split, no surrender: published house edges for these
        // rules sit around 0.4-0.5%.
        assertTrue(rtp < 1.0, "blackjack returns " + rtp + " to a perfect player");
        assertTrue(rtp > 0.99, "blackjack returns " + rtp + ", far from the published figure");
    }

    @Test
    @DisplayName("the engine, played perfectly, returns what the maths says")
    void engineMatchesMaths() {
        Random random = new Random(20260926L);
        int rounds = 1_000_000;
        double staked = 0.0;
        double returned = 0.0;
        for (int i = 0; i < rounds; i++) {
            BlackjackGame game = BlackjackGame.deal(random);
            int up = BlackjackMath.indexOf(game.dealer().get(0).rank());
            while (!game.isFinished()) {
                BlackjackHand hand = game.hands().get(game.activeHand());
                BlackjackGame.Action action = BlackjackMath.bestAction(hand, up,
                    game.canDo(BlackjackGame.Action.DOUBLE), game.canDo(BlackjackGame.Action.SPLIT));
                game.apply(action, random);
            }
            staked += 1.0;   // per initial stake, as returnToPlayer is
            for (int h = 0; h < game.hands().size(); h++) {
                BlackjackHand hand = game.hands().get(h);
                returned += hand.stakes() * game.returnFor(h) - (hand.stakes() - 1.0)
                    - (h > 0 ? 1.0 : 0.0);
            }
        }
        double measured = returned / staked;
        // One million rounds: the standard error is about 0.0012, so 0.006 is five of them.
        assertEquals(BlackjackMath.returnToPlayer(), measured, 0.006,
            "engine returned " + measured);
    }

    @Test
    @DisplayName("a blackjack pays 3:2 and a dealer blackjack ends the round")
    void naturals() {
        Random random = new Random(7L);
        int playerBlackjacks = 0;
        for (int i = 0; i < 200_000; i++) {
            BlackjackGame game = BlackjackGame.deal(random);
            BlackjackHand hand = game.hands().get(0);
            boolean dealerBj = BlackjackGame.handTotal(game.dealer()) == 21;
            if (hand.isBlackjack() || dealerBj) {
                assertTrue(game.isFinished());
                double expected = hand.isBlackjack() ? (dealerBj ? 1.0 : 2.5) : 0.0;
                assertEquals(expected, game.returnFor(0), 1e-12);
                playerBlackjacks += hand.isBlackjack() ? 1 : 0;
            } else {
                assertFalse(game.isFinished());
            }
        }
        // 2 x 4/13 x 1/13 = 4.73% of deals.
        assertEquals(0.0473, playerBlackjacks / 200_000.0, 0.003);
    }

    @Test
    @DisplayName("split once only; split aces take one card each; doubling ends the hand")
    void splitsAndDoubles() {
        Random random = new Random(11L);
        int checked = 0;
        for (int i = 0; i < 200_000 && checked < 500; i++) {
            BlackjackGame game = BlackjackGame.deal(random);
            if (game.isFinished() || !game.canDo(BlackjackGame.Action.SPLIT)) {
                continue;
            }
            boolean aces = game.hands().get(0).cards().get(0).rank()
                == com.micatechnologies.minecraft.lbe.casino.cards.Rank.ACE;
            game.apply(BlackjackGame.Action.SPLIT, random);
            assertEquals(2, game.hands().size());
            assertFalse(game.canDo(BlackjackGame.Action.SPLIT), "only one split");
            if (aces) {
                assertTrue(game.isFinished(), "split aces get one card each and stand");
                for (BlackjackHand hand : game.hands()) {
                    assertEquals(2, hand.cards().size());
                    assertFalse(hand.isBlackjack(), "21 after a split is not a blackjack");
                }
            } else if (game.canDo(BlackjackGame.Action.DOUBLE)) {
                int before = game.activeHand();
                game.apply(BlackjackGame.Action.DOUBLE, random);
                BlackjackHand doubled = game.hands().get(before);
                assertEquals(2, doubled.stakes());
                assertEquals(3, doubled.cards().size());
                assertTrue(doubled.isDone());
            }
            checked++;
        }
        assertTrue(checked > 100);
    }
}
