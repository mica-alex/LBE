package com.micatechnologies.minecraft.lbe.casino.war;

import com.micatechnologies.minecraft.lbe.casino.CasinoOdds;
import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import java.util.Random;

/**
 * Casino War: one card each against the dealer, high card wins, a tie returns the stake.
 *
 * <p>Ported from the Discord bot's {@code war_game.py}, with <b>one deliberate rules change</b>: the
 * win no longer pays flat even money.
 *
 * <p>Under the bot's rules the game returned exactly 100%, and the tie was why. Player and dealer
 * draw from the same deck, so a win is exactly as likely as a loss; handing the stake back on a tie
 * then made the whole thing wash out no matter how often ties came up. A real casino resolves a tie
 * by going to "war" — the player doubles, three cards burn, one more card each decides it — and that
 * single rule is its entire ~2.9% edge.
 *
 * <p>Rather than bolt a second decision onto a one-click game, the push is kept and paid for out of
 * the win: {@link #WIN_MULTIPLIER} is priced so that wins and pushes together return
 * {@link CasinoOdds#STANDARD_RETURN}. Same game to play, same friendly tie, and the house now keeps
 * 3%.
 */
public final class WarGame {

    /**
     * What beating the dealer returns, "for 1": about 1.94.
     *
     * <p>Priced, not chosen. A win happens on 24 of every 51 deals and a tie on 3, so this is what
     * {@link CasinoOdds#payoutFor} says a win must pay for the two together to return 97% — the
     * push is not free, and pretending it was is what made the bot's version break even.
     */
    public static final double WIN_MULTIPLIER = CasinoOdds.round(
        CasinoOdds.payoutFor((1.0 - 3.0 / 51.0) / 2.0, 3.0 / 51.0));

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
     * The long-run fraction of money wagered that comes back: 97%.
     *
     * <p>Worked out from the deck rather than assumed. Of the 52×51 ordered ways to deal two cards
     * the tie count is 52×3 — each card has three others of its rank — and by symmetry the rest
     * split evenly into wins and losses. So {@code P(win) × WIN_MULTIPLIER + P(tie) × 1}, which is
     * the equation {@link #WIN_MULTIPLIER} was solved from and is therefore true by construction.
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
