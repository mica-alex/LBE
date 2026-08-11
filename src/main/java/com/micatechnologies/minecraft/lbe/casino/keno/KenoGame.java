package com.micatechnologies.minecraft.lbe.casino.keno;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Keno: pick up to ten numbers from eighty, twenty are drawn, matches pay.
 *
 * <p>Ported from the Discord bot's {@code keno_game.py} and the {@code KENO_PAYTABLE} it reads from
 * {@code ServerLeaderboard}, unchanged.
 *
 * <p>Unlike most of the games here there is no single house edge — each pick count is its own bet
 * with its own return, ranging from about 74% to 95%. {@link #returnToPlayer(int)} computes each one
 * exactly from the hypergeometric distribution, and a test pins every row, because a paytable this
 * shape is very easy to mistype into a money printer: one digit in the ten-pick row is the
 * difference between a 10,000× jackpot that costs 0.3% of turnover and one that costs 30%.
 */
public final class KenoGame {

    /** Numbers on the board, 1-80. */
    public static final int BOARD_SIZE = 80;

    /** How many are drawn each game. */
    public static final int DRAW_COUNT = 20;

    /** The most a player may pick. */
    public static final int MAX_PICKS = 10;

    /**
     * Payout per pick count and match count, "for 1".
     *
     * <p>{@code PAYTABLE[picks][matches]}. Row 0 is unused so the index reads naturally. Copied
     * verbatim from the bot; a match count past the end of a row pays nothing.
     */
    private static final int[][] PAYTABLE = {
        {},                                              // 0 picks (unused)
        {0, 3},                                          // 1
        {0, 0, 9},                                       // 2
        {0, 0, 2, 25},                                   // 3
        {0, 0, 1, 5, 60},                                // 4
        {0, 0, 0, 3, 15, 200},                           // 5
        {0, 0, 0, 2, 6, 50, 500},                        // 6
        {0, 0, 0, 1, 4, 20, 100, 1000},                  // 7
        {0, 0, 0, 0, 3, 10, 50, 250, 2000},              // 8
        {0, 0, 0, 0, 2, 5, 25, 100, 750, 4000},          // 9
        {0, 0, 0, 0, 0, 3, 15, 50, 250, 1500, 10000},    // 10
    };

    private KenoGame() {
        throw new AssertionError("No instances.");
    }

    /** What {@code matches} out of {@code picks} pays, "for 1". */
    public static int payout(int picks, int matches) {
        if (picks < 1 || picks > MAX_PICKS) {
            return 0;
        }
        int[] row = PAYTABLE[picks];
        return matches >= 0 && matches < row.length ? row[matches] : 0;
    }

    /** Whether a set of picks is playable. */
    public static boolean isValid(SortedSet<Integer> picks) {
        if (picks == null || picks.isEmpty() || picks.size() > MAX_PICKS) {
            return false;
        }
        return picks.first() >= 1 && picks.last() <= BOARD_SIZE;
    }

    /** Draws twenty numbers and settles. */
    public static Result play(SortedSet<Integer> picks, Random random) {
        List<Integer> pool = new ArrayList<>(BOARD_SIZE);
        for (int i = 1; i <= BOARD_SIZE; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, random);
        SortedSet<Integer> drawn = new TreeSet<>(pool.subList(0, DRAW_COUNT));

        int matches = 0;
        for (int pick : picks) {
            if (drawn.contains(pick)) {
                matches++;
            }
        }
        return new Result(new TreeSet<>(picks), drawn, matches);
    }

    /**
     * The chance of exactly {@code matches} when picking {@code picks} numbers.
     *
     * <p>Hypergeometric: of the 80 numbers, 20 are drawn and 60 are not, so the odds of hitting
     * exactly k of your picks is {@code C(20,k) × C(60,picks-k) / C(80,picks)}.
     */
    public static double matchProbability(int picks, int matches) {
        if (matches < 0 || matches > picks || matches > DRAW_COUNT) {
            return 0.0;
        }
        return choose(DRAW_COUNT, matches) * choose(BOARD_SIZE - DRAW_COUNT, picks - matches)
            / choose(BOARD_SIZE, picks);
    }

    /** The long-run fraction of money wagered that comes back at this pick count. */
    public static double returnToPlayer(int picks) {
        double total = 0.0;
        for (int matches = 0; matches <= picks; matches++) {
            total += matchProbability(picks, matches) * payout(picks, matches);
        }
        return total;
    }

    /** {@code n choose k}, as a double — the numbers here run past a long. */
    private static double choose(int n, int k) {
        if (k < 0 || k > n) {
            return 0.0;
        }
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /** One game. */
    public static final class Result implements GameResult {

        private final SortedSet<Integer> picks;
        private final SortedSet<Integer> drawn;
        private final int matches;

        Result(SortedSet<Integer> picks, SortedSet<Integer> drawn, int matches) {
            this.picks = picks;
            this.drawn = drawn;
            this.matches = matches;
        }

        public SortedSet<Integer> picks() {
            return Collections.unmodifiableSortedSet(picks);
        }

        public SortedSet<Integer> drawn() {
            return Collections.unmodifiableSortedSet(drawn);
        }

        public int matches() {
            return matches;
        }

        @Override
        public double totalReturnMultiplier() {
            return payout(picks.size(), matches);
        }

        @Override
        public String describe() {
            return matches + " of " + picks.size() + " — "
                + (totalReturnMultiplier() > 0.0
                    ? "pays " + (long) totalReturnMultiplier() + "x" : "no win.");
        }
    }

    /** The pick counts that have a paytable row, for a screen that wants to list them. */
    public static int[] pickCounts() {
        int[] counts = new int[MAX_PICKS];
        for (int i = 0; i < MAX_PICKS; i++) {
            counts[i] = i + 1;
        }
        return Arrays.copyOf(counts, counts.length);
    }
}
