package com.micatechnologies.minecraft.lbe.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.CasinoOdds;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.highlow.HighLowGame;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.war.WarGame;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * That each game plays by the rules it was ported from, and that its arithmetic describes the game
 * it actually implements.
 *
 * <p>{@link HouseEdgeTest} proves the maths is what we think it is. This proves the code is what the
 * maths is about — the two are written independently, and a game whose {@code returnToPlayer()} is
 * beautiful and whose {@code play()} does something else is worse than having neither.
 */
class GameRulesTest {

    /** Enough hands that a 2% divergence between theory and practice shows up reliably. */
    private static final int SIMULATION_ROUNDS = 400_000;

    // ---------------------------------------------------------------------------------------------
    // Cards
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a deck holds 52 distinct cards")
    void deckIsComplete() {
        Deck deck = new Deck(new Random(1L));
        Set<Card> seen = new HashSet<>();
        assertEquals(52, deck.remaining());
        while (deck.remaining() > 0) {
            assertTrue(seen.add(deck.deal()), "a deck dealt the same card twice");
        }
        assertEquals(52, seen.size());
    }

    @Test
    @DisplayName("an over-dealt deck fails loudly rather than dealing nothing")
    void deckRefusesToOverdeal() {
        // Silently returning null here would put a non-card into a hand somebody has money on.
        Deck deck = new Deck(new Random(2L));
        deck.deal(52);
        assertThrows(IllegalStateException.class, deck::deal);
    }

    @Test
    @DisplayName("ranks run two to ace, ace high")
    void rankOrdering() {
        assertEquals(2, Rank.TWO.value());
        assertEquals(14, Rank.ACE.value());
        assertTrue(Rank.ACE.value() > Rank.KING.value());
        assertTrue(Rank.JACK.value() > Rank.TEN.value());
    }

    // ---------------------------------------------------------------------------------------------
    // Coin flip
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a correct call pays 2x and a wrong one pays nothing")
    void coinFlipRules() {
        Random rigged = new Random(4L);
        for (int i = 0; i < 1000; i++) {
            CoinFlipGame.Result result = CoinFlipGame.flip(CoinFlipGame.Side.HEADS, rigged);
            assertEquals(result.call() == result.landed() ? CoinFlipGame.WIN_MULTIPLIER : 0.0,
                result.totalReturnMultiplier());
        }
    }

    @Test
    @DisplayName("the coin is fair to within a whisker over 400,000 flips")
    void coinIsFair() {
        Random random = new Random(5L);
        int heads = 0;
        for (int i = 0; i < SIMULATION_ROUNDS; i++) {
            if (CoinFlipGame.flip(CoinFlipGame.Side.HEADS, random).landed()
                    == CoinFlipGame.Side.HEADS) {
                heads++;
            }
        }
        assertEquals(0.5, heads / (double) SIMULATION_ROUNDS, 0.005);
    }

    // ---------------------------------------------------------------------------------------------
    // War
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("war: high card wins, low card loses, equal ranks push")
    void warRules() {
        assertEquals(WarGame.WIN_MULTIPLIER, war(Rank.ACE, Rank.KING).totalReturnMultiplier());
        assertEquals(0.0, war(Rank.TWO, Rank.THREE).totalReturnMultiplier());
        assertEquals(1.0, war(Rank.NINE, Rank.NINE).totalReturnMultiplier());
        assertTrue(war(Rank.NINE, Rank.NINE).isPush());
    }

    @Test
    @DisplayName("war really is break-even in play, not just on paper")
    void warSimulatesToBreakEven() {
        assertEquals(WarGame.returnToPlayer(), simulate(WarGame::play, 6L), 0.01);
    }

    // ---------------------------------------------------------------------------------------------
    // High-low
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("high-low: a correct call wins, a wrong one loses, an equal rank pushes")
    void highLowRules() {
        // Driven through the real deck rather than constructed, so the comparison under test is the
        // one the game actually runs.
        Random random = new Random(7L);
        for (int i = 0; i < 5000; i++) {
            HighLowGame game = new HighLowGame(random);
            Card base = game.base();
            HighLowGame.Result result = game.call(HighLowGame.Call.HIGHER);
            double expected;
            if (result.next().value() == base.value()) {
                expected = HighLowGame.PUSH_MULTIPLIER;
            } else {
                expected = result.next().value() > base.value()
                    ? HighLowGame.payoutFor(base, HighLowGame.Call.HIGHER) : 0.0;
            }
            assertEquals(expected, result.totalReturnMultiplier(), 1.0e-9,
                base + " then " + result.next());
        }
    }

    @Test
    @DisplayName("high-low never deals a two or an ace as the base card")
    void highLowSkipsDeadHands() {
        // Both would leave a hand with one legal call priced below the stake. Driven through the
        // real constructor, which is the thing that has to skip them.
        Random random = new Random(31L);
        for (int i = 0; i < 20_000; i++) {
            Card base = new HighLowGame(random).base();
            assertTrue(HighLowGame.isPlayableBase(base), "dealt an unplayable base card: " + base);
            assertTrue(HighLowGame.payoutFor(base, HighLowGame.Call.HIGHER) > 1.0);
            assertTrue(HighLowGame.payoutFor(base, HighLowGame.Call.LOWER) > 1.0);
        }
    }

