package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeRegistry;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

/**
 * The casino's blocks: one {@link BlockCasinoMachine} per {@link CasinoGame}, created in
 * {@code preInit} and handed to {@link LbeRegistry}.
 *
 * <p>These register unconditionally, whether or not SUM is installed. A block that exists only on
 * some servers turns into a missing-model cube when a world moves between them, taking somebody's
 * build with it. The machines are always here; whether they take money is a question asked when
 * somebody uses one.
 */
public final class CasinoBlocks {

    private static final Map<CasinoGame, BlockCasinoMachine> MACHINES =
        new EnumMap<>(CasinoGame.class);

    /**
     * The item form of each machine, kept alongside the block.
     *
     * <p>Held rather than looked up with {@code Item.getItemFromBlock} because that lookup goes
     * through the block-to-item registry mapping, which does not exist yet: the
     * {@code RegistryEvent.Register} events fire after every mod's {@code preInit}, and this map is
     * populated during it.
     */
    private static final Map<CasinoGame, ItemBlock> MACHINE_ITEMS =
        new EnumMap<>(CasinoGame.class);

    private CasinoBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        for (CasinoGame game : CasinoGame.values()) {
            BlockCasinoMachine block = LbeRegistry.addBlock(new BlockCasinoMachine(game));
            MACHINES.put(game, block);
            MACHINE_ITEMS.put(game, registerItemBlock(block));
        }
    }

    /** The machine for a game. Null before {@link #init()}. */
    public static BlockCasinoMachine machine(CasinoGame game) {
        return MACHINES.get(game);
    }

    /** The machine's item form. Null before {@link #init()}. */
    public static ItemBlock machineItem(CasinoGame game) {
        return MACHINE_ITEMS.get(game);
    }

    /** Every machine item, for model binding. */
    public static Map<CasinoGame, ItemBlock> machineItems() {
        return Collections.unmodifiableMap(MACHINE_ITEMS);
    }

    private static ItemBlock registerItemBlock(Block block) {
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        LbeRegistry.addItem(item);
        return item;
    }
}
