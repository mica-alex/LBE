package com.micatechnologies.minecraft.lbe.casino.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A shuffled 52-card deck, dealt from the top.
 *
 * <p><b>A fresh deck per hand, matching the Discord bot's games.</b> That is not how a real casino
 * works — a real one deals from a shoe until it is spent, which is what makes card counting possible
 * — and it is deliberately not what happens here. Reshuffling every hand means no information
 * carries between hands, so a game's odds are the same on the thousandth deal as the first, and the
 * return-to-player figure each game states stays true no matter how long somebody sits there.
 */
public final class Deck {

    private final List<Card> cards;

    /** Builds and shuffles a full 52-card deck. */
    public Deck(Random random) {
        cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, random);
    }

    /**
     * Deals the top card.
     *
     * @throws IllegalStateException if the deck is spent. No game here deals anywhere near 52 cards,
     *     so reaching this means a bug in a game's flow rather than an expected end of shoe — and it
     *     must be loud, because the alternative is silently dealing something that is not a card
     *     into a hand somebody has money on.
     */
    public Card deal() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("The deck is empty; a game has dealt more than 52 cards");
        }
        return cards.remove(cards.size() - 1);
    }

    /** Deals {@code count} cards, in order. */
    public List<Card> deal(int count) {
        List<Card> dealt = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            dealt.add(deal());
        }
        return dealt;
    }

    /** How many cards are left. */
    public int remaining() {
        return cards.size();
    }
}
