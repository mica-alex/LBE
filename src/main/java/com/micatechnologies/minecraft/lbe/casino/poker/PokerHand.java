package com.micatechnologies.minecraft.lbe.casino.poker;

import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a five-card hand is worth, in Jacks-or-Better terms.
 *
 * <p>Ported from the Discord bot's {@code evaluate_video_poker_hand}, ranks and thresholds
 * unchanged — including the one that gives the game its name: a pair only pays if it is jacks or
 * better, and a pair of tens is nothing.
 *
 * <p>Separate from the game that uses it because a hand evaluator is worth testing on its own, and
 * because blackjack and hold'em will want their own readings of the same cards later. Pure: no
 * Minecraft types, no randomness, no money.
 */
public final class PokerHand {

    /** Hand ranks, worst to best. Ordinal order is meaningful — {@code compareTo} works. */
    public enum Category {
        NOTHING("No pair"),
        JACKS_OR_BETTER("Jacks or better"),
        TWO_PAIR("Two pair"),
        THREE_OF_A_KIND("Three of a kind"),
        STRAIGHT("Straight"),
        FLUSH("Flush"),
        FULL_HOUSE("Full house"),
        FOUR_OF_A_KIND("Four of a kind"),
        STRAIGHT_FLUSH("Straight flush"),
        ROYAL_FLUSH("Royal flush");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        /** What a player sees on the screen. */
        public String label() {
            return label;
        }
    }

    /** How many cards a hand holds. */
    public static final int SIZE = 5;

    private PokerHand() {
        throw new AssertionError("No instances.");
    }

    /**
     * Categorises a five-card hand.
     *
     * @throws IllegalArgumentException for anything that is not five cards — silently scoring a
     *     malformed hand would settle a real bet against a hand nobody was dealt.
     */
    public static Category evaluate(List<Card> hand) {
        if (hand == null || hand.size() != SIZE) {
            throw new IllegalArgumentException("A video poker hand is exactly " + SIZE + " cards");
        }
        int[] countsByValue = new int[Rank.ACE.value() + 1];
        List<Integer> values = new ArrayList<>(SIZE);
        Suit firstSuit = hand.get(0).suit();
        boolean flush = true;
        for (Card card : hand) {
            countsByValue[card.value()]++;
            values.add(card.value());
            flush &= card.suit() == firstSuit;
        }
        Collections.sort(values);

        boolean straight = isStraight(values);
        if (flush && straight) {
            // Ten through ace is the royal; everything else in sequence is a straight flush.
            return values.get(0) == Rank.TEN.value() && values.get(SIZE - 1) == Rank.ACE.value()
                ? Category.ROYAL_FLUSH : Category.STRAIGHT_FLUSH;
        }

        int pairs = 0;
        int trips = 0;
        int quads = 0;
        int highestPairValue = 0;
        for (int value = 0; value < countsByValue.length; value++) {
            switch (countsByValue[value]) {
                case 4:
                    quads++;
                    break;
                case 3:
                    trips++;
                    break;
                case 2:
                    pairs++;
                    highestPairValue = Math.max(highestPairValue, value);
                    break;
                default:
                    break;
            }
        }

        if (quads > 0) {
            return Category.FOUR_OF_A_KIND;
        }
        if (trips > 0 && pairs > 0) {
            return Category.FULL_HOUSE;
        }
        if (flush) {
            return Category.FLUSH;
        }
        if (straight) {
            return Category.STRAIGHT;
        }
        if (trips > 0) {
            return Category.THREE_OF_A_KIND;
        }
        if (pairs >= 2) {
            return Category.TWO_PAIR;
        }
        // The game's name, in one line: a single pair pays only from jacks up.
        if (pairs == 1 && highestPairValue >= Rank.JACK.value()) {
            return Category.JACKS_OR_BETTER;
        }
        return Category.NOTHING;
    }

    /**
     * Whether five sorted values run in sequence.
     *
     * <p>Handles the wheel — ace, two, three, four, five — where the ace plays low. That is the one
     * place in this file where an ace is not the highest card, and leaving it out would quietly
     * refuse to pay a legitimate straight.
     */
    private static boolean isStraight(List<Integer> sortedValues) {
        for (int i = 1; i < sortedValues.size(); i++) {
            if (sortedValues.get(i).equals(sortedValues.get(i - 1))) {
                return false;   // a pair cannot be part of a straight
            }
        }
        if (sortedValues.get(SIZE - 1) - sortedValues.get(0) == SIZE - 1) {
            return true;
        }
        // A-2-3-4-5, which sorts as 2,3,4,5,14.
        return sortedValues.get(0) == Rank.TWO.value()
            && sortedValues.get(1) == Rank.THREE.value()
            && sortedValues.get(2) == Rank.FOUR.value()
            && sortedValues.get(3) == Rank.FIVE.value()
            && sortedValues.get(4) == Rank.ACE.value();
    }
}
