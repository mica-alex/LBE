package com.micatechnologies.minecraft.lbe.casino.cards;

/** The four suits. No game here ranks them; they exist so a card can be shown and told apart. */
public enum Suit {

    SPADES("\u2660", false),
    HEARTS("\u2665", true),
    DIAMONDS("\u2666", true),
    CLUBS("\u2663", false);

    private final String symbol;
    private final boolean red;

    Suit(String symbol, boolean red) {
        this.symbol = symbol;
        this.red = red;
    }

    public String symbol() {
        return symbol;
    }

    /** Whether the suit draws red, which is all the colour information a screen needs. */
    public boolean isRed() {
        return red;
    }
}
