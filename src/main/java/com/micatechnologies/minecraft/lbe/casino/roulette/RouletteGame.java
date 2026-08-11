package com.micatechnologies.minecraft.lbe.casino.roulette;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * European single-zero roulette: 37 pockets, 0 to 36.
 *
 * <p>Ported from the Discord bot's {@code roulette_game.py}, bets and payouts unchanged: a straight
 * number pays 36, the even-money bets pay 2, a dozen pays 3, all "for 1".
 *
 * <p><b>This one is properly built.</b> Every bet type returns the same 97.30%, which is not a
 * coincidence — it is what single-zero roulette is, and it comes from paying true odds on 36 numbers
 * while spinning 37. The zero is the entire house edge, and it applies evenly, so no bet here is
 * better than another and a player cannot shop for one. That is the property the games ported
 * alongside it are missing.
 */
public final class RouletteGame {

    /** Pockets on the wheel: 0-36. A single zero, so European rather than American. */
    public static final int POCKETS = 37;

    /** The red numbers. Everything else except zero is black. */
    private static final Set<Integer> RED = new HashSet<>(Arrays.asList(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36));

    private RouletteGame() {
        throw new AssertionError("No instances.");
    }

    /** What colour a pocket is. */
    public enum Colour {
        RED,
        BLACK,
        GREEN
    }

    /** The kinds of bet the table accepts. */
    public enum BetType {
        /** One number, 0-36. Pays 36. */
        STRAIGHT(36.0),
        RED(2.0),
        BLACK(2.0),
        EVEN(2.0),
        ODD(2.0),
        /** 1-18. */
        LOW(2.0),
        /** 19-36. */
        HIGH(2.0),
        /** One of the three twelves. Pays 3. */
        DOZEN(3.0);

        private final double multiplier;

        BetType(double multiplier) {
            this.multiplier = multiplier;
        }

        /** What this bet returns when it wins, "for 1". */
        public double multiplier() {
            return multiplier;
        }

        /** Whether this bet needs a number alongside it. */
        public boolean needsValue() {
            return this == STRAIGHT || this == DOZEN;
        }
    }

    public static Colour colourOf(int pocket) {
        if (pocket == 0) {
            return Colour.GREEN;
        }
        return RED.contains(pocket) ? Colour.RED : Colour.BLACK;
    }

    /** Spins the wheel and settles one bet. */
    public static Result spin(BetType type, int value, Random random) {
        return new Result(type, value, random.nextInt(POCKETS));
    }

    /** Whether {@code value} is a legal companion to {@code type}. */
    public static boolean isValidValue(BetType type, int value) {
        if (type == BetType.STRAIGHT) {
            return value >= 0 && value <= 36;
        }
        if (type == BetType.DOZEN) {
            return value >= 1 && value <= 3;
        }
        return true;
    }

    /** How many of the 37 pockets win this bet. */
    public static int winningPockets(BetType type) {
        switch (type) {
            case STRAIGHT:
                return 1;
            case DOZEN:
                return 12;
            default:
                // Every even-money bet covers 18 pockets; zero is in none of them, which is the
                // whole point of it.
                return 18;
        }
    }

    /**
     * The long-run fraction of money wagered that comes back for {@code type}: 36/37 for all of
     * them, or 97.30%.
     */
    public static double returnToPlayer(BetType type) {
        return winningPockets(type) * type.multiplier() / POCKETS;
    }

    /** One spin against one bet. */
    public static final class Result implements GameResult {

        private final BetType type;
        private final int value;
        private final int pocket;

        Result(BetType type, int value, int pocket) {
            this.type = type;
            this.value = value;
            this.pocket = pocket;
        }

        public int pocket() {
            return pocket;
        }

        public Colour colour() {
            return colourOf(pocket);
        }

        public BetType type() {
            return type;
        }

        private boolean won() {
            switch (type) {
                case STRAIGHT:
                    return pocket == value;
                case RED:
                    return colourOf(pocket) == Colour.RED;
                case BLACK:
                    return colourOf(pocket) == Colour.BLACK;
                case EVEN:
                    // Zero is not even here. It is not anything — that is the house's pocket.
                    return pocket != 0 && pocket % 2 == 0;
                case ODD:
                    return pocket != 0 && pocket % 2 == 1;
                case LOW:
                    return pocket >= 1 && pocket <= 18;
                case HIGH:
                    return pocket >= 19 && pocket <= 36;
                case DOZEN:
                    int low = (value - 1) * 12 + 1;
                    return pocket >= low && pocket <= low + 11;
                default:
                    return false;
            }
        }

        @Override
        public double totalReturnMultiplier() {
            return won() ? type.multiplier() : 0.0;
        }

        @Override
        public String describe() {
            String colour = colour() == Colour.GREEN ? "green"
                : colour() == Colour.RED ? "red" : "black";
            return pocket + " " + colour + " — " + (won() ? "a winner!" : "not this time.");
        }
    }
}
