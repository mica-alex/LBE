package com.micatechnologies.minecraft.lbe.casino.blackjack;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One round of blackjack, from the deal to the dealer's last card.
 *
 * <h2>The rules, all of them</h2>
 *
 * <ul>
 *   <li>Infinite deck: every card is drawn independently, so nothing carries between rounds and
 *       nothing can be counted.</li>
 *   <li>The dealer peeks with an ace or a ten-value card showing. A dealer blackjack ends the round
 *       at once: a player blackjack pushes, anything else loses its one stake.</li>
 *   <li>A player blackjack pays 3:2.</li>
 *   <li>Double on any first two cards, including after a split. One more card, then the hand
 *       stands.</li>
 *   <li>Split any two cards of equal value (so K and Q split), once only. Split aces get one card
 *       each and stand, and 21 on a split hand is not a blackjack.</li>
 *   <li>The dealer stands on all 17s, soft included.</li>
 *   <li>No insurance, no surrender.</li>
 * </ul>
 *
 * <p>Pure, with no Minecraft types, like every game here. Money is the caller's: this only says how
 * many stakes ride on each hand and what each hand returns per stake.
 */
public final class BlackjackGame {

    /** What a player can do with the hand in front of them. */
    public enum Action {
        HIT, STAND, DOUBLE, SPLIT;

        /** The action for a code from the screen, or null. */
        public static Action byCode(int code) {
            Action[] all = values();
            return code >= 0 && code < all.length ? all[code] : null;
        }
    }

    /** A blackjack pays three to two: the stake back plus one and a half times it. */
    public static final double BLACKJACK_RETURN = 2.5;

    private final List<BlackjackHand> hands = new ArrayList<>();
    private final List<Card> dealer = new ArrayList<>();
    private int active;
    private boolean finished;

    private BlackjackGame() {
    }

    /** Draws a card from an infinite deck. */
    static Card draw(Random random) {
        Rank[] ranks = Rank.values();
        Suit[] suits = Suit.values();
        return new Card(ranks[random.nextInt(ranks.length)], suits[random.nextInt(suits.length)]);
    }

    /** Deals a round: two cards each, dealer's first face up. May finish at once on a blackjack. */
    public static BlackjackGame deal(Random random) {
        BlackjackGame game = new BlackjackGame();
        BlackjackHand hand = new BlackjackHand(false, false);
        game.hands.add(hand);
        hand.add(draw(random));
        game.dealer.add(draw(random));
        hand.add(draw(random));
        game.dealer.add(draw(random));

        boolean dealerBlackjack = handTotal(game.dealer) == 21;
        if (dealerBlackjack || hand.isBlackjack()) {
            // With an ace or ten showing the dealer has peeked; either way the round is over.
            hand.finish();
            game.finished = true;
        }
        return game;
    }

    /** Whether {@code action} is allowed on the active hand right now. */
    public boolean canDo(Action action) {
        if (finished || action == null) {
            return false;
        }
        BlackjackHand hand = hands.get(active);
        switch (action) {
            case HIT:
            case STAND:
                return true;
            case DOUBLE:
                return hand.cards().size() == 2 && !hand.isSplitAces();
            case SPLIT:
                return hands.size() == 1 && hand.cards().size() == 2
                    && BlackjackHand.hardValue(hand.cards().get(0).rank())
                        == BlackjackHand.hardValue(hand.cards().get(1).rank());
            default:
                return false;
        }
    }

    /**
     * Plays an action on the active hand. The caller stakes the extra money for a double or split
     * <b>before</b> calling this, and must not call it when {@link #canDo} is false.
     */
    public void apply(Action action, Random random) {
        if (!canDo(action)) {
            throw new IllegalStateException(action + " is not allowed now");
        }
        BlackjackHand hand = hands.get(active);
        switch (action) {
            case HIT:
                hand.add(draw(random));
                if (hand.isBust() || hand.total() == 21) {
                    hand.finish();
                }
                break;
            case STAND:
                hand.finish();
                break;
            case DOUBLE:
                hand.doubleDown();
                hand.add(draw(random));
                hand.finish();
                break;
            case SPLIT:
                split(random);
                break;
            default:
                break;
        }
        advance(random);
    }

    private void split(Random random) {
        BlackjackHand original = hands.get(0);
        boolean aces = original.cards().get(0).rank() == Rank.ACE;
        BlackjackHand first = new BlackjackHand(true, aces);
        BlackjackHand second = new BlackjackHand(true, aces);
        first.add(original.cards().get(0));
        second.add(original.cards().get(1));
        first.add(draw(random));
        second.add(draw(random));
        for (BlackjackHand hand : new BlackjackHand[] {first, second}) {
            if (aces || hand.total() == 21) {
                hand.finish();
            }
        }
        hands.clear();
        hands.add(first);
        hands.add(second);
        active = 0;
    }

    /** Moves to the next unfinished hand, or plays out the dealer when there is none. */
    private void advance(Random random) {
        while (active < hands.size() && hands.get(active).isDone()) {
            active++;
        }
        if (active < hands.size()) {
            return;
        }
        active = hands.size() - 1;
        boolean anyAlive = false;
        for (BlackjackHand hand : hands) {
            anyAlive |= !hand.isBust();
        }
        if (anyAlive) {
            while (handTotal(dealer) < 17) {
                dealer.add(draw(random));
            }
        }
        finished = true;
    }

    /** Best total of a list of cards, as for a hand. */
    static int handTotal(List<Card> cards) {
        int hard = 0;
        boolean ace = false;
        for (Card card : cards) {
            hard += BlackjackHand.hardValue(card.rank());
            ace |= card.rank() == Rank.ACE;
        }
        return ace && hard + 10 <= 21 ? hard + 10 : hard;
    }

    /** The dealer's best total. Mid-round that includes the hidden card, so never show it then. */
    public int dealerTotal() {
        return handTotal(dealer);
    }

    public boolean isFinished() {
        return finished;
    }

    public List<BlackjackHand> hands() {
        return Collections.unmodifiableList(hands);
    }

    /** Index of the hand being played. */
    public int activeHand() {
        return active;
    }

    /** The dealer's cards. Mid-round, only the first is face up; the screen must hide the rest. */
    public List<Card> dealer() {
        return Collections.unmodifiableList(dealer);
    }

    /**
     * What a finished hand returns per stake, "for 1": 0 lost, 1 push, 2 won, 2.5 blackjack.
     */
    public double returnFor(int handIndex) {
        if (!finished) {
            throw new IllegalStateException("The round is not over");
        }
        BlackjackHand hand = hands.get(handIndex);
        int dealerTotal = handTotal(dealer);
        boolean dealerBlackjack = dealer.size() == 2 && dealerTotal == 21;
        if (hand.isBlackjack()) {
            return dealerBlackjack ? 1.0 : BLACKJACK_RETURN;
        }
        if (dealerBlackjack || hand.isBust()) {
            return 0.0;
        }
        if (dealerTotal > 21) {
            return 2.0;
        }
        int total = hand.total();
        return total > dealerTotal ? 2.0 : total == dealerTotal ? 1.0 : 0.0;
    }

    /** Stakes in play across every hand. */
    public int totalStakes() {
        int stakes = 0;
        for (BlackjackHand hand : hands) {
            stakes += hand.stakes();
        }
        return stakes;
    }
}
