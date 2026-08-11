package com.micatechnologies.minecraft.lbe;

import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.block.TileEntityLootBox;
import com.micatechnologies.minecraft.lbe.casino.block.CasinoBlocks;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntitySlotMachine;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.catalog.LootCatalog;
import com.micatechnologies.minecraft.lbe.command.CommandLbe;
import com.micatechnologies.minecraft.lbe.world.LootBoxWorldGen;
import com.micatechnologies.minecraft.lbe.world.LootTableInjector;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for LBE (Loot Box Extravaganza).
 *
 * <p>LBE scatters four tiers of loot box through the world and fills them with items drawn from
 * <b>every installed mod</b>, sorted into those tiers automatically. The sorting is the mod: rather
 * than a hand-written loot table per supported mod — which does not scale past about three mods and
 * is stale the moment a pack updates — it scores every registered item from how it is made and what
 * it is, then cuts the scores into tiers by percentile so the result self-normalises to whatever is
 * installed. Anything the scorer gets wrong can be nailed down in the config.</p>
 *
 * <p>Lifecycle contract for the rest of the mod:</p>
 * <ul>
 *   <li>{@code preInit} — config, blocks/items, tile entity, world generator. Nothing here may
 *       touch a {@code World}, and nothing here may inspect another mod's registry: they are still
 *       filling them.</li>
 *   <li>{@code init} — nothing yet.</li>
 *   <li>{@code postInit} — <b>the catalogue is built here, and it must be.</b> Every other mod has
 *       finished registering its items and recipes by this point and not one moment earlier, so this
 *       is the first instant at which the question "what is in this pack?" has a correct answer.</li>
 *   <li>{@code serverStarting} — the {@code /lbe} command, and the casino's connection to SUM's
 *       economy if that mod is installed. Not earlier: SUM chooses its backend as the server
 *       starts.</li>
 * </ul>
 *
 * <p><b>Side discipline.</b> Everything reachable from this class must be loadable on a dedicated
 * server. Client-only code is reached exclusively through {@link LbeClientProxy}, never directly —
 * a single stray import of a {@code net.minecraft.client} type in common code fails the CI server
 * smoke test.</p>
 */
@Mod(modid = LbeConstants.MOD_NAMESPACE,
     version = LbeConstants.MOD_VERSION,
     name = LbeConstants.MOD_NAME,
     acceptedMinecraftVersions = "[1.12.2]",
     // 'after', not 'required-after': SUM is optional, and LBE loads happily without it.
     //
     // But when it IS present, load order decides who handles FMLServerStartingEvent first, and
     // SUM installs its economy provider on that event. Without this line LBE asked for a handle
     // before SUM had published one and the casino switched itself off on a server that was
     // perfectly well configured — silently, and only on the mod orderings where LBE sorted first.
     dependencies = "after:sum")
public class Lbe {

    public static final Logger LOGGER = LogManager.getLogger(LbeConstants.MOD_NAMESPACE);

    @SidedProxy(clientSide = "com.micatechnologies.minecraft.lbe.LbeClientProxy",
                serverSide = "com.micatechnologies.minecraft.lbe.LbeCommonProxy")
    public static LbeProxy proxy;

    @Mod.Instance(LbeConstants.MOD_NAMESPACE)
    public static Lbe instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LbeConfig.init(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new LootTableInjector());
        com.micatechnologies.minecraft.lbe.network.LbeNetwork.init();
        LbeBlocks.init();
        CasinoBlocks.init();
        GameRegistry.registerTileEntity(TileEntityLootBox.class,
            new ResourceLocation(LbeConstants.MOD_NAMESPACE, "loot_box"));
        GameRegistry.registerTileEntity(TileEntitySlotMachine.class,
            new ResourceLocation(LbeConstants.MOD_NAMESPACE, LbeConstants.SLOT_MACHINE_NAME));
        LbeTab.initTabElements();
        // Weight 0 is the middle of the road: LBE has no opinion about running before or after any
        // other generator, because it only ever writes into air that is already there.
        GameRegistry.registerWorldGenerator(new LootBoxWorldGen(), 0);
        proxy.preInit(event);
        LOGGER.info("I am {} at version {}", LbeConstants.MOD_NAME, LbeConstants.MOD_VERSION);
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(LbeRegistry.getBlocks().toArray(new Block[0]));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(LbeRegistry.getItems().toArray(new Item[0]));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // The one place this can happen. See the lifecycle contract above.
        LootCatalog.rebuild();
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandLbe());
        // Must be here and not earlier: SUM decides which economy backend it is using as the server
        // starts, so asking during preInit is asking before there is an answer.
        LbeEconomy.onServerStarting();
    }

    /**
     * Runs after every mod's {@code serverStarting}, which is the first moment SUM's economy is
     * guaranteed to be published if it is going to be. See {@link LbeEconomy#onServerStarted()}.
     */
    @EventHandler
    public void serverStarted(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) {
        LbeEconomy.onServerStarted();
    }

    @EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        // Otherwise a single-player client carries the first world's economy handle into the next
        // world it opens, which is a different save with a different set of balances.
        LbeEconomy.onServerStopped();
    }
}
