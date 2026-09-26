package com.micatechnologies.minecraft.lbe.casino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.lbe.casino.race.PigRace;
import com.micatechnologies.minecraft.lbe.casino.wheel.BigWheel;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The big wheel and the pig race: their layouts, their prices, and that every bet is equal. */
class SharedGamesTest {

    @Test
    @DisplayName("the wheel has exactly the segments it declares")
    void wheelLayout() {
        Map<BigWheel.Segment, Integer> counts = new EnumMap<>(BigWheel.Segment.class);
        for (int i = 0; i < BigWheel.SEGMENTS; i++) {
            counts.merge(BigWheel.segmentAt(i), 1, Integer::sum);
        }
        int total = 0;
        for (BigWheel.Segment segment : BigWheel.Segment.values()) {
            assertEquals(segment.count(), (int) counts.getOrDefault(segment, 0), segment.name());
            total += segment.count();
        }
        assertEquals(BigWheel.SEGMENTS, total);
    }

    @Test
    @DisplayName("every wheel bet returns exactly 96%")
    void wheelIsFair() {
        for (BigWheel.Segment segment : BigWheel.Segment.values()) {
            if (segment.isBettable()) {
                assertEquals(0.96, BigWheel.returnToPlayer(segment), 1e-12, segment.name());
            }
        }
        assertEquals(null, BigWheel.Segment.bettable(BigWheel.Segment.HOUSE.ordinal()),
            "nobody can back the house");
    }

    @Test
    @DisplayName("the big payers are spread round the wheel, not bunched")
    void wheelIsSpread() {
        for (int i = 0; i < BigWheel.SEGMENTS; i++) {
            BigWheel.Segment here = BigWheel.segmentAt(i);
            BigWheel.Segment next = BigWheel.segmentAt(i + 1);
            if (here.pays() >= 7 || here == BigWheel.Segment.HOUSE) {
                assertTrue(next.pays() < 7 && next != BigWheel.Segment.HOUSE,
                    "two rare segments side by side at " + i);
            }
        }
    }

    @Test
    @DisplayName("every pig returns 90%, favourite and long shot alike")
    void raceIsFair() {
        double chances = 0.0;
        for (PigRace.Pig pig : PigRace.Pig.values()) {
            assertEquals(PigRace.RETURN, PigRace.returnToPlayer(pig), 1e-9, pig.name());
            chances += pig.chance();
        }
        assertEquals(1.0, chances, 1e-12);
    }

    @Test
    @DisplayName("races are won as often as each pig's chance says")
    void raceFrequencies() {
        Random random = new Random(42L);
        Map<PigRace.Pig, Integer> wins = new EnumMap<>(PigRace.Pig.class);
        int races = 400_000;
        for (int i = 0; i < races; i++) {
            wins.merge(PigRace.race(random), 1, Integer::sum);
        }
        for (PigRace.Pig pig : PigRace.Pig.values()) {
            assertEquals(pig.chance(), wins.getOrDefault(pig, 0) / (double) races, 0.004,
                pig.name());
        }
    }
}
