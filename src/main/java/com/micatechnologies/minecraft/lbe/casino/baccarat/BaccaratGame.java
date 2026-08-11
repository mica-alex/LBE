package com.micatechnologies.minecraft.lbe.casino.baccarat;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Punto Banco baccarat: bet on the player hand, the banker hand, or a tie.
 *
 * <p>Ported from the Discord bot's {@code baccarat_game.py} with its rules and payouts unchanged —
 * and unlike four of the games ported alongside it, nothing here needed repricing. Baccarat's edge
 * is structural and the bot's version has it: the banker wins slightly more often than the player,
 * which is why a banker bet pays 1.95 rather than 2, and the 5% commission is the entire reason the
 * game is not a coin toss.
 *
 * <h2>Nobody makes a decision</h2>
 *
 * <p>The only choice is which side to back. Whether a third card is drawn is fixed by the tableau —
 * a table of rules dating to the game's casino form — and neither the player nor the dealer may
 * deviate from it. That is unusual and worth knowing: it means there is no strategy to get wrong,
 * and the return is the same for every player who ever sits down.
 *
 * <p>Scoring is the game's other oddity. Tens and faces count zero, aces count one, and a hand is
 * its total <b>modulo ten</b> — so a nine and a seven is a six, not a sixteen.
 */
public final class BaccaratGame {

    /** Which hand a bet is on. */
    public enum Side {
        PLAYER("Player", 2.0),
        BANKER("Banker", 1.95),
        TIE("Tie", 9.0);

        private final String label;
        private final double multiplier;

        Side(String label, double multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        public String label() {
            return label;
        }

        /**
         * What backing this side pays when it comes in, "for 1".
         *
         * <p>The banker's 1.95 is even money less the house's traditional 5% commission. Without it
         * the banker bet would return more than 100%, because the banker really does win more often
         * — the drawing rules give it the last card and therefore the last chance to improve.
         */
        public double multiplier() {
            return multiplier;
        }
    }

    /** The number a side travels as. Encoding and decoding together, so they cannot drift. */
    public static int codeFor(Side side) {
        return side.ordinal();
    }

    /** The side a code means, or {@code null} if it is not one. */
    public static Side sideFor(int code) {
        Side[] all = Side.values();
        return code >= 0 && code < all.length ? all[code] : null;
    }

    private BaccaratGame() {
        throw new AssertionError("No instances.");
    }

    /** A card's baccarat value: ten and the faces are zero, an ace is one, the rest face value. */
    public static int valueOf(Card card) {
        Rank rank = card.rank();
        if (rank == Rank.TEN || rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING) {
            return 0;
        }
        return rank == Rank.ACE ? 1 : rank.value();
    }

    /** A hand's score: the sum of its cards, modulo ten. */
    public static int score(List<Card> hand) {
        int total = 0;
        for (Card card : hand) {
            total += valueOf(card);
        }
        return total % 10;
    }

    /**
     * Whether the banker draws a third card.
     *
     * <p>The tableau, verbatim. It looks arbitrary because it is: these are the rules the game
     * arrived with, and they are what make the banker's edge what it is.
     *
     * @param bankerScore the banker's two-card score.
     * @param playerThirdValue the value of the card the player drew, or {@code null} if they stood.
     */
    public static boolean bankerDraws(int bankerScore, Integer playerThirdValue) {
        if (playerThirdValue == null) {
            return bankerScore <= 5;
        }
        if (bankerScore <= 2) {
            return true;
        }
        switch (bankerScore) {
            case 3:
                return playerThirdValue != 8;
            case 4:
                return playerThirdValue >= 2 && playerThirdValue <= 7;
            case 5:
                return playerThirdValue >= 4 && playerThirdValue <= 7;
            case 6:
                return playerThirdValue >= 6 && playerThirdValue <= 7;
            default:
                return false;
        }
    }

    /** Deals a coup and settles a bet on {@code side}. */
    public static Result play(Side side, Random random) {
        Deck deck = new Deck(random);
        List<Card> player = new ArrayList<>(3);
        List<Card> banker = new ArrayList<>(3);
        player.add(deck.deal());
        banker.add(deck.deal());
        player.add(deck.deal());
        banker.add(deck.deal());

        int playerScore = score(player);
        int bankerScore = score(banker);

        // A "natural" — eight or nine on two cards — ends the coup at once, for both hands.
        if (playerScore < 8 && bankerScore < 8) {
            Integer playerThirdValue = null;
            if (playerScore <= 5) {
                Card third = deck.deal();
                player.add(third);
                playerThirdValue = valueOf(third);
            }
            if (bankerDraws(bankerScore, playerThirdValue)) {
                banker.add(deck.deal());
            }
            playerScore = score(player);
            bankerScore = score(banker);
        }
        return new Result(side, player, banker, playerScore, bankerScore);
    }

    /** One coup. */
    public static final class Result implements GameResult {

        private final Side side;
        private final List<Card> player;
        private final List<Card> banker;
        private final int playerScore;
        private final int bankerScore;

        Result(Side side, List<Card> player, List<Card> banker, int playerScore, int bankerScore) {
            this.side = side;
            this.player = Collections.unmodifiableList(new ArrayList<>(player));
            this.banker = Collections.unmodifiableList(new ArrayList<>(banker));
            this.playerScore = playerScore;
            this.bankerScore = bankerScore;
        }

        public Side side() {
            return side;
        }

        public List<Card> playerHand() {
            return player;
        }

        public List<Card> bankerHand() {
            return banker;
        }

        public int playerScore() {
            return playerScore;
        }

        public int bankerScore() {
            return bankerScore;
        }

        /** Which side actually won. */
        public Side winner() {
            if (playerScore > bankerScore) {
                return Side.PLAYER;
            }
            return bankerScore > playerScore ? Side.BANKER : Side.TIE;
        }

        @Override
        public double totalReturnMultiplier() {
            Side winner = winner();
            if (side == winner) {
                return side.multiplier();
            }
            // A tie returns a player or banker bet rather than taking it — the hands are equal, so
            // neither side lost. Only a bet ON the tie is decided by one.
            return winner == Side.TIE ? 1.0 : 0.0;
        }

        @Override
        public String describe() {
            String scores = "Player " + playerScore + " — Banker " + bankerScore + ". ";
            Side winner = winner();
            if (side == winner) {
                return scores + (winner == Side.TIE ? "A tie, and you called it!" : winner.label()
                    + " wins — you called it!");
            }
            if (winner == Side.TIE) {
                return scores + "A tie; your bet is returned.";
            }
            return scores + winner.label() + " wins.";
        }
    }
}
