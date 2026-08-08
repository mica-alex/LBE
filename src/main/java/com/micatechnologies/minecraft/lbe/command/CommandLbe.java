package com.micatechnologies.minecraft.lbe.command;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.block.BlockLootBox;
import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.catalog.ForgeItemGraph;
import com.micatechnologies.minecraft.lbe.catalog.LootCatalog;
import com.micatechnologies.minecraft.lbe.rarity.ItemKeys;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import com.micatechnologies.minecraft.lbe.world.LootTableInjector;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/**
 * {@code /lbe} — the operator-facing view of the rarity engine.
 *
 * <p>This is a diagnostic tool first and a convenience second, and it exists because the alternative
 * is untenable: the scoring model makes tens of thousands of judgements about a pack nobody has
 * inspected, and without a way to ask "why is this item rare?" a pack author's only recourse when
 * something looks wrong is to guess at a weight and restart the server.</p>
 *
 * <pre>
 *   /lbe rarity [item]     the tier of the held item (or a named one), with the full score breakdown
 *   /lbe dump              write the whole scored catalogue to rarity-dumps/
 *   /lbe reload            re-read the config and rebuild the catalogue
 *   /lbe give &lt;tier&gt; [n]   give yourself loot boxes
 *   /lbe place &lt;tier&gt;      place a box where you are looking
 * </pre>
 */
public class CommandLbe extends CommandBase {

    @Override
    public String getName() {
        return "lbe";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/lbe <rarity|dump|reload|give|place|loottables>";
    }

    /**
     * Operator level 2.
     *
     * <p>{@code reload} rebuilds the catalogue — a full registry-and-recipe pass that stalls the
     * server thread for around a second on a large pack — and {@code give} produces items from thin
     * air. Neither belongs in the hands of an ordinary player, so the whole command is gated rather
     * than gating the subcommands individually and getting one of them wrong later.</p>
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("rarity".equals(subcommand)) {
            rarity(sender, args);
        }
        else if ("dump".equals(subcommand)) {
            dump(server, sender);
        }
        else if ("reload".equals(subcommand)) {
            reload(sender);
        }
        else if ("give".equals(subcommand)) {
            give(sender, args);
        }
        else if ("place".equals(subcommand)) {
            place(sender, args);
        }
        else if ("loottables".equals(subcommand)) {
            lootTables(server, sender);
        }
        else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void rarity(ICommandSender sender, String[] args) throws CommandException {
        String key;
        if (args.length >= 2) {
            key = ItemKeys.normalise(args[1]);
        }
        else {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ItemStack held = player.getHeldItemMainhand();
            key = ForgeItemGraph.keyOf(held);
            if (key == null) {
                throw new CommandException("lbe.command.rarity.nothing_held");
            }
        }
        // Sent line by line rather than as one message: the breakdown is a dozen lines, and the chat
        // window renders a single multi-line component as one unwrappable blob.
        for (String line : LootCatalog.explain(key).split("\n")) {
            sender.sendMessage(new TextComponentString(line));
        }
    }

    private void dump(MinecraftServer server, ICommandSender sender) throws CommandException {
        File target = new File(server.getDataDirectory(),
            "rarity-dumps/lbe-catalogue-" + System.currentTimeMillis() + ".tsv");
        try {
            int lines = LootCatalog.dumpTo(target);
            sender.sendMessage(new TextComponentString(
                "Wrote " + lines + " entries to " + target.getPath()));
        }
        catch (java.io.IOException e) {
            throw new CommandException("Could not write the dump: " + e.getMessage());
        }
    }

    /**
     * Force every configured loot table to load and report which ones got a loot-box pool.
     *
     * <p>Exists because loot-table injection is otherwise <b>unobservable</b>. Tables load lazily —
     * the first time something asks for one — so a prefix in the config that matches nothing looks
     * exactly like a prefix that matches something you have not opened a chest from yet, and the
     * pack author finds out weeks later. Asking for each table here triggers
     * {@code LootTableLoadEvent} on the spot and turns "did my config work?" into a question with an
     * immediate answer.</p>
     */
    private void lootTables(MinecraftServer server, ICommandSender sender) {
        if (!LbeConfig.injectIntoLootTables) {
            sender.sendMessage(new TextComponentString(
                "Loot-table injection is OFF (worldgen.injectIntoLootTables)."));
            return;
        }
        net.minecraft.world.storage.loot.LootTableManager manager =
            server.getWorld(0).getLootTableManager();

        int matched = 0;
        int missing = 0;
        for (String target : LbeConfig.lootTableTargets) {
            String name = target == null ? "" : target.trim();
            if (name.isEmpty() || name.charAt(0) == '#') {
                continue;
            }
            // A prefix entry ('minecraft:chests/') is not itself a loadable table, so asking for it
            // would be meaningless; it can only be confirmed by whatever it matches.
            if (name.endsWith("/")) {
                sender.sendMessage(new TextComponentString(
                    "  " + name + " — prefix, matches on demand"));
                continue;
            }
            net.minecraft.world.storage.loot.LootTable table =
                manager.getLootTableFromLocation(new net.minecraft.util.ResourceLocation(name));
            boolean injected = table != null
                && table != net.minecraft.world.storage.loot.LootTable.EMPTY_LOOT_TABLE
                && table.getPool(LootTableInjector.poolName()) != null;
            if (injected) {
                matched++;
            }
            else {
                missing++;
            }
            sender.sendMessage(new TextComponentString(
                (injected ? "  §a✔ " : "  §c✘ ") + name
                    + (injected ? "" : " — no pool added (table missing, or not matched)")));
        }
        sender.sendMessage(new TextComponentString(
            matched + " table(s) carry a loot-box pool, " + missing + " did not."));
    }

