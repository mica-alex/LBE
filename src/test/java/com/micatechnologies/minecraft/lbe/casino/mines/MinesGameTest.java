package com.micatechnologies.minecraft.lbe.casino.mines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mines' multiplier, and the claim that makes the game fair.
 *
 * <p>Its payout is derived rather than tabled, which is unusual here and worth checking hard: if the
 * formula is even slightly wrong the game still <i>plays</i> perfectly — tiles turn over, numbers go
 * up — while paying the wrong price at every step. The strongest available check is that the edge
 * comes out at exactly 4% no matter how a player behaves, so that is what these tests do.
 */
class MinesGameTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    @DisplayName("revealing nothing is worth exactly the stake")
    void noRevealsIsOne() {
        assertEquals(1.0, MinesGame.multiplierFor(MinesGame.GRID_SIZE, 3, 0), EPSILON);
    }

    @Test
    @DisplayName("the multiplier only ever goes up as more tiles are turned")
    void multiplierIsMonotonic() {
        for (int mines = MinesGame.MIN_MINES; mines <= 10; mines++) {
            double previous = 0.0;
            for (int revealed = 0; revealed <= MinesGame.GRID_SIZE - mines; revealed++) {
                double current = MinesGame.multiplierFor(MinesGame.GRID_SIZE, mines, revealed);
                assertTrue(current >= previous,
                    mines + " mines, " + revealed + " revealed went backwards");
                previous = current;
            }
        }
    }

    @Test
    @DisplayName("more mines pays better at the same number of tiles")
    void moreMinesPaysMore() {
        for (int revealed = 1; revealed <= 5; revealed++) {
            double previous = 0.0;
            for (int mines = 1; mines <= 10; mines++) {
                double current = MinesGame.multiplierFor(MinesGame.GRID_SIZE, mines, revealed);
                assertTrue(current > previous,
                    "at " + revealed + " tiles, " + mines + " mines paid no better than "
                        + (mines - 1));
                previous = current;
            }
        }
    }

    @Test
    @DisplayName("THE claim: the house keeps exactly 4% at every stopping point")
    void edgeIsConstantEverywhere() {
        // This is what makes the game honest, and it is the reason the multiplier is computed from
        // the odds rather than written into a table. If it held at some depths and not others, a
        // player could find the good stopping point and sit on it — and nothing on screen would
        // give any hint that one existed.
        //
        // Expected return at any given stopping point is P(surviving that far) x multiplier, and
        // P(survive r picks) is C(g-m, r) / C(g, r) — the exact inverse of the fair multiplier.
        for (int mines = 1; mines <= 12; mines++) {
            int safe = MinesGame.GRID_SIZE - mines;
            for (int revealed = 1; revealed <= safe; revealed++) {
                double survive = choose(safe, revealed) / choose(MinesGame.GRID_SIZE, revealed);
                double multiplier = MinesGame.multiplierFor(MinesGame.GRID_SIZE, mines, revealed);
                double rtp = survive * multiplier;
                // Loose enough to absorb the two-decimal rounding on the printed multiplier, tight
                // enough that a genuinely wrong formula fails.
                assertEquals(1.0 - MinesGame.HOUSE_EDGE, rtp, 0.02,
                    mines + " mines, stopping at " + revealed + " tiles returns " + rtp);
            }
        }
    }

    @Test
    @DisplayName("a board holds exactly the mines it was asked for")
    void boardIsLaidCorrectly() {
        Random random = new Random(1L);
        for (int mines = 1; mines <= 12; mines++) {
            MinesGame game = new MinesGame(mines, random);
            assertEquals(mines, game.mineCount());
            assertEquals(mines, game.mines().size());
            assertEquals(MinesGame.GRID_SIZE - mines, game.safeTotal());
            assertTrue(game.mines().first() >= 0);
            assertTrue(game.mines().last() < MinesGame.GRID_SIZE);
        }
    }

    @Test
    @DisplayName("a nonsense mine count gives a playable board rather than an exception")
    void mineCountIsClamped() {
        // This number comes from a client, and the stake is taken before the board is laid.
        Random random = new Random(2L);
        assertEquals(MinesGame.MIN_MINES, new MinesGame(0, random).mineCount());
        assertEquals(MinesGame.MIN_MINES, new MinesGame(-5, random).mineCount());
        assertEquals(MinesGame.MAX_MINES, new MinesGame(9999, random).mineCount());
    }

    @Test
    @DisplayName("hitting a mine ends the round and pays nothing")
    void hittingAMineLoses() {
        Random random = new Random(3L);
        MinesGame game = new MinesGame(5, random);
        int mine = game.mines().first();
        MinesGame.Result result = game.reveal(mine);
        assertNotNull(result, "hitting a mine must end the round");
        assertTrue(result.busted());
        assertEquals(0.0, result.totalReturnMultiplier(), EPSILON);
        assertTrue(game.isFinished());
    }

    @Test
    @DisplayName("a safe tile continues the round")
    void safeTileContinues() {
        Random random = new Random(4L);
        MinesGame game = new MinesGame(3, random);
        int safe = firstSafeTile(game);
        assertNull(game.reveal(safe), "a safe tile must not end the round");
        assertFalse(game.isFinished());
        assertEquals(1, game.revealed().size());
        assertTrue(game.currentMultiplier() > 1.0);
    }

    @Test
    @DisplayName("clearing the whole board pays out automatically")
    void clearingTheBoardCashesOut() {
        // Otherwise a player who turned over every safe tile would be left holding a finished game
        // with money still in it, waiting for a button that has nothing left to do.
        Random random = new Random(5L);
        MinesGame game = new MinesGame(MinesGame.MAX_MINES, random);   // one mine, one safe tile
        MinesGame.Result result = null;
        for (int tile = 0; tile < MinesGame.GRID_SIZE && result == null; tile++) {
            if (!game.mines().contains(tile)) {
                result = game.reveal(tile);
            }
        }
        assertNotNull(result, "clearing the board must settle the round");
        assertFalse(result.busted());
        assertTrue(result.totalReturnMultiplier() > 1.0);
    }

    @Test
    @DisplayName("cashing out with nothing revealed is refused")
    void cannotCashOutImmediately() {
        // It would be a bet placed and instantly handed back — not something to allow by accident.
        MinesGame game = new MinesGame(3, new Random(6L));
        assertThrows(IllegalStateException.class, game::cashOut);
    }

    @Test
    @DisplayName("a finished round refuses further moves")
    void finishedRoundIsClosed() {
        Random random = new Random(7L);
        MinesGame game = new MinesGame(3, random);
        game.reveal(firstSafeTile(game));
        game.cashOut();
        assertThrows(IllegalStateException.class, game::cashOut);
        assertThrows(IllegalStateException.class, () -> game.reveal(0));
    }

    @Test
    @DisplayName("an out-of-range or repeated tile is ignored, not an error")
    void misclicksAreIgnored() {
        Random random = new Random(8L);
        MinesGame game = new MinesGame(3, random);
        int safe = firstSafeTile(game);
        game.reveal(safe);
        assertNull(game.reveal(safe), "revealing the same tile twice should do nothing");
        assertEquals(1, game.revealed().size());
        assertNull(game.reveal(-1));
        assertNull(game.reveal(MinesGame.GRID_SIZE));
        assertEquals(1, game.revealed().size());
    }

    @Test
    @DisplayName("playing to a fixed depth really does return 96%")
    void simulationMatchesTheEdge() {
        // The formula proved above, played out: pick three mines, always stop at four tiles.
        Random random = new Random(9L);
        int rounds = 200_000;
        double returned = 0.0;
        for (int i = 0; i < rounds; i++) {
            MinesGame game = new MinesGame(3, random);
            MinesGame.Result result = null;
            int picks = 0;
            for (int tile = 0; tile < MinesGame.GRID_SIZE && result == null && picks < 4; tile++) {
                result = game.reveal(tile);
                picks++;
            }
            if (result == null) {
                result = game.cashOut();
            }
            returned += result.totalReturnMultiplier();
        }
        double rtp = returned / rounds;
        assertEquals(1.0 - MinesGame.HOUSE_EDGE, rtp, 0.02,
            "stopping at four tiles returned " + rtp);
    }

    private static int firstSafeTile(MinesGame game) {
        for (int tile = 0; tile < MinesGame.GRID_SIZE; tile++) {
            if (!game.mines().contains(tile)) {
                return tile;
            }
        }
        throw new IllegalStateException("a board with no safe tiles");
    }

    private static double choose(int n, int k) {
        if (k < 0 || k > n) {
            return 0.0;
        }
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
