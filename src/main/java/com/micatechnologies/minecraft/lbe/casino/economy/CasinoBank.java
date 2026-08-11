package com.micatechnologies.minecraft.lbe.casino.economy;

import java.util.OptionalDouble;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Where the casino's money comes from, described without naming who provides it.
 *
 * <p><b>This interface exists to keep SUM optional.</b> Not one type in this file comes from SUM, so
 * every class that plays a game can be compiled, loaded and run on a pack that has never heard of
 * it. The single implementation that does name SUM types is {@link SumEconomyBridge}, and
 * {@link LbeEconomy} never mentions that class unless Forge confirms the mod is installed — so on a
 * pack without SUM it is never loaded, and its absent supertypes never have to resolve.
 *
 * <p>Get that wrong and the failure is not a friendly startup message. It is a
 * {@code NoClassDefFoundError} thrown the first time somebody right-clicks a slot machine, on a
 * server that booted perfectly an hour earlier.
 *
 * <p>Server thread only, like everything that touches a balance.
 */
public interface CasinoBank {

    /** True when a bet placed right now would actually be able to move money. */
    boolean isReady();

    /** Why {@link #isReady()} is false, phrased for a player. Empty when it is true. */
    String unavailableReason();

    /** What the player can spend, or empty when that cannot be determined. */
    OptionalDouble balance(EntityPlayer player);

    /** The currency symbol to put in front of an amount, e.g. {@code "$"}. */
    String currencySymbol();

    /**
     * Takes {@code amount} from the player and holds it while a game resolves.
     *
     * <p>The money is gone from their wallet the moment this returns a wager, so it cannot be spent
     * twice while the reels are still turning, and a crash mid-game does not simply eat it.
     *
     * @return the held stake, or {@code null} if it could not be taken — in which case nothing moved
     *     and {@link #lastFailure()} says why.
     */
    Wager stake(EntityPlayer player, double amount, String reason);

    /**
     * Why the last {@link #stake} returned {@code null}, phrased for a player.
     *
     * <p>Read it immediately after the failed call, on the same thread. It is the message to show
     * whoever just tried to bet.
     */
    String lastFailure();
}
