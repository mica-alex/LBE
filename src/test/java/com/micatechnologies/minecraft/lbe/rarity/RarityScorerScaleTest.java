package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scorer at kitchen-sink-pack scale.
 *
 * <p>{@link RarityScorerTest} proves the model's behaviour on graphs small enough to read;
 * this test proves the walk stays <i>bounded</i> on a graph the size of a real 1.12 mega-pack
 * registry, in its worst shape: cycles at the very bottom, so that essentially every item in the
 * pack is transitively barred from the permanent cache and only the per-query scoped memo stands
 * between {@code scoreAll} and exponential path-walking.</p>
 *
 * <p>The timeout is deliberately generous — it is here to catch a complexity regression (minutes
 * to forever), not to benchmark a laptop. When this fails, something has reintroduced per-path
 * walking; see rule 5 in CLAUDE.md and the class doc on {@link RarityScorer}.</p>
 */
class RarityScorerScaleTest {

    @Test
    @DisplayName("a 10k-item pack whose whole registry sits above cycles scores in seconds")
    void tenThousandItemsAboveCyclesScoreInSeconds() {
        // Seeded: the graph must be the same on every run, or a failure is not reproducible.
        Random random = new Random(20260812L);
        FakeItemGraph graph = new FakeItemGraph();
        int total = 10_000;

        // The bottom of the pack: 500 "materials", every pair locked in an ore <-> dust style
        // cycle. This is the adversarial part — every recipe in the pack eventually reaches one
        // of these, so no result anywhere may enter the permanent cache.
        for (int i = 0; i < 500; i += 2) {
            graph.crafted(key(i), 1, key(i + 1));
            graph.crafted(key(i + 1), 1, key(i));
        }

        // The rest: layered like a real pack. Each item crafts from 2..8 slots drawn from
        // anything below it, and every third slot is an ore-dictionary style slot with several
        // alternatives, which is what fans the walk out in real packs.
        for (int i = 500; i < total; i++) {
            int slotCount = 2 + random.nextInt(7);
            java.util.List<java.util.List<String>> slots = new java.util.ArrayList<>(slotCount);
            for (int s = 0; s < slotCount; s++) {
                if (s % 3 == 2) {
                    int alternatives = 2 + random.nextInt(6);
                    java.util.List<String> slot = new java.util.ArrayList<>(alternatives);
                    for (int a = 0; a < alternatives; a++) {
                        slot.add(key(random.nextInt(i)));
                    }
                    slots.add(slot);
                }
                else {
                    slots.add(java.util.Collections.singletonList(key(random.nextInt(i))));
                }
            }
            graph.craftedFromSlots(key(i), 1 + random.nextInt(4), slots);
        }

        RarityScorer scorer = new RarityScorer(graph, new RarityWeights());
        long startedAt = System.nanoTime();
        Map<String, Double> scores = assertTimeoutPreemptively(Duration.ofSeconds(60),
            () -> scorer.scoreAll(),
            "scoring must stay near-linear on a cycle-dense mega-pack");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(total, scores.size());
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            assertTrue(Double.isFinite(entry.getValue()),
                entry.getKey() + " must have a finite score");
        }
        // Not an assertion — a data point for whoever is reading the test output.
        System.out.println("[scale] scored " + total + " cycle-tainted items in " + elapsedMs + " ms");
    }

    private static String key(int index) {
        return "pack:item" + index + "#0";
    }
}
