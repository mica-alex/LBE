package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Key parsing and matching — the format every config line and every registry lookup goes through. */
class ItemKeysTest {

    @Test
    @DisplayName("normalising appends #0 and lowercases the registry name")
    void normalisation() {
        assertEquals("minecraft:diamond#0", ItemKeys.normalise("Minecraft:Diamond"));
        assertEquals("minecraft:wool#14", ItemKeys.normalise("  minecraft:wool#14  "));
        assertEquals("minecraft:wool#*", ItemKeys.normalise("minecraft:wool#*"));
        assertEquals("mod:thing#0", ItemKeys.normalise("mod:thing#"));
        assertNull(ItemKeys.normalise("   "));
        assertNull(ItemKeys.normalise(null));
    }

    @Test
    @DisplayName("the pieces come back out")
    void decomposition() {
        assertEquals("minecraft:wool", ItemKeys.registryName("minecraft:wool#14"));
        assertEquals("14", ItemKeys.metaPart("minecraft:wool#14"));
        assertEquals(14, ItemKeys.meta("minecraft:wool#14"));
        assertEquals(-1, ItemKeys.meta("minecraft:wool#*"));
        assertEquals("minecraft", ItemKeys.modId("minecraft:wool#14"));
        assertEquals("", ItemKeys.modId("nocolon#0"));
    }

    @Test
    @DisplayName("unparseable metadata widens the match rather than erroring")
    void badMetadataBecomesWildcard() {
        assertEquals(-1, ItemKeys.meta("mod:thing#not_a_number"));
    }

    @Test
    @DisplayName("matching honours the metadata wildcard and nothing else")
    void matching() {
        assertTrue(ItemKeys.matches("minecraft:wool#*", "minecraft:wool#5"));
        assertTrue(ItemKeys.matches("minecraft:wool#5", "minecraft:wool#5"));
        assertFalse(ItemKeys.matches("minecraft:wool#5", "minecraft:wool#6"));
        assertFalse(ItemKeys.matches("minecraft:wool#*", "minecraft:carpet#5"));
        assertFalse(ItemKeys.matches(null, "minecraft:wool#5"));
    }
}
