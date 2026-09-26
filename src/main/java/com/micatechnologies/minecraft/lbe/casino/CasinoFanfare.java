package com.micatechnologies.minecraft.lbe.casino;

/**
 * How loudly a finished round deserves to be celebrated.
 *
 * <p>Purely presentational: nothing that moves money reads this. It exists so the screen, which
 * plays the player's own sounds, and the server, which plays everyone else's and makes the chat
 * announcement, agree on what a "big win" is without syncing any config. Both derive it from the
 * same two facts about the round, and both facts reach the client anyway.
 *
 * <p>Pure, no Minecraft types, like the rest of {@code casino/}.
 */
public enum CasinoFanfare {

    /** The stake is gone. */
    LOSS,

    /** The stake came back and nothing else happened. */
    PUSH,

    /** Anything above the stake, up to {@link #BIG_WIN_MULTIPLIER}. */
    WIN,

    /** At least {@link #BIG_WIN_MULTIPLIER}: worth turning heads at the next machine. */
    BIG_WIN,

    /** The top prize, worth telling the whole server about. */
    JACKPOT;

    /** Returning this many times the stake, or more, is a big win. */
    public static final double BIG_WIN_MULTIPLIER = 10.0;

    /**
     * Returning this many times the stake, or more, is a jackpot on any game without a top prize of
     * its own. Rare enough on every game here to be an event.
     */
    public static final double JACKPOT_MULTIPLIER = 50.0;

    /**
     * How long, in ticks, the screen animates a round before it shows the result.
     *
     * <p>The server settles the money the moment the bet arrives, but the player only learns the
     * outcome when this runs out. Anything that announces the result — a sound other players hear,
     * a chat line — has to wait this long too, or it gives the result away before the reels stop.
     */
    public static final int REVEAL_TICKS = 44;

    /**
     * How long a shared round's spin or race runs before its result shows. Longer than a machine's
     * reveal: a crowd is watching, and the wait is the show.
     */
    public static final int SHARED_REVEAL_TICKS = 100;

    /** How long a shared round takes bets after the first one, in ticks. */
    public static final int BETTING_WINDOW_TICKS = 20 * 20;

    /** When each slot reel stops spinning, in ticks into the reveal. Staggered so they land 1-2-3. */
    public static final int[] REEL_STOP_TICKS = {24, 34, 44};

    /**
     * Classifies a finished round.
     *
     * @param multiplier what the round returned per unit staked, "for 1"
     * @param topPrize whether the game says this is its top prize. Slots has a specific one (three
     *     sevens); every other game passes {@code false} and relies on {@link #JACKPOT_MULTIPLIER}.
     */
    public static CasinoFanfare of(double multiplier, boolean topPrize) {
        if (topPrize || multiplier >= JACKPOT_MULTIPLIER) {
            return JACKPOT;
        }
        if (multiplier >= BIG_WIN_MULTIPLIER) {
            return BIG_WIN;
        }
        if (multiplier > 1.0) {
            return WIN;
        }
        return multiplier == 1.0 ? PUSH : LOSS;
    }

    /** True for the outcomes other players nearby get to hear about. */
    public boolean isHeardByBystanders() {
        return this == WIN || this == BIG_WIN || this == JACKPOT;
    }
}