    private void reload(ICommandSender sender) {
        LbeConfig.reload();
        LootCatalog.rebuild();
        sender.sendMessage(new TextComponentString(
            "Config reloaded; catalogue rebuilt (" + LootCatalog.table().size() + " items)."));
    }

    private void give(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/lbe give <" + tierList() + "> [count]");
        }
        Rarity tier = requireTier(args[1]);
        int count = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        BlockLootBox block = requireBox(tier);

        for (int i = 0; i < count; i++) {
            // One stack per box, each with its own seed — see BlockLootBox.createStack. Giving a
            // single stack of N would make all N boxes share one roll, which is exactly the bug the
            // seed exists to prevent.
            ItemStack stack = block.createStack(player.getRNG());
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
        }
        sender.sendMessage(new TextComponentString(
            "Gave " + count + " " + tier.id() + " loot box" + (count == 1 ? "" : "es") + "."));
    }

    private void place(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/lbe place <" + tierList() + ">");
        }
        Rarity tier = requireTier(args[1]);
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        BlockLootBox block = requireBox(tier);
        BlockPos pos = player.getPosition();
        player.world.setBlockState(pos, block.getDefaultState(), 2);
        sender.sendMessage(new TextComponentString(
            "Placed a " + tier.id() + " loot box at " + pos.getX() + " " + pos.getY() + " "
                + pos.getZ() + "."));
    }

    private static Rarity requireTier(String text) throws CommandException {
        Rarity tier = Rarity.byId(text);
        if (tier == null) {
            throw new CommandException("'" + text + "' is not a tier. Expected one of: " + tierList());
        }
        return tier;
    }

    private static BlockLootBox requireBox(Rarity tier) throws CommandException {
        BlockLootBox block = LbeBlocks.box(tier);
        if (block == null) {
            throw new CommandException("The " + tier.id() + " loot box block is not registered.");
        }
        return block;
    }

    private static String tierList() {
        StringBuilder out = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            if (out.length() > 0) {
                out.append('|');
            }
            out.append(rarity.id());
        }
        return out.toString();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args,
                "rarity", "dump", "reload", "give", "place", "loottables");
        }
        if (args.length == 2 && ("give".equalsIgnoreCase(args[0]) || "place".equalsIgnoreCase(args[0]))) {
            List<String> tiers = new ArrayList<>();
            for (Rarity rarity : Rarity.values()) {
                tiers.add(rarity.id());
            }
            return getListOfStringsMatchingLastWord(args, tiers);
        }
        if (args.length == 2 && "rarity".equalsIgnoreCase(args[0])) {
            // Completing over every item in the pack would be tens of thousands of strings sent to
            // the client on every keystroke. The held-item form of the command is the one to use.
            return java.util.Collections.emptyList();
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("lootbox");
    }
}
