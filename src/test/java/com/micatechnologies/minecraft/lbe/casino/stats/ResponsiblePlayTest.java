package com.micatechnologies.minecraft.lbe.casino.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Limits hold, raises wait, exclusions cannot be cut short, and comps add up. */
class ResponsiblePlayTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long NOON = 20000L * DAY + DAY / 2;

    @Test
    @DisplayName("a bet that could pass the day's limit is refused; the limit resets at midnight UTC")
    void dailyLimit() {
        ResponsiblePlay play = new ResponsiblePlay();
        play.record(ME, 80.0, 0.0, 0.005, NOON);           // down 80 today
        assertNull(play.refusal(ME, 20.0, 100.0, NOON), "exactly reaching the cap is fine");
        assertNotNull(play.refusal(ME, 25.0, 100.0, NOON), "could pass the cap");
        assertNull(play.refusal(ME, 25.0, 100.0, NOON + DAY), "a new day starts at zero");
        assertNull(play.refusal(ME, 1_000.0, 0.0, NOON), "no cap, no limit");
    }

    @Test
    @DisplayName("winning back reduces the day's loss, but never below nothing")
    void lossTracking() {
        ResponsiblePlay play = new ResponsiblePlay();
        play.record(ME, 50.0, 0.0, 0.005, NOON);
        play.record(ME, 10.0, 40.0, 0.005, NOON);
        assertEquals(20.0, play.view(ME, NOON).lossToday(), 1e-9);
        play.record(ME, 10.0, 100.0, 0.005, NOON);
        assertEquals(0.0, play.view(ME, NOON).lossToday(), 1e-9);
    }

    @Test
    @DisplayName("lowering a personal limit is instant; raising it waits a day")
    void personalLimits() {
        ResponsiblePlay play = new ResponsiblePlay();
        assertTrue(play.setLimit(ME, 50.0, NOON));
        assertEquals(50.0, play.view(ME, NOON).limit(), 1e-9);
        assertTrue(play.setLimit(ME, 20.0, NOON), "lower: at once");
        assertFalse(play.setLimit(ME, 500.0, NOON), "higher: waits");
        assertEquals(20.0, play.view(ME, NOON + DAY - 1).limit(), 1e-9);
        assertEquals(500.0, play.view(ME, NOON + DAY).limit(), 1e-9);
        assertFalse(play.setLimit(ME, -1.0, NOON + DAY), "removing a limit waits too");
        // The personal limit and the server cap: the smaller one applies.
        ResponsiblePlay.Player p = play.view(ME, NOON + DAY);
        assertEquals(100.0, ResponsiblePlay.effectiveLimit(p, 100.0), 1e-9);
    }

    @Test
    @DisplayName("self-exclusion refuses every bet until it ends, and can only be extended")
    void exclusion() {
        ResponsiblePlay play = new ResponsiblePlay();
        long until = play.exclude(ME, 7, NOON);
        assertTrue(play.isExcluded(ME, NOON + 6 * DAY));
        assertNotNull(play.refusal(ME, 1.0, 0.0, NOON + DAY));
        assertEquals(until, play.exclude(ME, 1, NOON), "a shorter exclusion does not shorten it");
        assertFalse(play.isExcluded(ME, NOON + 7 * DAY));
        play.exclude(ME, 30, NOON);
        play.lift(ME);
        assertFalse(play.isExcluded(ME, NOON), "an operator can lift it");
    }

    @Test
    @DisplayName("comps earn a point per unit staked and are capped so they cannot be farmed")
    void comps() {
        ResponsiblePlay play = new ResponsiblePlay();
        play.record(ME, 1_999.5, 0.0, 0.005, NOON);
        assertEquals(1_999, play.view(ME, NOON).points());
        long commonBox = ResponsiblePlay.pointsFor(10.0, 0.005);
        assertEquals(2_000, commonBox);
        assertFalse(play.spend(ME, commonBox));
        play.record(ME, 1.0, 1.0, 0.005, NOON);
        assertTrue(play.spend(ME, commonBox));
        assertEquals(0, play.view(ME, NOON).points());
        assertEquals(2_000, ResponsiblePlay.pointsFor(10.0, 0.05), "rates above the cap are capped");
    }
}
