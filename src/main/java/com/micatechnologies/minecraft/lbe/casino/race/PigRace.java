package com.micatechnologies.minecraft.lbe.casino.race;

import java.util.Random;

/**
 * Pig racing: six pigs, one race for everyone at the board.
 *
 * <p><b>Fixed odds, not a pool.</b> A pari-mutuel pool only works with a crowd: a player racing
 * alone who backs the winner gets back 90% of a pool made of their own bet. So each pig has a
 * chance of winning and a price, set together so that every bet returns exactly 90% whether one
 * person bets or fifty. The favourite pays little and often, the long shot a lot and rarely.
 *
 * <p>Pure, with no Minecraft types. The race's look — who leads when, by how much — is drawn from
 * a seed on the client; only the winner is decided here, on the server.
 */
public final class PigRace {

    /** What every bet returns per unit staked, over time: the house keeps 10%. */
    public static final double RETURN = 0.90;

    /** A runner: its name, its chance of winning, and so its price. */
    public enum Pig {
        BACON("Bacon", 0.30),
        TRUFFLE("Truffle", 0.25),
        OINK_FLOYD("Oink Floyd", 0.18),
        PORKCHOP("Porkchop", 0.12),
        HAMLET("Hamlet", 0.10),
        SIR_SQUEALS("Sir Squeals", 0.05);

        private final String displayName;
        private final double chance;

        Pig(String displayName, double chance) {
            this.displayName = displayName;
            this.chance = chance;
        }

        public String displayName() {
            return displayName;
        }

        /** Probability this pig wins a race. */
        public double chance() {
            return chance;
        }

        /** What a winning bet returns per unit staked: priced so the bet returns {@link #RETURN}. */
        public double returnMultiplier() {
            return Math.round(RETURN / chance * 100.0) / 100.0;
        }

        /** The pig for a code from the screen, or null. */
        public static Pig byCode(int code) {
            Pig[] all = values();
            return code >= 0 && code < all.length ? all[code] : null;
        }
    }

    private PigRace() {
    }

    /** Runs a race: the winner, drawn by each pig's chance. */
    public static Pig race(Random random) {
        double roll = random.nextDouble();
        double seen = 0.0;
        for (Pig pig : Pig.values()) {
            seen += pig.chance;
            if (roll < seen) {
                return pig;
            }
        }
        return Pig.BACON;   // only reachable through floating-point dust at the very top
    }

    /** What a bet on {@code backed} returns per unit when {@code winner} wins. */
    public static double returnFor(Pig backed, Pig winner) {
        return backed == winner ? backed.returnMultiplier() : 0.0;
    }

    /** A bet's long-run return. */
    public static double returnToPlayer(Pig backed) {
        return backed.chance * backed.returnMultiplier();
    }
}
