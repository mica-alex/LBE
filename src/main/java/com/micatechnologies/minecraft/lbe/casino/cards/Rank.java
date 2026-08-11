package com.micatechnologies.minecraft.lbe.casino.cards;

/**
 * Card ranks, two through ace, with ace high.
 *
 * <p>{@link #value()} is the plain comparison value used by war and high-low. Games where an ace
 * means something else — blackjack counting it as 1 or 11, a wheel straight in poker — do that
 * reading themselves rather than bending this enum, because there is no single answer that suits
 * all of them.
 */
public enum Rank {

    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
    ACE("A", 14);

    private final String symbol;
    private final int value;

    Rank(String symbol, int value) {
        this.symbol = symbol;
        this.value = value;
    }

    public String symbol() {
        return symbol;
    }

    /** 2..14, ace high. */
    public int value() {
        return value;
    }
}
