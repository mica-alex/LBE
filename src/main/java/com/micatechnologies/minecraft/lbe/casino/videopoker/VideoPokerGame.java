package com.micatechnologies.minecraft.lbe.casino.videopoker;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import com.micatechnologies.minecraft.lbe.casino.poker.PokerHand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Jacks or Better video poker: five cards, hold what you like, draw the rest.
 *
 * <p>Ported from the Discord bot's {@code video_poker_game.py} on its {@code VIDEOPOKER_PAYTABLE},
 * unchanged. That table is the classic <b>9/6</b> schedule — a full house pays 9 and a flush pays 6
 * — which is the one every video poker strategy chart in the world is written against.
 *
 * <h2>The only game here where the player's skill matters</h2>
 *
 * <p>Everything else in this casino is a bet and a result. Here the player chooses which cards to
 * keep, and that choice moves the return by several percent: 9/6 Jacks or Better famously returns
 * about <b>99.5%</b> to a player using the optimal chart, and far less to one holding at random. So
 * unlike the other games, "the return" is not a single number — it is a range whose top end depends
 * on how well somebody plays.
 *
 * <p>That is authentic and it is also the thinnest margin in the building. A server that finds a
 * 0.5% edge too thin should shorten the paytable — 8/5 rather than 9/6 — rather than tamper with the
 * deal, because the chart is public knowledge and players will notice a rigged draw long before they
 * notice a smaller full house.
 *
 * <h2>Two steps, one stake</h2>
 *
 * <p>The stake is taken when the hand is dealt, and the draw settles it — the same shape high-low
 * uses. A hand in progress is a small piece of state, which is why the machine holds it in memory
 * and refunds it if the player leaves.
 *
 * <p>Double-or-nothing, which the bot offers after a win, is deliberately not ported yet: it is a
 * third step against an already-settled payout, and it wants the wager model to express "stake what
 * I just won", which {@code Wager} does not do.
 */
public final class VideoPokerGame {

    /** Cards in a hand. */
    public static final int HAND_SIZE = PokerHand.SIZE;

    /** The 9/6 paytable, "for 1". A jacks-or-better pair returns the stake and no more. */
    private static final Map<PokerHand.Category, Double> PAYTABLE =
        new EnumMap<>(PokerHand.Category.class);

    static {
        PAYTABLE.put(PokerHand.Category.ROYAL_FLUSH, 250.0);
        PAYTABLE.put(PokerHand.Category.STRAIGHT_FLUSH, 50.0);
        PAYTABLE.put(PokerHand.Category.FOUR_OF_A_KIND, 25.0);
        PAYTABLE.put(PokerHand.Category.FULL_HOUSE, 9.0);
        PAYTABLE.put(PokerHand.Category.FLUSH, 6.0);
        PAYTABLE.put(PokerHand.Category.STRAIGHT, 4.0);
        PAYTABLE.put(PokerHand.Category.THREE_OF_A_KIND, 3.0);
        PAYTABLE.put(PokerHand.Category.TWO_PAIR, 2.0);
        PAYTABLE.put(PokerHand.Category.JACKS_OR_BETTER, 1.0);
        PAYTABLE.put(PokerHand.Category.NOTHING, 0.0);
    }

    private final Deck deck;
    private final List<Card> hand;
    private boolean drawn;

    /** Deals the opening five. */
    public VideoPokerGame(Random random) {
        this.deck = new Deck(random);
        this.hand = new ArrayList<>(deck.deal(HAND_SIZE));
    }

    /** The cards on the table. Unmodifiable. */
    public List<Card> hand() {
        return Collections.unmodifiableList(new ArrayList<>(hand));
    }

    /** What {@code category} pays, "for 1". */
    public static double payout(PokerHand.Category category) {
        Double value = PAYTABLE.get(category);
        return value == null ? 0.0 : value;
    }

    /** The whole paytable, best hand first, for a screen that wants to print it. */
    public static List<PokerHand.Category> paytableOrder() {
        List<PokerHand.Category> order = new ArrayList<>();
        PokerHand.Category[] all = PokerHand.Category.values();
        for (int i = all.length - 1; i >= 0; i--) {
            if (payout(all[i]) > 0.0) {
                order.add(all[i]);
            }
        }
        return order;
    }

    /**
     * Replaces every card not held, and settles.
     *
     * @param holds one flag per card; true keeps it. A shorter or null array holds nothing, which is
     *     a legal (if poor) play rather than an error — the client decides what to keep, and a
     *     malformed choice must not be able to strand a stake that is already down.
     * @throws IllegalStateException if called twice, which would deal against a settled bet.
     */
    public Result draw(boolean[] holds) {
        if (drawn) {
            throw new IllegalStateException("This hand has already been drawn");
        }
        drawn = true;
        List<Card> before = new ArrayList<>(hand);
        for (int i = 0; i < HAND_SIZE; i++) {
            boolean keep = holds != null && i < holds.length && holds[i];
            if (!keep) {
                hand.set(i, deck.deal());
            }
        }
        return new Result(before, new ArrayList<>(hand));
    }

    /** One completed hand. */
    public static final class Result implements GameResult {

        private final List<Card> dealt;
        private final List<Card> finalHand;
        private final PokerHand.Category category;

        Result(List<Card> dealt, List<Card> finalHand) {
            this.dealt = Collections.unmodifiableList(dealt);
            this.finalHand = Collections.unmodifiableList(finalHand);
            this.category = PokerHand.evaluate(finalHand);
        }

        /** The five cards before the draw. */
        public List<Card> dealtHand() {
            return dealt;
        }

        /** The five cards after it. */
        public List<Card> finalHand() {
            return finalHand;
        }

        public PokerHand.Category category() {
            return category;
        }

        @Override
        public double totalReturnMultiplier() {
            return payout(category);
        }

        @Override
        public String describe() {
            double multiplier = payout(category);
            if (multiplier <= 0.0) {
                return category.label() + " — no win.";
            }
            if (multiplier == 1.0) {
                return category.label() + " — your bet is returned.";
            }
            return category.label() + " — pays " + trim(multiplier) + "x!";
        }

        private static String trim(double value) {
            return value == Math.floor(value) ? String.valueOf((long) value)
                : String.valueOf(value);
        }
    }
}
