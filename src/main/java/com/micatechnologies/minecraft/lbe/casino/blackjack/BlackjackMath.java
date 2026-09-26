package com.micatechnologies.minecraft.lbe.casino.blackjack;

import java.util.HashMap;
import java.util.Map;

/**
 * Blackjack's return to a perfect player, in closed form, for exactly the rules in
 * {@link BlackjackGame}.
 *
 * <p>With an infinite deck every card is independent, so the whole game reduces to arithmetic over
 * hand totals. For each dealer upcard this works out how the dealer's hand ends (conditioned on the
 * dealer having no blackjack when the upcard would have made them peek), then the value of standing,
 * hitting, doubling and splitting every player total, taking the best. The weighted average over
 * the deal is {@link #returnToPlayer()}.
 *
 * <p>The same numbers are the optimal strategy, which {@link #bestAction} exposes so a test can
 * play the real engine with it and check that the engine and the maths agree.
 *
 * <p>Card values are indexed 2..11, with 11 an ace. All "net" values are per unit of the initial
 * stake: +1 a won stake, -1 a lost one.
 */
public final class BlackjackMath {

    /** Probability of each card value 2..11 from an infinite deck: ten-values are 4 in 13. */
    private static final double[] P = new double[12];

    static {
        for (int v = 2; v <= 11; v++) {
            P[v] = v == 10 ? 4.0 / 13.0 : 1.0 / 13.0;
        }
    }

    /** Dealer outcome slots: final totals 17..21, then bust. */
    private static final int BUST = 5;

    private static final Map<Integer, BlackjackMath> BY_UPCARD = new HashMap<>();
    private static Double cachedReturn;

    /** The dealer's outcome distribution for this upcard, peek-conditioned where it applies. */
    private final double[] dealer;
    private final Map<Integer, Double> bestMemo = new HashMap<>();

    private BlackjackMath(int upcard) {
        this.dealer = dealerFromUpcard(upcard);
    }

