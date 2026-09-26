package com.micatechnologies.minecraft.lbe.casino.craps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each craps bet returns its textbook figure, and the engine plays to it. */
class CrapsGameTest {

    @Test
    @DisplayName("pass 98.59%, don't pass 98.64%, field 97.22% — the textbook figures")
    void returns() {
        assertEquals(244.0 / 495.0 * 2.0, CrapsGame.returnToPlayer(CrapsGame.Bet.PASS), 1e-12);
        assertEquals(0.986363636, CrapsGame.returnToPlayer(CrapsGame.Bet.DONT_PASS), 1e-8);
        assertEquals(35.0 / 36.0, CrapsGame.returnToPlayer(CrapsGame.Bet.FIELD), 1e-12);
        for (CrapsGame.Bet bet : CrapsGame.Bet.values()) {
            assertTrue(CrapsGame.returnToPlayer(bet) < 1.0, bet.name());
        }
    }

    @Test
    @DisplayName("the engine, played out, lands on each figure")
    void engineMatchesMaths() {
        Random random = new Random(20260926L);
        for (CrapsGame.Bet bet : CrapsGame.Bet.values()) {
            int rounds = 600_000;
            double returned = 0.0;
            for (int i = 0; i < rounds; i++) {
                CrapsGame game = CrapsGame.start(bet, random);
                while (!game.isFinished()) {
                    game.roll(random);
                }
                returned += game.totalReturnMultiplier();
            }
            assertEquals(CrapsGame.returnToPlayer(bet), returned / rounds, 0.006, bet.name());
        }
    }

    @Test
    @DisplayName("a field bet is always one roll; pass and don't pass set a point on 4-10")
    void roundShapes() {
        Random random = new Random(3L);
        for (int i = 0; i < 20_000; i++) {
            assertTrue(CrapsGame.start(CrapsGame.Bet.FIELD, random).isFinished());
            CrapsGame pass = CrapsGame.start(CrapsGame.Bet.PASS, random);
            int t = pass.total();
            boolean decisive = t == 2 || t == 3 || t == 7 || t == 11 || t == 12;
            assertEquals(decisive, pass.isFinished(), "come-out " + t);
            if (!decisive) {
                assertEquals(t, pass.point());
            }
        }
    }
}
