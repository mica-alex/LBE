package com.micatechnologies.minecraft.lbe.casino.block;

/**
 * A wall sign showing the slots' progressive jackpot as it grows.
 *
 * <p>The same panel, tile entity and sync as {@link BlockCasinoLeaderboard}; only what it shows
 * differs. Put one above a bank of slot machines.
 */
public class BlockProgressiveSign extends BlockCasinoLeaderboard {

    public BlockProgressiveSign() {
        super("progressive_sign");
    }
}
