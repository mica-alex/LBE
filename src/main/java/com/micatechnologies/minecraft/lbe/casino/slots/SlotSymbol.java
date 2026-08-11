package com.micatechnologies.minecraft.lbe.casino.slots;

/**
 * The six symbols on a reel, with the two numbers that define the whole game.
 *
 * <p>{@code weight} is how often a symbol comes up, relative to the others. {@code tripleMultiplier}
 * is what three of it pays, "for 1" — a $10 bet on three sevens returns $1000, of which $990 is
 * profit. Rarer symbols pay more, which is the only interesting thing about a slot machine.
 *
 * <p><b>These numbers are the paytable, and the paytable is the house edge.</b> Changing one changes
 * how much money the machine pulls out of the server's economy per spin;
 * {@link SlotPaytable#returnToPlayer()} computes exactly how much, and a test pins it. Do not tune
 * these by feel — work out what the return becomes first.
 */
public enum SlotSymbol {

    /** The common one, and the only symbol that pays for a partial match. */
    CHERRY("cherry", 30, 5),
    LEMON("lemon", 25, 8),
    BELL("bell", 18, 12),
    STAR("star", 12, 25),
    DIAMOND("diamond", 8, 50),
    SEVEN("seven", 4, 100);

    private final String id;
    private final int weight;
    private final int tripleMultiplier;

    SlotSymbol(String id, int weight, int tripleMultiplier) {
        this.id = id;
        this.weight = weight;
        this.tripleMultiplier = tripleMultiplier;
    }

    /** Lowercase name used in texture paths and lang keys. */
    public String id() {
        return id;
    }

    /** Relative frequency on a reel. Not a percentage — see {@link SlotPaytable#totalWeight()}. */
    public int weight() {
        return weight;
    }

    /** What three of this symbol pays, as a multiple of the bet, "for 1". */
    public int tripleMultiplier() {
        return tripleMultiplier;
    }

    /** Index in {@link #values()}, which is the order symbols appear on the reel strip. */
    public int index() {
        return ordinal();
    }

    /** The symbol at {@code index}, wrapping — a reel strip is a loop with no end. */
    public static SlotSymbol byIndex(int index) {
        SlotSymbol[] all = values();
        int wrapped = ((index % all.length) + all.length) % all.length;
        return all[wrapped];
    }
}
