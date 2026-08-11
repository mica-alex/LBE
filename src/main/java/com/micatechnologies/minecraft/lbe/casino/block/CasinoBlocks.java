package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/**
 * The casino's blocks, created in {@code preInit} and handed to {@link LbeRegistry}.
 *
 * <p>One so far. The list is expected to grow — a table per game — which is why this is a holder
 * rather than a line in the main mod class.
 *
 * <p>These register unconditionally, whether or not SUM is installed. A block that exists only on
 * some servers is a block that turns into a missing-texture cube when a world moves between them,
 * taking the player's build with it. The machines are always here; whether they take money is a
 * question asked when somebody uses one.
 */
public final class CasinoBlocks {

    private static BlockSlotMachine slotMachine;
    private static ItemBlock slotMachineItem;

    private CasinoBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        slotMachine = LbeRegistry.addBlock(new BlockSlotMachine());
        slotMachineItem = registerItemBlock(slotMachine);
    }

    /** The slot machine block. Null before {@link #init()}. */
    public static BlockSlotMachine slotMachine() {
        return slotMachine;
    }

    /** The slot machine's item form. Null before {@link #init()}. */
    public static ItemBlock slotMachineItem() {
        return slotMachineItem;
    }

    private static ItemBlock registerItemBlock(Block block) {
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        LbeRegistry.addItem(item);
        return item;
    }
}
