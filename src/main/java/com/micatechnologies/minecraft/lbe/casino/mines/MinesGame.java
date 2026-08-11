package com.micatechnologies.minecraft.lbe.casino.mines;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Mines: turn over safe tiles to grow a multiplier, and stop before you hit one.
 *
 * <p>Ported from the Discord bot's {@code mines_game.py}, with its 24-tile grid and 4% house edge
 * unchanged.
 *
 * <h2>The multiplier is computed, not tabled</h2>
 *
 * <p>Every other game here has a paytable somebody wrote down. This one derives its payout from the
 * odds of having got this far: after revealing {@code r} safe tiles from a grid of {@code g} holding
 * {@code m} mines, the fair multiplier is {@code C(g,r) / C(g-m,r)} — one over the probability of
 * surviving that long — scaled by {@link #HOUSE_EDGE}.
 *
 * <p>That is worth stating plainly because it means <b>the edge is exactly 4% at every single step</b>,
 * whatever the player does. There is no clever stopping point and no bad one; cashing out after one
 * tile and cashing out after fifteen cost the same 4%. The only thing the player's choices change is
 * the variance.
 *
 * <h2>Three steps, not two</h2>
 *
 * <p>Unlike anything before it, a round here has an unbounded number of decisions: the stake is
 * taken at the start, then every reveal is a choice, and cashing out is another. A hand in progress
 * is therefore money in the air for as long as the player likes — which is fine, because the machine
 * refunds an abandoned round exactly as it does for the other in-progress games.
 */
public final class MinesGame {

    /** Tiles on the board. 24, which is a 5×5 grid with one square given to the cash-out button. */
    public static final int GRID_SIZE = 24;

    /** The house's cut, applied to the fair multiplier at every step. */
    public static final double HOUSE_EDGE = 0.04;

    /** Fewest mines a player may choose. One is the gentlest game on offer. */
    public static final int MIN_MINES = 1;

    /** Most mines a player may choose: everything but one tile. */
    public static final int MAX_MINES = GRID_SIZE - 1;

    private final int mineCount;
    private final Set<Integer> mines;
    private final SortedSet<Integer> revealed = new TreeSet<>();
    private boolean busted;
    private boolean finished;

    /**
     * Lays a board.
     *
     * @param mineCount how many mines to hide, clamped into {@link #MIN_MINES}..{@link #MAX_MINES}.
     *     Clamped rather than refused because this arrives from a client, and a nonsense value
     *     should give a playable board rather than strand a stake that is already down.
     */
    public MinesGame(int mineCount, Random random) {
        this.mineCount = Math.max(MIN_MINES, Math.min(MAX_MINES, mineCount));
        List<Integer> tiles = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            tiles.add(i);
        }
        Collections.shuffle(tiles, random);
        this.mines = new HashSet<>(tiles.subList(0, this.mineCount));
    }

    /**
     * The multiplier after revealing {@code revealed} safe tiles, "for 1".
     *
     * <p>{@code C(grid, revealed) / C(grid - mines, revealed)} is one over the chance of surviving
     * that many picks, which is the fair price; the rest is the house's 4%.
     */
    public static double multiplierFor(int grid, int mines, int revealed) {
        if (revealed <= 0) {
            return 1.0;
        }
        double fair = choose(grid, revealed) / choose(grid - mines, revealed);
        return Math.round(fair * (1.0 - HOUSE_EDGE) * 100.0) / 100.0;
    }

    /** How many safe tiles this board holds. */
    public int safeTotal() {
        return GRID_SIZE - mineCount;
    }

    public int mineCount() {
        return mineCount;
    }

    /** Tiles turned over so far, in order of index. */
    public SortedSet<Integer> revealed() {
        return Collections.unmodifiableSortedSet(revealed);
    }

    /** Where the mines are. Only for the reveal after a round ends — never send this mid-round. */
    public SortedSet<Integer> mines() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(mines));
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isBusted() {
        return busted;
    }

    /** What cashing out right now would pay, "for 1". */
    public double currentMultiplier() {
        return multiplierFor(GRID_SIZE, mineCount, revealed.size());
    }

    /** What one more safe tile would make it. Equal to the current one on a cleared board. */
    public double nextMultiplier() {
        return revealed.size() >= safeTotal() ? currentMultiplier()
            : multiplierFor(GRID_SIZE, mineCount, revealed.size() + 1);
    }

    /**
     * Turns over a tile.
     *
     * @return a finished {@link Result} when this ended the round — by hitting a mine or by
     *     clearing the last safe tile — or {@code null} when the round continues.
     * @throws IllegalStateException if the round is already over.
     */
    public Result reveal(int tile) {
        if (finished) {
            throw new IllegalStateException("This round has already ended");
        }
        if (tile < 0 || tile >= GRID_SIZE || revealed.contains(tile)) {
            return null;   // a misclick, not a move: nothing changes and the round goes on
        }
        if (mines.contains(tile)) {
            busted = true;
            finished = true;
            return new Result(this, 0.0);
        }
        revealed.add(tile);
        if (revealed.size() >= safeTotal()) {
            // Cleared the board — there is nothing left to risk, so it pays out automatically.
            return cashOut();
        }
        return null;
    }

    /**
     * Stops and takes the current multiplier.
     *
     * @throws IllegalStateException if the round is over, or nothing has been revealed — cashing out
     *     at 1.0 would be a bet placed and instantly returned, which is not a thing to allow by
     *     accident.
     */
    public Result cashOut() {
        if (finished) {
            throw new IllegalStateException("This round has already ended");
        }
        if (revealed.isEmpty()) {
            throw new IllegalStateException("Reveal at least one tile before cashing out");
        }
        finished = true;
        return new Result(this, currentMultiplier());
    }

    /** {@code n choose k}, as a double — exact enough for a 24-tile board. */
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

    /** A finished round. */
    public static final class Result implements GameResult {

        private final int mineCount;
        private final int revealedCount;
        private final boolean busted;
        private final SortedSet<Integer> mines;
        private final SortedSet<Integer> revealed;
        private final double multiplier;

        Result(MinesGame game, double multiplier) {
            this.mineCount = game.mineCount;
            this.revealedCount = game.revealed.size();
            this.busted = game.busted;
            this.mines = new TreeSet<>(game.mines);
            this.revealed = new TreeSet<>(game.revealed);
            this.multiplier = multiplier;
        }

        public boolean busted() {
            return busted;
        }

        public int revealedCount() {
            return revealedCount;
        }

        /** Where every mine was. Safe to show now that the round is over. */
        public SortedSet<Integer> mines() {
            return Collections.unmodifiableSortedSet(mines);
        }

        public SortedSet<Integer> revealed() {
            return Collections.unmodifiableSortedSet(revealed);
        }

        @Override
        public double totalReturnMultiplier() {
            return multiplier;
        }

        @Override
        public String describe() {
            if (busted) {
                return revealedCount == 0 ? "A mine, first tile. Brutal."
                    : "Mine after " + revealedCount + " safe "
                        + (revealedCount == 1 ? "tile" : "tiles") + ".";
            }
            return "Cashed out at " + String.format(java.util.Locale.ROOT, "%.2f", multiplier)
                + "x after " + revealedCount + " " + (revealedCount == 1 ? "tile" : "tiles") + ".";
        }
    }
}
