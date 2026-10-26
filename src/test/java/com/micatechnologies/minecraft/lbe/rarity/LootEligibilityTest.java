package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What may be loot at all. The rows come from a real 145-mod pack, where every one of these was
 * sitting in the common or rare pool.
 */
class LootEligibilityTest {

    private static LootEligibility withNameCheck() {
        return new LootEligibility(null, null, true, null);
    }

    @Test
    @DisplayName("creative and admin items are caught by their path or by their English name")
    void nameCheckCatchesTheRealPackRows() {
        LootEligibility filter = withNameCheck();
        String[][] rows = {
            { "ic2:te#86", "Creative Generator" },
            { "buildcraftcore:engine#3", "Creative Engine" },
            { "immersiveengineering:metal_device0#3", "Creative Capacitor" },
            { "railcraft:admin_steam_producer#0", "Admin Steam Producer" },
            { "railcraft:cart_worldspike_admin#0", "Admin worldspike cart" },
            { "railcraft:locomotive_creative#0", null },
            { "opencomputers:casecreative#0", "Creative computer case" },
            { "littleframes:creative_frame#0", "Creative frame" },
            { "securitycraft:admin_tool#0", "Admin tool" },
            { "somemod:creativeengine#0", "CreativeEngine" },
        };
        for (String[] row : rows) {
            assertEquals(LootEligibility.CREATIVE_NAME, filter.exclusionReason(row[0], row[1]),
                row[0] + " / " + row[1]);
        }
    }

    @Test
    @DisplayName("a word has to START with the marker, so ordinary names are left alone")
    void ordinaryNamesSurvive() {
        LootEligibility filter = withNameCheck();
        assertNull(filter.exclusionReason("minecraft:diamond_pickaxe#0", "Diamond Pickaxe"));
        assertNull(filter.exclusionReason("sports:badminton_racket#0", "Badminton Racket"));
        assertNull(filter.exclusionReason("mod:procreative_seed#0", "Procreative Seed"));
        // The path alone does not catch a run-together name; that is what the display name is for.
        assertFalse(LootEligibility.looksCreativeOrAdmin("casecreative"));
        assertTrue(LootEligibility.looksCreativeOrAdmin("Creative computer case"));
    }

    @Test
    @DisplayName("turning the name check off turns it off")
    void nameCheckIsOptional() {
        LootEligibility filter = new LootEligibility(null, null, false, null);
        assertNull(filter.exclusionReason("ic2:te#86", "Creative Generator"));
    }

    @Test
    @DisplayName("the creative list catches what no name gives away, and runs without the name check")
    void creativeListCoversTheRest() {
        LootEligibility filter = new LootEligibility(null,
            KeyFilter.parse(new String[] { "buildcraftlib:debugger", "customnpcs:*" }), false, null);
        assertEquals(LootEligibility.CREATIVE_LIST,
            filter.exclusionReason("buildcraftlib:debugger#0", "Debugger"));
        assertEquals(LootEligibility.CREATIVE_LIST,
            filter.exclusionReason("customnpcs:npcwand#0", "NPC Wand"));
        assertNull(filter.exclusionReason("minecraft:bread#0", "Bread"));
    }

    @Test
    @DisplayName("the blacklist names itself first, and a non-empty whitelist excludes everything else")
    void blacklistAndWhitelist() {
        LootEligibility filter = new LootEligibility(
            KeyFilter.parse(new String[] { "minecraft:command_block_minecart" }), null, true,
            KeyFilter.parse(new String[] { "minecraft:*" }));
        assertEquals(LootEligibility.BLACKLIST,
            filter.exclusionReason("minecraft:command_block_minecart#0", "Minecart with Command Block"));
        assertEquals(LootEligibility.NOT_WHITELISTED,
            filter.exclusionReason("ic2:copper_ingot#0", "Copper Ingot"));
        assertNull(filter.exclusionReason("minecraft:bread#0", "Bread"));
    }

    @Test
    @DisplayName("an empty whitelist restricts nothing")
    void emptyWhitelistMeansEverything() {
        LootEligibility filter = new LootEligibility(null, null, true,
            KeyFilter.parse(new String[] { "# only a comment" }));
        assertNull(filter.exclusionReason("ic2:copper_ingot#0", "Copper Ingot"));
    }
}
