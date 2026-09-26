package com.micatechnologies.minecraft.lbe.casino.craps;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Random;

/**
 * Craps, with the three bets everyone knows.
 *
 * <ul>
 *   <li><b>Pass line</b>: the come-out roll wins on 7 or 11 and loses on 2, 3 or 12. Anything else
 *       becomes the point, and the shooter rolls until the point comes back (win) or a 7 (lose).
 *       Pays even money: 98.59%.</li>
 *   <li><b>Don't pass</b>: the mirror image, except a 12 on the come-out is a push ("bar 12"),
 *       which is where its edge comes from. Pays even money: 98.64%.</li>
 *   <li><b>Field</b>: one roll. 3, 4, 9, 10 or 11 pay even money, 2 pays double and 12 triple;
 *       5, 6, 7 and 8 lose. 97.22%.</li>
 * </ul>
 *
 * <p>No odds bet. It is the one bet in a casino with no house edge at all, and this casino's rule,
 * enforced by {@code HouseEdgeTest}, is that every bet keeps one.
 *
 * <p>Pure, with no Minecraft types. A pass or don't-pass round that sets a point stays open, and
 * {@link #roll} carries it on one roll at a time.
 */
public final class CrapsGame implements GameResult {

    /** The bets a player can make. The ordinals travel on the wire. */
    public enum Bet {
        PASS("Pass line"),
        DONT_PASS("Don't pass"),
        FIELD("Field");

        private final String label;

        Bet(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Bet byCode(int code) {
            Bet[] all = values();
            return code >= 0 && code < all.length ? all[code] : null;
        }
    }

    private final Bet bet;
    private int die1;
    private int die2;
    private int point;
    private boolean finished;
    private double result;

    private CrapsGame(Bet bet) {
        this.bet = bet;
    }

    /** Makes the bet and throws the first roll. A field bet, or a decisive come-out, ends here. */
    public static CrapsGame start(Bet bet, Random random) {
        CrapsGame game = new CrapsGame(bet);
        game.throwDice(random);
        int total = game.total();
        if (bet == Bet.FIELD) {
            game.finish(fieldReturn(total));
        } else if (total == 7 || total == 11) {
            game.finish(bet == Bet.PASS ? 2.0 : 0.0);
        } else if (total == 2 || total == 3) {
            game.finish(bet == Bet.PASS ? 0.0 : 2.0);
        } else if (total == 12) {
            game.finish(bet == Bet.PASS ? 0.0 : 1.0);   // bar 12: a push for don't pass
        } else {
            game.point = total;
        }
        return game;
    }

    /** Rolls again for the point. Only while the round is open. */
    public void roll(Random random) {
        if (finished) {
            throw new IllegalStateException("The round is over");
        }
        throwDice(random);
        int total = total();
        if (total == point) {
            finish(bet == Bet.PASS ? 2.0 : 0.0);
        } else if (total == 7) {
            finish(bet == Bet.PASS ? 0.0 : 2.0);
        }
    }

    private void throwDice(Random random) {
        die1 = 1 + random.nextInt(6);
        die2 = 1 + random.nextInt(6);
    }

    private void finish(double multiplier) {
        finished = true;
        result = multiplier;
    }

    private static double fieldReturn(int total) {
        switch (total) {
            case 2:
                return 3.0;
            case 12:
                return 4.0;
            case 3:
            case 4:
            case 9:
            case 10:
            case 11:
                return 2.0;
            default:
                return 0.0;
        }
    }

    public Bet bet() {
        return bet;
    }

    public int die1() {
        return die1;
    }

    public int die2() {
        return die2;
    }

    public int total() {
        return die1 + die2;
    }

    /** The point, or 0 before one is set. */
    public int point() {
        return point;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public double totalReturnMultiplier() {
        return finished ? result : 0.0;
    }

    @Override
    public String describe() {
        String dice = die1 + " + " + die2 + " = " + total() + ". ";
        if (!finished) {
            return dice + "The point is " + point + ". Roll again.";
        }
        return dice + (result > 1.0 ? "You win!" : result == 1.0 ? "Push." : "You lose.");
    }

    // ---------------------------------------------------------------------------------------------
    // What each bet returns, exactly
    // ---------------------------------------------------------------------------------------------

    /** Ways to roll each total with two dice, out of 36. */
    private static int ways(int total) {
        return 6 - Math.abs(total - 7);
    }

    /** Chance of making the point {@code p} before a 7. */
    private static double makesPoint(int p) {
        return ways(p) / (double) (ways(p) + ways(7));
    }

    /** A bet's long-run return per unit staked. */
    public static double returnToPlayer(Bet bet) {
        switch (bet) {
            case FIELD: {
                double r = 0.0;
                for (int t = 2; t <= 12; t++) {
                    r += ways(t) / 36.0 * fieldReturn(t);
                }
                return r;
            }
            case PASS: {
                double win = (ways(7) + ways(11)) / 36.0;
                for (int p : new int[] {4, 5, 6, 8, 9, 10}) {
                    win += ways(p) / 36.0 * makesPoint(p);
                }
                return 2.0 * win;
            }
            case DONT_PASS: {
                double win = (ways(2) + ways(3)) / 36.0;
                double push = ways(12) / 36.0;
                for (int p : new int[] {4, 5, 6, 8, 9, 10}) {
                    win += ways(p) / 36.0 * (1.0 - makesPoint(p));
                }
                return 2.0 * win + push;
            }
            default:
                return 0.0;
        }
    }
}