    private static BlackjackMath forUpcard(int upcard) {
        synchronized (BY_UPCARD) {
            return BY_UPCARD.computeIfAbsent(upcard, BlackjackMath::new);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The dealer
    // ---------------------------------------------------------------------------------------------

    private static int hardOf(int v) {
        return v == 11 ? 1 : v;
    }

    private static int value(int hard, boolean ace) {
        return ace && hard + 10 <= 21 ? hard + 10 : hard;
    }

    /** How the dealer's hand ends from (hard total, holds an ace). Stands on all 17s. */
    private static double[] dealerFrom(int hard, boolean ace) {
        double[] out = new double[6];
        if (hard > 21) {
            out[BUST] = 1.0;
            return out;
        }
        int total = value(hard, ace);
        if (total >= 17) {
            out[total - 17] = 1.0;
            return out;
        }
        for (int v = 2; v <= 11; v++) {
            double[] next = dealerFrom(hard + hardOf(v), ace || v == 11);
            for (int i = 0; i < 6; i++) {
                out[i] += P[v] * next[i];
            }
        }
        return out;
    }

    /** The dealer's distribution given the upcard, excluding a hole card that makes blackjack. */
    private static double[] dealerFromUpcard(int up) {
        double[] out = new double[6];
        double excluded = up == 11 ? P[10] : up == 10 ? P[11] : 0.0;
        for (int v = 2; v <= 11; v++) {
            boolean blackjack = (up == 11 && v == 10) || (up == 10 && v == 11);
            if (blackjack) {
                continue;
            }
            double weight = P[v] / (1.0 - excluded);
            double[] next = dealerFrom(hardOf(up) + hardOf(v), up == 11 || v == 11);
            for (int i = 0; i < 6; i++) {
                out[i] += weight * next[i];
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // The player
    // ---------------------------------------------------------------------------------------------

    /** Net value of standing on (hard, ace). */
    double stand(int hard, boolean ace) {
        if (hard > 21) {
            return -1.0;
        }
        int total = value(hard, ace);
        double win = dealer[BUST];
        double lose = 0.0;
        for (int t = 17; t <= 21; t++) {
            if (t < total) {
                win += dealer[t - 17];
            } else if (t > total) {
                lose += dealer[t - 17];
            }
        }
        return win - lose;
    }

    /** Net value of taking a card, then playing on as well as possible (no doubling). */
    double hit(int hard, boolean ace) {
        double sum = 0.0;
        for (int v = 2; v <= 11; v++) {
            sum += P[v] * best(hard + hardOf(v), ace || v == 11);
        }
        return sum;
    }

    /** The better of standing and hitting, from (hard, ace). */
    double best(int hard, boolean ace) {
        if (hard > 21) {
            return -1.0;
        }
        int key = hard * 2 + (ace ? 1 : 0);
        Double known = bestMemo.get(key);
        if (known != null) {
            return known;
        }
        double result = value(hard, ace) == 21 ? stand(hard, ace)
            : Math.max(stand(hard, ace), hit(hard, ace));
        bestMemo.put(key, result);
        return result;
    }

    /** Net value of doubling: one card, twice the stake. */
    double doubleDown(int hard, boolean ace) {
        double sum = 0.0;
        for (int v = 2; v <= 11; v++) {
            sum += P[v] * stand(hard + hardOf(v), ace || v == 11);
        }
        return 2.0 * sum;
    }

    /** The best a two-card hand can do without splitting. */
    double twoCard(int hard, boolean ace) {
        return Math.max(best(hard, ace), doubleDown(hard, ace));
    }

    /** Net value of splitting a pair of value {@code v}: two hands, each worth playing out. */
    double split(int v) {
        double sum = 0.0;
        for (int c = 2; c <= 11; c++) {
            int hard = hardOf(v) + hardOf(c);
            boolean ace = v == 11 || c == 11;
            // Split aces take one card and stand; anything else plays on, doubling allowed.
            sum += P[c] * (v == 11 ? stand(hard, ace) : twoCard(hard, ace));
        }
        return 2.0 * sum;
    }

    /** The value of a fresh two-card hand (v1, v2) that is not a blackjack. */
    double opening(int v1, int v2) {
        double value = twoCard(hardOf(v1) + hardOf(v2), v1 == 11 || v2 == 11);
        if (v1 == v2) {
            value = Math.max(value, split(v1));
        }
        return value;
    }

    // ---------------------------------------------------------------------------------------------
    // The whole game
    // ---------------------------------------------------------------------------------------------

    /**
     * What a player using perfect strategy gets back per unit of their initial stake. Stakes
     * added by doubling and splitting are counted inside it, as their extra wins and losses.
     */
    public static synchronized double returnToPlayer() {
        if (cachedReturn != null) {
            return cachedReturn;
        }
        double playerBlackjack = 2.0 * P[10] * P[11];
        double net = 0.0;
        for (int up = 2; up <= 11; up++) {
            BlackjackMath table = forUpcard(up);
            double noDealerBlackjack = 0.0;
            for (int v1 = 2; v1 <= 11; v1++) {
                for (int v2 = 2; v2 <= 11; v2++) {
                    boolean blackjack = (v1 == 10 && v2 == 11) || (v1 == 11 && v2 == 10);
                    noDealerBlackjack += P[v1] * P[v2]
                        * (blackjack ? BlackjackGame.BLACKJACK_RETURN - 1.0 : table.opening(v1, v2));
                }
            }
            double dealerBlackjack = up == 11 ? P[10] : up == 10 ? P[11] : 0.0;
            // Dealer blackjack: a player blackjack pushes (0), anything else loses one stake.
            double peekLoss = dealerBlackjack * (1.0 - playerBlackjack) * -1.0;
            net += P[up] * (peekLoss + (1.0 - dealerBlackjack) * noDealerBlackjack);
        }
        cachedReturn = 1.0 + net;
        return cachedReturn;
    }

    /**
     * The perfect play for a hand, given what is allowed. Used by the tests to play the engine.
     *
     * @param upcard the dealer's upcard value, 2..11
     */
    public static BlackjackGame.Action bestAction(BlackjackHand hand, int upcard, boolean canDouble,
                                                  boolean canSplit) {
        BlackjackMath table = forUpcard(upcard);
        int hard = hand.hardTotal();
        boolean ace = hand.hasAce();
        BlackjackGame.Action choice = BlackjackGame.Action.STAND;
        double bestValue = table.stand(hard, ace);
        double hit = table.hit(hard, ace);
        if (hit > bestValue) {
            bestValue = hit;
            choice = BlackjackGame.Action.HIT;
        }
        if (canDouble && table.doubleDown(hard, ace) > bestValue) {
            bestValue = table.doubleDown(hard, ace);
            choice = BlackjackGame.Action.DOUBLE;
        }
        if (canSplit) {
            int v = BlackjackHand.hardValue(hand.cards().get(0).rank());
            int index = v == 1 ? 11 : v;
            if (table.split(index) > bestValue) {
                choice = BlackjackGame.Action.SPLIT;
            }
        }
        return choice;
    }

    /** A card rank's value as this class indexes it: 2..10, ace 11. */
    public static int indexOf(com.micatechnologies.minecraft.lbe.casino.cards.Rank rank) {
        int v = BlackjackHand.hardValue(rank);
        return v == 1 ? 11 : v;
    }
}
