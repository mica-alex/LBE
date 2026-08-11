package com.micatechnologies.minecraft.lbe.casino.war;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import java.util.Random;

/**
 * Casino War: one card each against the dealer, high card wins even money, a tie pushes.
 *
 * <p>Ported from the Discord bot's {@code war_game.py}, rules unchanged — including the tie, which
 * its own comment marks as "casual".
 *
 * <p><b>These rules have no house edge.</b> Player and dealer draw from the same deck, so by
 * symmetry a win is exactly as likely as a loss, and a tie returns the stake. The return to player
 * is exactly 1.0 — see {@link #returnToPlayer()}.
 *
 * <p>A real casino does not play it this way. There, a tie either loses outright or forces a "war":
 * the player doubles their stake, three cards are burned, and one more card each decides it. That
 * one rule is the entire house edge in the real game, worth about 2.9%. The bot dropped it, which
 * is a reasonable call for a Discord score and a consequential one for a server economy.
 */
public final class WarGame {

    /** What beating the dealer returns, "for 1". Even money. */
    public static final double WIN_MULTIPLIER = 2.0;

    /** What a tie returns: the stake, and nothing more. */
    public static final double PUSH_MULTIPLIER = 1.0;

    private WarGame() {
        throw new AssertionError("No instances.");
    }

    /** Deals one card each and compares them. */
    public static Result play(Random random) {
        Deck deck = new Deck(random);
        return new Result(deck.deal(), deck.deal());
    }

    /**
     * Compares two known cards, without dealing.
     *
     * <p>For a client re-displaying a result the server already decided, and for stating an exact
     * pair in a test rather than shuffling until one turns up.
     */
    public static Result evaluate(Card player, Card dealer) {
        return new Result(player, dealer);
    }

    /**
     * The long-run fraction of money wagered that comes back: exactly 1.0.
     *
     * <p>Worked out from the deck rather than assumed. Of the 52×51 ordered ways to deal two cards,
     * the tie count is 52×3 (each card has three others of its rank), and by symmetry the remaining
     * deals split evenly into wins and losses. So
     * {@code P(win) × 2 + P(tie) × 1 = (1 - P(tie))/2 × 2 + P(tie) = 1}, whatever P(tie) happens to
     * be — the tie cancels out entirely, which is exactly why the real game does not push on it.
     */
    public static double returnToPlayer() {
        double tie = tieProbability();
        double win = (1.0 - tie) / 2.0;
        return win * WIN_MULTIPLIER + tie * PUSH_MULTIPLIER;
    }

    /** The chance both cards share a rank: 3/51. */
    public static double tieProbability() {
        return 3.0 / 51.0;
    }

    /** One deal. */
    public static final class Result implements GameResult {

        private final Card player;
        private final Card dealer;

        Result(Card player, Card dealer) {
            this.player = player;
            this.dealer = dealer;
        }

        public Card player() {
            return player;
        }

        public Card dealer() {
            return dealer;
        }

        @Override
        public double totalReturnMultiplier() {
            if (player.value() > dealer.value()) {
                return WIN_MULTIPLIER;
            }
            return player.value() == dealer.value() ? PUSH_MULTIPLIER : 0.0;
        }

        @Override
        public String describe() {
            String hands = player + " vs " + dealer + " — ";
            if (player.value() > dealer.value()) {
                return hands + "you win!";
            }
            return player.value() == dealer.value() ? hands + "a tie; your bet is returned."
                : hands + "the dealer takes it.";
        }
    }
}
