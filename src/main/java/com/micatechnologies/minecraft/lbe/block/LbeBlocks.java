package com.micatechnologies.minecraft.lbe.block;

import com.micatechnologies.minecraft.lbe.LbeRegistry;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/**
 * Every block LBE adds: one {@link BlockLootBox} per {@link Rarity}, created once and handed to
 * {@link LbeRegistry}.
 *
 * <p>Instantiated from {@code preInit}; the {@code RegistryEvent.Register} handlers in the main mod
 * class drain {@link LbeRegistry} afterwards (blocks first, then items — which include these blocks'
 * item forms).</p>
 */
public final class LbeBlocks {

    private static final Map<Rarity, BlockLootBox> BOXES = new EnumMap<>(Rarity.class);

    /**
     * The item form of each box, kept alongside the block.
     *
     * <p>Held rather than looked up with {@code Item.getItemFromBlock} because that lookup goes
     * through the block↔item registry mapping, which does not exist yet: in 1.12.2 the
     * {@code RegistryEvent.Register} events fire <b>after</b> every mod's {@code preInit}, and this
     * map is populated during it. Anything running in {@code preInit} that needs a box's item —
     * the creative tab icon, most obviously — must come here for it.</p>
     */
    private static final Map<Rarity, ItemBlock> BOX_ITEMS = new EnumMap<>(Rarity.class);

    private LbeBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        for (Rarity rarity : Rarity.values()) {
            BlockLootBox block = LbeRegistry.addBlock(new BlockLootBox(rarity));
            BOXES.put(rarity, block);
            BOX_ITEMS.put(rarity, registerItemBlock(block));
        }
    }

    /** The box block for a tier. {@code null} before {@link #init()} has run. */
    public static BlockLootBox box(Rarity rarity) {
        return BOXES.get(rarity);
    }

    /** The box item for a tier. {@code null} before {@link #init()} has run. */
    public static ItemBlock boxItem(Rarity rarity) {
        return BOX_ITEMS.get(rarity);
    }

    /** Every box block, in tier order. */
    public static Map<Rarity, BlockLootBox> boxes() {
        return Collections.unmodifiableMap(BOXES);
    }

    /** Every box item, in tier order. */
    public static Map<Rarity, ItemBlock> boxItems() {
        return Collections.unmodifiableMap(BOX_ITEMS);
    }

    private static ItemBlock registerItemBlock(Block block) {
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        LbeRegistry.addItem(item);
        return item;
    }
}