    @Test
    @DisplayName("high-low refuses a second call on a settled hand")
    void highLowResolvesOnce() {
        // A second call would deal another card against a stake already settled.
        HighLowGame game = new HighLowGame(new Random(8L));
        game.call(HighLowGame.Call.HIGHER);
        assertThrows(IllegalStateException.class, () -> game.call(HighLowGame.Call.LOWER));
    }

    @Test
    @DisplayName("high-low: calling the bigger side no longer beats calling the smaller one")
    void highLowStrategyNoLongerPays() {
        // The regression test for the whole repricing. Under the bot's rules this exact strategy
        // returned 150.7%; both strategies must now land on the same 97%, because that is what
        // "there is no better side" means in practice rather than on paper.
        double greedy = simulateHighLow(true, 21L);
        double timid = simulateHighLow(false, 22L);
        assertEquals(CasinoOdds.STANDARD_RETURN, greedy, 0.03,
            "always calling the likelier side returned " + greedy);
        assertEquals(CasinoOdds.STANDARD_RETURN, timid, 0.06,
            "always calling the longer shot returned " + timid);
        assertTrue(greedy < 1.0, "the obvious strategy must no longer print money: " + greedy);
    }

    /** Plays high-low many times, always calling the likelier ({@code greedy}) side or the longer. */
    private static double simulateHighLow(boolean greedy, long seed) {
        Random random = new Random(seed);
        double returned = 0.0;
        int played = 0;
        for (int i = 0; i < SIMULATION_ROUNDS; i++) {
            HighLowGame game = new HighLowGame(random);
            Card base = game.base();
            int above = HighLowGame.cardsAbove(base);
            int below = HighLowGame.cardsBelow(base);
            // Both calls are always legal now — a two or an ace is never dealt as a base card.
            HighLowGame.Call call = (above >= below) == greedy
                ? HighLowGame.Call.HIGHER : HighLowGame.Call.LOWER;
            returned += game.call(call).totalReturnMultiplier();
            played++;
        }
        return returned / played;
    }

    @Test
    @DisplayName("the card counts high-low exposes are right, and sum to the 51 unseen cards")
    void highLowCounts() {
        for (Rank rank : Rank.values()) {
            Card card = new Card(rank, Suit.SPADES);
            assertEquals(51, HighLowGame.cardsAbove(card) + HighLowGame.cardsBelow(card)
                + HighLowGame.cardsEqual(), rank.name());
        }
        assertEquals(48, HighLowGame.cardsAbove(new Card(Rank.TWO, Suit.SPADES)));
        assertEquals(0, HighLowGame.cardsBelow(new Card(Rank.TWO, Suit.SPADES)));
        assertEquals(0, HighLowGame.cardsAbove(new Card(Rank.ACE, Suit.SPADES)));
        assertEquals(48, HighLowGame.cardsBelow(new Card(Rank.ACE, Suit.SPADES)));
    }

    // ---------------------------------------------------------------------------------------------
    // Roulette
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("zero is green and belongs to no even-money bet")
    void rouletteZero() {
        assertEquals(RouletteGame.Colour.GREEN, RouletteGame.colourOf(0));
        // The whole house edge lives here. If zero ever counts as even, or low, the game becomes
        // break-even and nobody notices until the money supply moves.
        for (RouletteGame.BetType type : new RouletteGame.BetType[] {
                RouletteGame.BetType.RED, RouletteGame.BetType.BLACK, RouletteGame.BetType.EVEN,
                RouletteGame.BetType.ODD, RouletteGame.BetType.LOW, RouletteGame.BetType.HIGH}) {
            assertEquals(0.0, RouletteGame.spin(type, 0, fixedPocket(0)).totalReturnMultiplier(),
                type.name() + " must not win on zero");
        }
    }

    @Test
    @DisplayName("roulette colours match the real wheel")
    void rouletteColours() {
        assertEquals(RouletteGame.Colour.RED, RouletteGame.colourOf(1));
        assertEquals(RouletteGame.Colour.BLACK, RouletteGame.colourOf(2));
        assertEquals(RouletteGame.Colour.RED, RouletteGame.colourOf(36));
        int reds = 0;
        for (int n = 1; n <= 36; n++) {
            if (RouletteGame.colourOf(n) == RouletteGame.Colour.RED) {
                reds++;
            }
        }
        assertEquals(18, reds, "a wheel must have eighteen red pockets");
    }

