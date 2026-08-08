package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The override table's grammar and precedence rules.
 *
 * <p>Worth testing thoroughly out of proportion to its size: this file is hand-edited by pack
 * authors, so every accepted form is a promise, and every rejected line is a promise too — it has to
 * be reported rather than swallowed.</p>
 */
class RarityOverridesTest {

    @Test
    @DisplayName("tiers can be named or numbered")
    void bothTierForms() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] {
            "minecraft:diamond=rare",
            "minecraft:emerald=2",
        });
        assertSame(Rarity.RARE, overrides.forKey("minecraft:diamond#0"));
        assertSame(Rarity.RARE, overrides.forKey("minecraft:emerald#0"));
        assertTrue(overrides.problems().isEmpty(), overrides.problems().toString());
    }

    @Test
    @DisplayName("a key with no metadata means metadata 0")
    void metadataDefaultsToZero() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] { "mod:thing=legendary" });
        assertSame(Rarity.LEGENDARY, overrides.forKey("mod:thing#0"));
        assertNull(overrides.forKey("mod:thing#1"));
    }

    @Test
    @DisplayName("a wildcard covers every metadata variant")
    void wildcardCoversVariants() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] { "minecraft:wool#*=common" });
        assertSame(Rarity.COMMON, overrides.forKey("minecraft:wool#0"));
        assertSame(Rarity.COMMON, overrides.forKey("minecraft:wool#14"));
        assertNull(overrides.forKey("minecraft:carpet#0"));
    }

    @Test
    @DisplayName("an exact key beats a wildcard — 'all wool is common, except that one'")
    void exactBeatsWildcard() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] {
            "minecraft:wool#*=common",
            "minecraft:wool#14=legendary",
        });
        assertSame(Rarity.COMMON, overrides.forKey("minecraft:wool#0"));
        assertSame(Rarity.LEGENDARY, overrides.forKey("minecraft:wool#14"));
    }

    @Test
    @DisplayName("comments and blank lines are ignored")
    void commentsIgnored() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] {
            "# this is a comment",
            "",
            "   ",
            "mod:thing=rare",
        });
        assertEquals(1, overrides.size());
        assertTrue(overrides.problems().isEmpty());
    }

    @Test
    @DisplayName("a malformed line is reported, not thrown, and the rest still load")
    void malformedLinesAreReported() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] {
            "mod:good=rare",
            "mod:missing_equals",
            "mod:bad_tier=mythical",
        });
        assertSame(Rarity.RARE, overrides.forKey("mod:good#0"));
        assertEquals(2, overrides.problems().size(), overrides.problems().toString());
        assertTrue(overrides.problems().get(0).contains("line 2"));
        assertTrue(overrides.problems().get(1).contains("line 3"));
    }

    @Test
    @DisplayName("keys are matched case-insensitively on the registry name")
    void registryNamesAreLowercased() {
        RarityOverrides overrides = RarityOverrides.parse(new String[] { "Minecraft:Diamond=rare" });
        assertSame(Rarity.RARE, overrides.forKey("minecraft:diamond#0"));
    }

    @Test
    @DisplayName("an empty table covers nothing")
    void emptyCoversNothing() {
        assertFalse(RarityOverrides.empty().covers("anything:at_all#0"));
        assertNull(RarityOverrides.parse(null).forKey("anything:at_all#0"));
    }
}
