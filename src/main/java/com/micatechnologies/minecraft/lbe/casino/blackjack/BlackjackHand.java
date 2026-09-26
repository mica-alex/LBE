package com.micatechnologies.minecraft.lbe.casino.blackjack;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One blackjack hand: its cards, how many stakes ride on it (two once doubled), and whether it is
 * finished.
 *
 * <p>Totals count every ace as 1 and then use one as 11 if that does not bust, which is the only
 * reading of aces blackjack needs: two aces as 11 would always be 22.
 */
public final class BlackjackHand {

    private final List<Card> cards = new ArrayList<>();
    private final boolean fromSplit;
    private final boolean splitAces;
    private int stakes = 1;
    private boolean done;

    BlackjackHand(boolean fromSplit, boolean splitAces) {
        this.fromSplit = fromSplit;
        this.splitAces = splitAces;
    }

    /** A card's blackjack value with aces as 1: 2-9 face value, ten and pictures 10. */
    public static int hardValue(Rank rank) {
        switch (rank) {
            case ACE:
                return 1;
            case TEN:
            case JACK:
            case QUEEN:
            case KING:
                return 10;
            default:
                return rank.value();
        }
    }

    void add(Card card) {
        cards.add(card);
    }

    void doubleDown() {
        stakes = 2;
    }

    void finish() {
        done = true;
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    /** Sum with every ace as 1. */
    public int hardTotal() {
        int total = 0;
        for (Card card : cards) {
            total += hardValue(card.rank());
        }
        return total;
    }

    public boolean hasAce() {
        for (Card card : cards) {
            if (card.rank() == Rank.ACE) {
                return true;
            }
        }
        return false;
    }

    /** The hand's best total: an ace counts as 11 if that does not bust. */
    public int total() {
        int hard = hardTotal();
        return hasAce() && hard + 10 <= 21 ? hard + 10 : hard;
    }

    /** True when an ace is being counted as 11. */
    public boolean isSoft() {
        int hard = hardTotal();
        return hasAce() && hard + 10 <= 21;
    }

    public boolean isBust() {
        return hardTotal() > 21;
    }

    /** Twenty-one in two cards, dealt rather than made after a split. */
    public boolean isBlackjack() {
        return !fromSplit && cards.size() == 2 && total() == 21;
    }

    /** Stakes riding on this hand: 1, or 2 after doubling. */
    public int stakes() {
        return stakes;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isFromSplit() {
        return fromSplit;
    }

    /** Split aces get one card each and no further say. */
    public boolean isSplitAces() {
        return splitAces;
    }
}
