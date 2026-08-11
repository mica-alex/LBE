package com.micatechnologies.minecraft.lbe.casino.slots;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Arrays;
import java.util.Random;

/**
 * One pull of the lever: where the reels stopped and what it paid.
 *
 * <p>Immutable, and carries no money — only a multiplier. The amount is applied by the caller that
 * holds the stake, which keeps this class testable without a server and keeps exactly one place in
 * the mod able to move a player's balance.
 *
 * <p><b>The server rolls; the client is told.</b> {@link #roll} is only ever called server-side. The
 * client receives a finished spin and animates toward the answer it was given — it never generates
 * one, because a reel that stops wherever the client decides is a machine that pays whatever the
 * client decides.
 */
public final class SlotSpin implements GameResult {

    private final SlotSymbol[] reels;
    private final int multiplier;

    private SlotSpin(SlotSymbol[] reels, int multiplier) {
        this.reels = reels;
        this.multiplier = multiplier;
    }

    /** Spins three reels and works out the result. */
    public static SlotSpin roll(Random random) {
        SlotSymbol[] reels = new SlotSymbol[SlotPaytable.REELS];
        for (int i = 0; i < reels.length; i++) {
            reels[i] = pick(random);
        }
        return of(reels);
    }

    /** Builds a spin from known reels — for the client receiving a server's roll, and for tests. */
    public static SlotSpin of(SlotSymbol[] reels) {
        SlotSymbol[] copy = Arrays.copyOf(reels, reels.length);
        return new SlotSpin(copy, SlotPaytable.multiplierFor(copy));
    }

    /** One weighted reel stop. */
    private static SlotSymbol pick(Random random) {
        int roll = random.nextInt(SlotPaytable.totalWeight());
        for (SlotSymbol symbol : SlotSymbol.values()) {
            roll -= symbol.weight();
            if (roll < 0) {
                return symbol;
            }
        }
        // Unreachable while the loop above subtracts exactly totalWeight() across all symbols, but
        // returning something valid beats throwing in the middle of a wager that has been paid for.
        return SlotSymbol.CHERRY;
    }

    /** Where the reels stopped, left to right. */
    public SlotSymbol[] reels() {
        return Arrays.copyOf(reels, reels.length);
    }

    /** The symbol on one reel. */
    public SlotSymbol reel(int index) {
        return reels[index];
    }

    /** What this pays as a multiple of the bet, "for 1". Zero for a loss. */
    public int multiplier() {
        return multiplier;
    }

    /**
     * The same figure as {@link #multiplier()}, as the shared settlement path wants it.
     *
     * <p>Slots have no push — every outcome is a win or a total loss — so this is only ever 0 or
     * well above 1.
     */
    @Override
    public double totalReturnMultiplier() {
        return multiplier;
    }

    @Override
    public String describe() {
        if (isJackpot()) {
            return "JACKPOT — three sevens!";
        }
        return isWin() ? "Pays " + multiplier + "x" : "No luck.";
    }

    /** True when the player gets anything back. */
    @Override
    public boolean isWin() {
        return multiplier > 0;
    }

    /** True for three of the same symbol, as opposed to the two-cherry consolation. */
    public boolean isTriple() {
        return reels[0] == reels[1] && reels[1] == reels[2];
    }

    /** True for the top prize, which is worth telling the whole server about. */
    public boolean isJackpot() {
        return isTriple() && reels[0] == SlotSymbol.SEVEN;
    }

    /**
     * What a bet returns, rounded to whole cents.
     *
     * <p>Rounded <b>down</b>: a fractional cent that rounds up is a fraction of a cent created from
     * nothing on every win, which is exactly the sort of thing that turns into real money at the
     * volume a slot machine runs at.
     */
    public double payoutFor(double bet) {
        return Math.floor(bet * multiplier * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        return "SlotSpin" + Arrays.toString(reels) + " x" + multiplier;
    }
}
