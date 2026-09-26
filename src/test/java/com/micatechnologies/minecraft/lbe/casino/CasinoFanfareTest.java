package com.micatechnologies.minecraft.lbe.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * That the screen and the server, which classify a round independently, agree on the boundaries —
 * and that nothing a player loses on is celebrated in front of the room.
 */
class CasinoFanfareTest {

    @Test
    @DisplayName("each boundary lands on the side the constants say")
    void boundaries() {
        assertEquals(CasinoFanfare.LOSS, CasinoFanfare.of(0.0, false));
        assertEquals(CasinoFanfare.LOSS, CasinoFanfare.of(0.5, false));
        assertEquals(CasinoFanfare.PUSH, CasinoFanfare.of(1.0, false));
        assertEquals(CasinoFanfare.WIN, CasinoFanfare.of(1.01, false));
        assertEquals(CasinoFanfare.WIN, CasinoFanfare.of(9.99, false));
        assertEquals(CasinoFanfare.BIG_WIN, CasinoFanfare.of(CasinoFanfare.BIG_WIN_MULTIPLIER, false));
        assertEquals(CasinoFanfare.JACKPOT, CasinoFanfare.of(CasinoFanfare.JACKPOT_MULTIPLIER, false));
    }

    @Test
    @DisplayName("a game's own top prize is a jackpot whatever it pays")
    void topPrize() {
        assertEquals(CasinoFanfare.JACKPOT, CasinoFanfare.of(3.0, true));
    }

    @Test
    @DisplayName("only winning rounds are heard by bystanders")
    void bystanders() {
        assertFalse(CasinoFanfare.LOSS.isHeardByBystanders());
        assertFalse(CasinoFanfare.PUSH.isHeardByBystanders());
        assertTrue(CasinoFanfare.WIN.isHeardByBystanders());
        assertTrue(CasinoFanfare.BIG_WIN.isHeardByBystanders());
        assertTrue(CasinoFanfare.JACKPOT.isHeardByBystanders());
    }

    @Test
    @DisplayName("the last reel stops exactly when the reveal ends")
    void reelsFitTheReveal() {
        int[] stops = CasinoFanfare.REEL_STOP_TICKS;
        for (int i = 1; i < stops.length; i++) {
            assertTrue(stops[i] > stops[i - 1], "reels land in order");
        }
        assertEquals(CasinoFanfare.REVEAL_TICKS, stops[stops.length - 1]);
    }
}
