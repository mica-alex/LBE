package com.micatechnologies.minecraft.lbe.casino.cards;

/**
 * One playing card. Immutable, and cheap enough to make a fresh deck per hand.
 *
 * <p>Shared by every card game in the casino, so the rank ordering lives here once. War and
 * high-low compare {@link Rank#value()} directly; blackjack and poker will need their own reading of
 * an ace, which is why {@link Rank} keeps a plain ordinal value and leaves the special cases to the
 * games that have them.
 */
public final class Card {

    private final Rank rank;
    private final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Rank rank() {
        return rank;
    }

    public Suit suit() {
        return suit;
    }

    /** Two through ace, as 2..14. */
    public int value() {
        return rank.value();
    }

    /** Short form for a screen or a log line, e.g. {@code Q♥}. */
    @Override
    public String toString() {
        return rank.symbol() + suit.symbol();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Card)) {
            return false;
        }
        Card that = (Card) other;
        return rank == that.rank && suit == that.suit;
    }

    @Override
    public int hashCode() {
        return rank.hashCode() * 31 + suit.hashCode();
    }
}
