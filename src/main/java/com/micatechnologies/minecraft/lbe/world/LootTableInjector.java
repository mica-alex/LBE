package com.micatechnologies.minecraft.lbe.world;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.block.BlockLootBox;
import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Optionally adds loot boxes to vanilla and modded chest loot tables.
 *
 * <p><b>Off by default</b> ({@code worldgen.injectIntoLootTables}). Natural generation already puts
 * boxes in the world, and a pack author who has balanced their dungeon loot did not ask a newly
 * installed mod to start editing it. Turning this on is a decision, not a default.</p>
 *
 * <p>Boxes are <b>added</b> to a table, never replacing what is already in it. A new pool is appended
 * containing one weighted entry per tier plus a heavily weighted empty entry, so most chests are
 * untouched and the ones that do contain a box usually contain a common one. Replacing existing
 * entries would silently delete loot the pack author put there on purpose, which is not a thing a
 * config toggle should be able to do quietly.</p>
 */
public class LootTableInjector {

    /**
     * Name of the pool we add, used to make injection idempotent.
     *
     * <p>{@code LootTableLoadEvent} fires again on {@code /reload} and on some resource-pack changes,
     * and {@code LootTable.addPool} throws on a duplicate pool name — so without the guard in
     * {@link #onLootTableLoad} the second fire would crash the reload rather than no-op.</p>
     */
    private static final String POOL_NAME = "lbe_loot_boxes";

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        if (!LbeConfig.injectIntoLootTables) {
            return;
        }
        String tableName = event.getName().toString();
        if (!LbeConfig.injectsInto(tableName)) {
            return;
        }
        // A table that already has our pool — a reload, most likely. Adding it twice throws.
        if (event.getTable().getPool(POOL_NAME) != null) {
            return;
        }

        List<LootEntry> entries = new ArrayList<>();
        int emptyWeight = weightOf(LbeConfig.lootTableEmptyWeight, 100);
        entries.add(new LootEntryEmpty(emptyWeight, 0, new LootCondition[0], POOL_NAME + "_empty"));

        boolean addedAnyBox = false;
        for (Rarity tier : Rarity.values()) {
            int weight = weightOf(tierWeight(tier), 0);
            if (weight <= 0) {
                continue;
            }
            BlockLootBox block = LbeBlocks.box(tier);
            Item item = block == null ? null : Item.getItemFromBlock(block);
            if (item == null || item == net.minecraft.init.Items.AIR) {
                // Registration failed for this tier, which is already an error elsewhere. Skipping is
                // better than adding an entry that would hand players an air stack.
                continue;
            }
            entries.add(new LootEntryItem(item, weight, 0, new LootFunction[0],
                new LootCondition[0], POOL_NAME + "_" + tier.id()));
            addedAnyBox = true;
        }

        if (!addedAnyBox) {
            return;
        }
        event.getTable().addPool(new LootPool(entries.toArray(new LootEntry[0]),
            new LootCondition[0], new RandomValueRange(1.0F), new RandomValueRange(0.0F),
            POOL_NAME));
        injectedTables.add(tableName);
        // INFO rather than DEBUG, and worth the handful of lines: whether a prefix in the config
        // actually matched anything is otherwise invisible until someone opens the right chest and
        // notices nothing happened. At most one line per configured table, and only when the
        // feature is switched on at all.
        Lbe.LOGGER.info("Added loot-box pool to {}", tableName);
    }

    /**
     * Tables this session has injected into, for {@code /lbe loottables}.
     *
     * <p>Static and never cleared: loot tables load lazily and only once, so by the time anyone asks,
     * "which of my configured tables has actually been touched?" is a question about the whole
     * session, not about a moment in it.</p>
     */
    private static final java.util.Set<String> injectedTables =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    /** Tables injected into so far this session. */
    public static java.util.List<String> injectedTables() {
        synchronized (injectedTables) {
            return new ArrayList<>(injectedTables);
        }
    }

    /** The pool name added to every injected table. */
    public static String poolName() {
        return POOL_NAME;
    }

    /**
     * Per-tier weight from the config, safe against a short or absent array.
     *
     * <p>Weights are relative to the empty entry: at the defaults a matching chest has roughly a one
     * in six chance of containing a box at all, and a legendary is about one in five hundred of
     * those chests — deliberately rarer than the natural-generation rate, because a chest is
     * something a player is already being rewarded by.</p>
     */
    private static int tierWeight(Rarity tier) {
        int[] weights = LbeConfig.lootTableTierWeights;
        if (weights == null || tier.ordinal() >= weights.length) {
            return 0;
        }
        return weights[tier.ordinal()];
    }

    private static int weightOf(int configured, int fallback) {
        // LootEntry rejects a weight below 1 with an exception at table-build time, which would take
        // out world load rather than the one bad config line that caused it.
        return configured > 0 ? configured : fallback;
    }
}
