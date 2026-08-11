package com.micatechnologies.minecraft.lbe.casino.economy;

/**
 * A stake that has left a player's wallet and is waiting to find out what happened to it.
 *
 * <p>Exactly one of {@link #payOut}, {@link #loseToHouse} or {@link #cancel} must be called, once.
 * Leaving a wager unsettled leaves real money held with nothing coming to claim it — the game's
 * equivalent of a leaked file handle, except a player can see the hole in their balance.
 *
 * <p>No SUM types here, deliberately. See {@link CasinoBank}.
 */
public interface Wager {

    /** What was staked, after rounding. The amount actually taken from the wallet. */
    double amount();

    /**
     * The player won: return the stake and pay {@code totalReturn} in total, "for 1".
     *
     * <p>Pass the full return, not the profit — {@code totalReturn} of {@code amount()} is a push,
     * and {@code 5 * amount()} on a $10 bet hands back $50. The implementation works out how much
     * of that is the returned stake and how much is new money from the house.
     *
     * @return true when the player has been paid. False means the stake is still held and the
     *     player has not been paid, which is worth logging and telling them about.
     */
    boolean payOut(double totalReturn);

    /**
     * The player lost: the house keeps the stake and the money leaves the economy.
     *
     * @return true when settled.
     */
    boolean loseToHouse();

    /**
     * The game did not happen — give it all back.
     *
     * <p>For anything that goes wrong between taking the stake and resolving the game. A refund is
     * always the right answer there: the player never got the thing they paid for.
     *
     * @return true when refunded.
     */
    boolean cancel();
}
