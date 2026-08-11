package com.micatechnologies.minecraft.lbe.casino.highlow;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import java.util.Random;

/**
 * A card is shown; call whether the next one is higher or lower. Even money, ties push.
 *
 * <p>Ported from the Discord bot's {@code highlow_game.py}, rules unchanged — its own docstring
 * calls them "casual rules — no odds-weighted payout", and that phrase is doing a great deal of
 * work.
 *
 * <h2>⚠ These rules pay the player 150% of everything they wager</h2>
 *
 * <p>The player sees the base card <b>before</b> choosing a direction, and both directions pay the
 * same. So the correct move is always "pick whichever side has more cards left in the deck", and
 * that is not a subtle edge — on a base of 2 you call higher and win 48 times in 51.
 *
 * <p>Worked out exactly in {@link #returnToPlayerWithOptimalPlay()}: <b>1.5068</b>. Every dollar
 * staked returns a dollar and a half. A player who notices — and it takes one hand to notice — turns
 * the machine into an income of 50% per press of a button, limited only by the server's maximum bet
 * and how fast they can click.
 *
 * <p>{@link #returnToPlayerWithRandomPlay()} is exactly 1.0, so even a player choosing at random
 * costs the house nothing and gains it nothing. There is no way to play this badly enough to lose
 * money over time.
 *
 * <p><b>This is faithful to the bot and unsuitable for a live economy.</b> It is fine against a
 * Discord score, where the currency is engagement and inflation costs nobody anything. It is not
 * fine against a balance that also buys plots. The conventional fix is an odds-weighted payout —
 * pay the inverse of the actual chance, times a house factor — which turns the free choice into a
 * priced one and makes every base card equally (un)attractive. That is a rules change, so it is not
 * made here; see the warning this ships with in the config and the note in
 * {@code docs/design/CASINO.md}.
 */
public final class HighLowGame {

    /** What a correct call returns, "for 1". Even money, per the bot. */
    public static final double WIN_MULTIPLIER = 2.0;

    /** What an equal rank returns: the stake back. */
    public static final double PUSH_MULTIPLIER = 1.0;

    /** Which way the next card is called to go. */
    public enum Call {
        HIGHER,
        LOWER
    }

    private final Card base;
    private final Deck deck;
    private boolean resolved;

    /** Deals the base card and waits for a call. */
    public HighLowGame(Random random) {
        this.deck = new Deck(random);
        this.base = deck.deal();
    }

    /** The card on show. */
    public Card base() {
        return base;
    }

    /**
     * Turns the next card and settles.
     *
     * @throws IllegalStateException if called twice. A second call would deal another card against
     *     a stake that has already been settled, which is money moved for a game nobody played.
     */
    public Result call(Call call) {
        if (resolved) {
            throw new IllegalStateException("This hand has already been resolved");
        }
        resolved = true;
        return new Result(base, deck.deal(), call);
    }

    /**
     * How many of the 51 unseen cards beat {@code card}.
     *
     * <p>Public because it is the number a player works out in their head, and a screen that shows
     * it is being honest rather than generous — see the class warning.
     */
    public static int cardsAbove(Card card) {
        return 4 * (Rank.ACE.value() - card.value());
    }

    /** How many of the 51 unseen cards are below {@code card}. */
    public static int cardsBelow(Card card) {
        return 4 * (card.value() - Rank.TWO.value());
    }

    /** How many of the 51 unseen cards tie with {@code card}: always 3. */
    public static int cardsEqual() {
        return 3;
    }

    /**
     * The return to a player who always calls the side with more cards left: <b>1.5068</b>.
     *
     * <p>Computed, not estimated. Each of the 13 ranks is equally likely as a base card; for each,
     * the better side holds {@code max(above, below)} of the 51 unseen cards and three tie. The
     * favourable counts across the ranks are 48, 44, 40, 36, 32, 28, 24, 28, 32, 36, 40, 44, 48 —
     * summing to 480 — so the return is {@code (2 × 480 + 13 × 3) / (13 × 51)}.
     */
    public static double returnToPlayerWithOptimalPlay() {
        int favourableTotal = 0;
        for (Rank rank : Rank.values()) {
            Card card = new Card(rank, com.micatechnologies.minecraft.lbe.casino.cards.Suit.SPADES);
            favourableTotal += Math.max(cardsAbove(card), cardsBelow(card));
        }
        int ranks = Rank.values().length;
        return (WIN_MULTIPLIER * favourableTotal + ranks * cardsEqual())
            / (double) (ranks * 51);
    }

    /**
     * The return to a player who calls at random: exactly 1.0.
     *
     * <p>The floor, in other words. Averaged over both directions the favourable count is always
     * half of the 48 non-tying cards, so the ties cancel exactly as they do in war. There is no way
     * to play this game badly enough to lose money to the house over time.
     */
    public static double returnToPlayerWithRandomPlay() {
        int ranks = Rank.values().length;
        return (WIN_MULTIPLIER * (48.0 / 2.0) * ranks + ranks * cardsEqual())
            / (double) (ranks * 51);
    }

    /** One resolved hand. */
    public static final class Result implements GameResult {

        private final Card base;
        private final Card next;
        private final Call call;

        Result(Card base, Card next, Call call) {
            this.base = base;
            this.next = next;
            this.call = call;
        }

        public Card base() {
            return base;
        }

        public Card next() {
            return next;
        }

        public Call call() {
            return call;
        }

        @Override
        public double totalReturnMultiplier() {
            if (next.value() == base.value()) {
                return PUSH_MULTIPLIER;
            }
            boolean higher = next.value() > base.value();
            boolean correct = (call == Call.HIGHER) == higher;
            return correct ? WIN_MULTIPLIER : 0.0;
        }

        @Override
        public String describe() {
            String hands = base + " then " + next + " — ";
            if (next.value() == base.value()) {
                return hands + "a tie; your bet is returned.";
            }
            return totalReturnMultiplier() > 0.0 ? hands + "you called it!" : hands + "wrong call.";
        }
    }
}
