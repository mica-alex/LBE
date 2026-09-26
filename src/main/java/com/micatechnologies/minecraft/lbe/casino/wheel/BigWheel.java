package com.micatechnologies.minecraft.lbe.casino.wheel;

import java.util.Random;

/**
 * The big wheel: fifty segments, one spin for everyone at the table.
 *
 * <h2>Priced so every bet is the same bet</h2>
 *
 * <p>A classic Big Six wheel pays each segment differently badly: 11% on the dollar, 22% on the
 * joker. That makes some bets simply worse than others, which this casino does not do (see
 * {@code CasinoOdds}). Here the counts and payouts are chosen together so that
 * {@code count x (pays + 1)} is 48 for every segment:
 *
 * <pre>
 *   1:1 x24   3:1 x12   7:1 x6   15:1 x3   23:1 x2   47:1 x1   (48 segments)
 * </pre>
 *
 * <p>plus two house segments nobody can back. Every bet therefore returns exactly 48/50 = 96%,
 * and the difference between them is only how often it pays and how much.
 *
 * <p>Pure, with no Minecraft types.
 */
public final class BigWheel {

    /** What a segment pays, and how many of it are on the wheel. */
    public enum Segment {
        ONE(1, 24),
        THREE(3, 12),
        SEVEN(7, 6),
        FIFTEEN(15, 3),
        TWENTY_THREE(23, 2),
        FORTY_SEVEN(47, 1),
        /** The house's segments: nobody can back them, and they pay nothing. */
        HOUSE(0, 2);

        private final int pays;
        private final int count;

        Segment(int pays, int count) {
            this.pays = pays;
            this.count = count;
        }

        /** Pays this many to one. */
        public int pays() {
            return pays;
        }

        /** How many of this segment the wheel has. */
        public int count() {
            return count;
        }

        /** What a winning bet on it returns per unit staked, "for 1". */
        public double returnMultiplier() {
            return pays + 1.0;
        }

        /** Whether players can back it. */
        public boolean isBettable() {
            return this != HOUSE;
        }

        /** "7:1", for a button. */
        public String label() {
            return this == HOUSE ? "HOUSE" : pays + ":1";
        }

        /** The segment for a code from the screen, or null for one that cannot be backed. */
        public static Segment bettable(int code) {
            Segment[] all = values();
            return code >= 0 && code < all.length && all[code].isBettable() ? all[code] : null;
        }
    }

    /** Segments on the wheel. */
    public static final int SEGMENTS = 50;

    /**
     * The wheel's layout, clockwise from the pointer. Spread so the rare segments are not bunched:
     * each big payer sits among ones and threes, as on a real wheel.
     */
    private static final Segment[] LAYOUT = buildLayout();

    private BigWheel() {
    }

    private static Segment[] buildLayout() {
        Segment[] layout = new Segment[SEGMENTS];
        // Place the rare segments first at evenly spaced positions, then fill the gaps alternating
        // ones and threes.
        Segment[] rare = {
            Segment.FORTY_SEVEN, Segment.SEVEN, Segment.FIFTEEN, Segment.SEVEN, Segment.HOUSE,
            Segment.SEVEN, Segment.TWENTY_THREE, Segment.SEVEN, Segment.FIFTEEN, Segment.SEVEN,
            Segment.HOUSE, Segment.SEVEN, Segment.TWENTY_THREE, Segment.FIFTEEN,
        };
        for (int i = 0; i < rare.length; i++) {
            layout[(i * SEGMENTS) / rare.length] = rare[i];
        }
        int ones = Segment.ONE.count;
        int threes = Segment.THREE.count;
        boolean one = true;
        for (int i = 0; i < SEGMENTS; i++) {
            if (layout[i] != null) {
                continue;
            }
            // Mostly ones, a three every other gap, until each runs out.
            if ((one && ones > 0) || threes == 0) {
                layout[i] = Segment.ONE;
                ones--;
            } else {
                layout[i] = Segment.THREE;
                threes--;
            }
            one = !one || threes * 2 < ones;
        }
        return layout;
    }

    /** The segment at a position on the wheel. */
    public static Segment segmentAt(int index) {
        return LAYOUT[((index % SEGMENTS) + SEGMENTS) % SEGMENTS];
    }

    /** Spins: the position that stops under the pointer. Every position equally likely. */
    public static int spin(Random random) {
        return random.nextInt(SEGMENTS);
    }

    /** What a bet on {@code backed} returns per unit when the wheel stops at {@code landed}. */
    public static double returnFor(Segment backed, int landed) {
        return segmentAt(landed) == backed ? backed.returnMultiplier() : 0.0;
    }

    /** A bet's long-run return: count on the wheel x what it returns, over the whole wheel. */
    public static double returnToPlayer(Segment backed) {
        int count = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            count += segmentAt(i) == backed ? 1 : 0;
        }
        return count * backed.returnMultiplier() / SEGMENTS;
    }
}
