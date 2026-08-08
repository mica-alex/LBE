package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The blacklist's three pattern forms. */
class KeyFilterTest {

    @Test
    @DisplayName("all three forms match what they say they do")
    void allThreeForms() {
        KeyFilter filter = KeyFilter.parse(new String[] {
            "minecraft:barrier",
            "minecraft:spawn_egg#*",
            "creativemod:*",
        });

        assertTrue(filter.matches("minecraft:barrier#0"));
        assertFalse(filter.matches("minecraft:barrier#1"));

        assertTrue(filter.matches("minecraft:spawn_egg#0"));
        assertTrue(filter.matches("minecraft:spawn_egg#120"));

        assertTrue(filter.matches("creativemod:anything#0"));
        assertTrue(filter.matches("creativemod:anything_else#7"));
        assertFalse(filter.matches("othermod:anything#0"));

        assertEquals(3, filter.size());
        assertTrue(filter.problems().isEmpty(), filter.problems().toString());
    }

    @Test
    @DisplayName("comments, blanks, and a line with no mod id")
    void malformedAndIgnored() {
        KeyFilter filter = KeyFilter.parse(new String[] {
            "# a comment",
            "",
            "no_mod_id_here",
            "mod:thing",
        });
        assertEquals(1, filter.size());
        assertEquals(1, filter.problems().size());
        assertTrue(filter.problems().get(0).contains("line 3"));
    }

    @Test
    @DisplayName("an empty filter matches nothing")
    void emptyMatchesNothing() {
        assertTrue(KeyFilter.empty().isEmpty());
        assertFalse(KeyFilter.empty().matches("anything:at_all#0"));
        assertTrue(KeyFilter.parse(null).isEmpty());
    }
}