    @Test
    @DisplayName("each bet covers exactly the pockets it should")
    void rouletteCoverage() {
        for (RouletteGame.BetType type : RouletteGame.BetType.values()) {
            int wins = 0;
            for (int pocket = 0; pocket < RouletteGame.POCKETS; pocket++) {
                // Straight bets on 7, dozens on the first twelve — one representative each.
                int value = type == RouletteGame.BetType.STRAIGHT ? 7 : 1;
                if (RouletteGame.spin(type, value, fixedPocket(pocket)).totalReturnMultiplier()
                        > 0.0) {
                    wins++;
                }
            }
            assertEquals(RouletteGame.winningPockets(type), wins, type.name());
        }
    }

    @Test
    @DisplayName("roulette validates the number that comes with a bet")
    void rouletteValidation() {
        assertTrue(RouletteGame.isValidValue(RouletteGame.BetType.STRAIGHT, 0));
        assertTrue(RouletteGame.isValidValue(RouletteGame.BetType.STRAIGHT, 36));
        assertFalse(RouletteGame.isValidValue(RouletteGame.BetType.STRAIGHT, 37));
        assertFalse(RouletteGame.isValidValue(RouletteGame.BetType.STRAIGHT, -1));
        assertTrue(RouletteGame.isValidValue(RouletteGame.BetType.DOZEN, 3));
        assertFalse(RouletteGame.isValidValue(RouletteGame.BetType.DOZEN, 4));
        assertFalse(RouletteGame.isValidValue(RouletteGame.BetType.DOZEN, 0));
    }

    // ---------------------------------------------------------------------------------------------
    // Plinko
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a drop's path and its landing slot agree")
    void plinkoPathMatchesSlot() {
        // The client animates the path; if it disagreed with the slot, the ball would visibly land
        // somewhere other than where it paid.
        Random random = new Random(11L);
        for (int i = 0; i < 20_000; i++) {
            PlinkoGame.Result result = PlinkoGame.drop(PlinkoGame.Risk.MEDIUM, random);
            int rights = 0;
            for (boolean right : result.path()) {
                if (right) {
                    rights++;
                }
            }
            assertEquals(rights, result.slot());
            assertEquals(PlinkoGame.ROWS, result.path().length);
        }
    }

    @Test
    @DisplayName("every risk level plays to its computed return")
    void plinkoSimulates() {
        for (PlinkoGame.Risk risk : PlinkoGame.Risk.values()) {
            Random random = new Random(12L);
            double returned = 0.0;
            for (int i = 0; i < SIMULATION_ROUNDS; i++) {
                returned += PlinkoGame.drop(risk, random).totalReturnMultiplier();
            }
            assertEquals(PlinkoGame.returnToPlayer(risk), returned / SIMULATION_ROUNDS, 0.02,
                risk.name());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Keno
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("keno draws twenty distinct numbers from the board")
    void kenoDraw() {
        Random random = new Random(13L);
        SortedSet<Integer> picks = new TreeSet<>();
        picks.add(7);
        for (int i = 0; i < 2000; i++) {
            KenoGame.Result result = KenoGame.play(picks, random);
            assertEquals(KenoGame.DRAW_COUNT, result.drawn().size(), "draws must be distinct");
            assertTrue(result.drawn().first() >= 1);
            assertTrue(result.drawn().last() <= KenoGame.BOARD_SIZE);
            assertEquals(result.drawn().contains(7) ? 1 : 0, result.matches());
        }
    }

    @Test
    @DisplayName("keno rejects an empty or oversized pick set")
    void kenoValidation() {
        assertFalse(KenoGame.isValid(new TreeSet<>()));
        assertFalse(KenoGame.isValid(null));
        SortedSet<Integer> tooMany = new TreeSet<>();
        for (int i = 1; i <= KenoGame.MAX_PICKS + 1; i++) {
            tooMany.add(i);
        }
        assertFalse(KenoGame.isValid(tooMany));
        SortedSet<Integer> offBoard = new TreeSet<>();
        offBoard.add(81);
        assertFalse(KenoGame.isValid(offBoard));
    }

    @Test
    @DisplayName("keno plays to its computed return at a representative pick count")
    void kenoSimulates() {
        SortedSet<Integer> picks = new TreeSet<>();
        for (int i = 1; i <= 4; i++) {
            picks.add(i * 7);
        }
        Random random = new Random(14L);
        double returned = 0.0;
        int rounds = 200_000;
        for (int i = 0; i < rounds; i++) {
            returned += KenoGame.play(picks, random).totalReturnMultiplier();
        }
        assertEquals(KenoGame.returnToPlayer(4), returned / rounds, 0.05);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static WarGame.Result war(Rank player, Rank dealer) {
        return WarGame.evaluate(new Card(player, Suit.SPADES), new Card(dealer, Suit.HEARTS));
    }

    /** A {@link Random} whose {@code nextInt} always names one pocket, for exhaustive coverage. */
    private static Random fixedPocket(int pocket) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return pocket % bound;
            }
        };
    }

    private static double simulate(java.util.function.Function<Random, GameResult> game, long seed) {
        Random random = new Random(seed);
        double returned = 0.0;
        for (int i = 0; i < SIMULATION_ROUNDS; i++) {
            returned += game.apply(random).totalReturnMultiplier();
        }
        return returned / SIMULATION_ROUNDS;
    }
}
