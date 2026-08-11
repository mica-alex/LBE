package com.micatechnologies.minecraft.lbe.casino;

/**
 * What a finished game owes the player, expressed the one way the money layer understands.
 *
 * <p>Every game in the casino resolves to a single number: how much the player gets back per unit
 * staked, <b>"for 1"</b>. A $10 bet with a multiplier of 2 returns $20, of which $10 is profit.
 *
 * <ul>
 *   <li>{@code 0} — a loss. The stake is forfeited and the money leaves the economy.</li>
 *   <li>{@code 1} — a push. The stake comes back and nothing else happens.</li>
 *   <li>{@code > 1} — a win. The stake comes back plus new money from the house.</li>
 * </ul>
 *
 * <p>Keeping every game behind this one number is what lets a single settlement path serve all of
 * them, and it is why adding a game does not mean touching anything that moves money. It is also
 * what makes a game's economics computable: sum the multiplier over every outcome weighted by its
 * probability and you have the return to player, which every game here is required to state.
 */
public interface GameResult {

    /**
     * What the player gets back per unit staked, "for 1". Never negative.
     *
     * <p>Not the profit. A game that returns the stake exactly reports {@code 1}, not {@code 0}.
     */
    double totalReturnMultiplier();

    /** True when the stake comes back untouched and no money changes hands. */
    default boolean isPush() {
        return totalReturnMultiplier() == 1.0;
    }

    /** True when the player gets anything at all back, including a push. */
    default boolean isWin() {
        return totalReturnMultiplier() > 1.0;
    }

    /** True when the stake is gone. */
    default boolean isLoss() {
        return totalReturnMultiplier() <= 0.0;
    }

    /** One short line describing what happened, for the screen and the log. */
    String describe();
}
