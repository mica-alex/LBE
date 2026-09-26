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

    /** The wall-mounted leaderboard, and its item. */
    private static BlockCasinoLeaderboard leaderboard;
    private static ItemBlock leaderboardItem;

    /** The decor set's items: carpet, stool, rope post and neon sign. For model binding. */
    private static final java.util.List<ItemBlock> DECOR_ITEMS = new java.util.ArrayList<>();

    /** The loot box vendor, and its item. */
    private static BlockLootVendor vendor;
    private static ItemBlock vendorItem;

    /** The progressive jackpot sign, and its item. */
    private static BlockProgressiveSign progressiveSign;
    private static ItemBlock progressiveSignItem;

    private CasinoBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        for (CasinoGame game : CasinoGame.values()) {
            BlockCasinoMachine block = LbeRegistry.addBlock(new BlockCasinoMachine(game));
            MACHINES.put(game, block);
            MACHINE_ITEMS.put(game, registerItemBlock(block));
        }
        leaderboard = LbeRegistry.addBlock(new BlockCasinoLeaderboard());
        leaderboardItem = registerItemBlock(leaderboard);
        progressiveSign = LbeRegistry.addBlock(new BlockProgressiveSign());
        progressiveSignItem = registerItemBlock(progressiveSign);
        vendor = LbeRegistry.addBlock(new BlockLootVendor());
        vendorItem = registerItemBlock(vendor);

        // Decor: no money anywhere near these, so they are plain blocks.
        for (Block decor : new Block[] {
            new com.micatechnologies.minecraft.lbe.casino.decor.BlockCasinoCarpet("casino_carpet"),
            new com.micatechnologies.minecraft.lbe.casino.decor.BlockCasinoCarpet(
                "casino_carpet_royal"),
            new com.micatechnologies.minecraft.lbe.casino.decor.BlockBarStool(),
            new com.micatechnologies.minecraft.lbe.casino.decor.BlockVelvetRopePost(),
            new com.micatechnologies.minecraft.lbe.casino.decor.BlockNeonSign(),
        }) {
            DECOR_ITEMS.add(registerItemBlock(LbeRegistry.addBlock(decor)));
        }
    }

    /** The decor set's items. */
    public static java.util.List<ItemBlock> decorItems() {
        return Collections.unmodifiableList(DECOR_ITEMS);
    }

    /** The vendor's item form. Null before {@link #init()}. */
    public static ItemBlock vendorItem() {
        return vendorItem;
    }

    /** The progressive sign's item form. Null before {@link #init()}. */
    public static ItemBlock progressiveSignItem() {
        return progressiveSignItem;
    }

    /** The leaderboard's item form. Null before {@link #init()}. */
    public static ItemBlock leaderboardItem() {
        return leaderboardItem;
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
