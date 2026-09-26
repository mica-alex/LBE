package com.micatechnologies.minecraft.lbe.command;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.block.CasinoStatsData;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
import com.micatechnologies.minecraft.lbe.casino.stats.ResponsiblePlay;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * {@code /casino}: what the casino remembers, for the people who played in it.
 *
 * <p>Separate from {@code /lbe}, which is operator-only as a whole on purpose. This one is open to
 * every player, with one line drawn inside it: your own record is yours to see, and anyone else's
 * is for operators. The public view is {@code top}, which shows exactly what a leaderboard block
 * shows and honours the same config.
 */
public class CommandCasino extends CommandBase {

    /** Permission level needed to look up another player's record. */
    private static final int LOOK_UP_OTHERS = 2;

    @Override
    public String getName() {
        return "casino";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/casino <stats [player]|top|comps|redeem <tier>|limit [amount|off]"
            + "|exclude <days> confirm|lift <player>>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        CasinoStatsData data = CasinoStatsData.get(server.getEntityWorld());
        CasinoLedger ledger = data.ledger();
        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("stats".equals(subcommand)) {
            stats(sender, ledger, args);
        } else if ("top".equals(subcommand)) {
            top(sender, ledger);
        } else if ("comps".equals(subcommand)) {
            comps(sender, data);
        } else if ("redeem".equals(subcommand)) {
            redeem(sender, data, args);
        } else if ("limit".equals(subcommand)) {
            limit(sender, data, args);
        } else if ("exclude".equals(subcommand)) {
            exclude(sender, data, args);
        } else if ("lift".equals(subcommand)) {
            lift(server, sender, data, args);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void stats(ICommandSender sender, CasinoLedger ledger, String[] args)
            throws CommandException {
        UUID id;
        CasinoLedger.Player player;
        if (args.length >= 2) {
            if (!sender.canUseCommand(LOOK_UP_OTHERS, getName())) {
                throw new CommandException("Only operators can look up another player's record.");
            }
            Map.Entry<UUID, CasinoLedger.Player> found = ledger.playerNamed(args[1]);
            if (found == null) {
                throw new CommandException(args[1] + " has never played at the casino.");
            }
            id = found.getKey();
            player = found.getValue();
        } else {
            EntityPlayer self = getCommandSenderAsPlayer(sender);
            id = self.getUniqueID();
            player = ledger.player(id);
            if (player == null) {
                send(sender, TextFormatting.GRAY, "You have not played at the casino yet.");
                return;
            }
        }

        CasinoLedger.Tally total = player.total();
        send(sender, TextFormatting.GOLD, "Casino record for " + player.name());
        for (Map.Entry<String, CasinoLedger.Tally> entry : player.games().entrySet()) {
            CasinoLedger.Tally tally = entry.getValue();
            CasinoGame game = CasinoGame.byRegistryName(entry.getKey());
            String name = game == null ? entry.getKey() : game.displayName();
            send(sender, TextFormatting.WHITE, String.format(java.util.Locale.ROOT,
                "  %s: %d rounds, staked %s, %s", name, tally.rounds(),
                LbeEconomy.format(tally.staked()), signed(tally.net())));
        }
        send(sender, TextFormatting.YELLOW, String.format(java.util.Locale.ROOT,
            "  Overall: %d rounds, staked %s, %s. Best round paid %s.", total.rounds(),
            LbeEconomy.format(total.staked()), signed(total.net()),
            LbeEconomy.format(total.biggestReturn())));
    }

    private void top(ICommandSender sender, CasinoLedger ledger) {
        send(sender, TextFormatting.GOLD, "Biggest winners");
        List<CasinoLedger.Player> winners = ledger.topWinners(5);
        if (winners.isEmpty()) {
            send(sender, TextFormatting.GRAY, "  Nobody is ahead of the house yet.");
        }
        int rank = 1;
        for (CasinoLedger.Player player : winners) {
            send(sender, TextFormatting.WHITE, "  " + rank++ + ". "
                + displayName(player.name()) + "  " + signed(player.total().net()));
        }
        send(sender, TextFormatting.GOLD, "Recent big wins");
        List<CasinoLedger.BigWin> wins = ledger.recentBigWins();
        if (wins.isEmpty()) {
            send(sender, TextFormatting.GRAY, "  None yet.");
        }
        for (CasinoLedger.BigWin win : wins.subList(0, Math.min(5, wins.size()))) {
            send(sender, TextFormatting.WHITE, "  " + displayName(win.name()) + " won "
                + LbeEconomy.format(win.amount()) + " at " + win.game());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Comps
    // ---------------------------------------------------------------------------------------------

    private void comps(ICommandSender sender, CasinoStatsData data) throws CommandException {
        EntityPlayer self = getCommandSenderAsPlayer(sender);
        if (LbeConfig.compRate <= 0.0) {
            send(sender, TextFormatting.GRAY, "Comps are turned off on this server.");
            return;
        }
        long points = data.responsible().view(self.getUniqueID(), System.currentTimeMillis())
            .points();
        send(sender, TextFormatting.GOLD, "You have " + points + " comp points. Redeem them with "
            + "/casino redeem <tier>:");
        for (Rarity tier : Rarity.values()) {
            send(sender, TextFormatting.WHITE, "  " + tier.id() + ": " + boxCost(tier) + " points");
        }
    }

    private static long boxCost(Rarity tier) {
        return ResponsiblePlay.pointsFor(LbeConfig.boxPrice(tier), LbeConfig.compRate);
    }

    private void redeem(ICommandSender sender, CasinoStatsData data, String[] args)
            throws CommandException {
        EntityPlayer self = getCommandSenderAsPlayer(sender);
        if (args.length < 2) {
            throw new WrongUsageException("/casino redeem <common|uncommon|rare|legendary>");
        }
        Rarity tier = Rarity.byId(args[1]);
        if (tier == null) {
            throw new CommandException("There is no '" + args[1] + "' tier.");
        }
        if (LbeConfig.compRate <= 0.0) {
            throw new CommandException("Comps are turned off on this server.");
        }
        long cost = boxCost(tier);
        if (!data.responsible().spend(self.getUniqueID(), cost)) {
            throw new CommandException("A " + tier.id() + " box costs " + cost
                + " comp points, and you have " + data.responsible()
                    .view(self.getUniqueID(), System.currentTimeMillis()).points() + ".");
        }
        data.changed();
        net.minecraft.item.ItemStack box = LbeBlocks.box(tier).createStack(self.getRNG());
        if (!self.inventory.addItemStackToInventory(box)) {
            self.dropItem(box, false);
        }
        send(sender, TextFormatting.GREEN, "Redeemed " + cost + " points for a " + tier.id()
            + " loot box.");
    }

    // ---------------------------------------------------------------------------------------------
    // Limits and exclusion
    // ---------------------------------------------------------------------------------------------

    private void limit(ICommandSender sender, CasinoStatsData data, String[] args)
            throws CommandException {
        EntityPlayer self = getCommandSenderAsPlayer(sender);
        long now = System.currentTimeMillis();
        ResponsiblePlay care = data.responsible();
        if (args.length >= 2) {
            double amount;
            if ("off".equalsIgnoreCase(args[1])) {
                amount = -1.0;
            } else {
                amount = parseDouble(args[1], 0.0, 1.0E9);
            }
            boolean immediate = care.setLimit(self.getUniqueID(), amount, now);
            data.changed();
            if (immediate) {
                send(sender, TextFormatting.GREEN, "Your daily loss limit is now "
                    + LbeEconomy.format(amount) + ", starting now.");
            } else {
                send(sender, TextFormatting.YELLOW, (amount < 0.0 ? "Removing your limit"
                    : "Raising your limit to " + LbeEconomy.format(amount))
                    + " takes effect in 24 hours. Lowering it is always immediate.");
            }
        }
        ResponsiblePlay.Player p = care.view(self.getUniqueID(), now);
        double limit = ResponsiblePlay.effectiveLimit(p, LbeConfig.dailyLossCap);
        send(sender, TextFormatting.GOLD, "Lost today: " + LbeEconomy.format(p.lossToday())
            + (limit >= 0.0 ? " of " + LbeEconomy.format(limit) + " allowed" : " (no limit)")
            + ". Days reset at midnight UTC.");
        if (LbeConfig.dailyLossCap > 0.0) {
            send(sender, TextFormatting.GRAY, "  Server cap: " + LbeEconomy.format(
                LbeConfig.dailyLossCap));
        }
        if (p.limit() >= 0.0) {
            send(sender, TextFormatting.GRAY, "  Your limit: " + LbeEconomy.format(p.limit()));
        }
        if (p.hasPendingRaise()) {
            long hours = Math.max(1L, (p.pendingAt() - now) / 3_600_000L);
            send(sender, TextFormatting.GRAY, "  Changing to " + (p.pendingLimit() < 0.0 ? "no limit"
                : LbeEconomy.format(p.pendingLimit())) + " in about " + hours + "h.");
        }
    }

    private void exclude(ICommandSender sender, CasinoStatsData data, String[] args)
            throws CommandException {
        EntityPlayer self = getCommandSenderAsPlayer(sender);
        if (args.length < 2) {
            throw new WrongUsageException("/casino exclude <days> confirm");
        }
        int days = parseInt(args[1], 1, 3650);
        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            // It cannot be undone, so it is never one keystroke away.
            send(sender, TextFormatting.YELLOW, "This bars you from every casino machine and the "
                + "loot box vendor for " + days + " day" + (days == 1 ? "" : "s") + ", and you "
                + "cannot end it early. To go ahead: /casino exclude " + days + " confirm");
            return;
        }
        long until = data.responsible().exclude(self.getUniqueID(), days,
            System.currentTimeMillis());
        data.changed();
        java.text.SimpleDateFormat format =
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", java.util.Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        send(sender, TextFormatting.GREEN, "You are excluded from the casino until "
            + format.format(new java.util.Date(until)) + ". Look after yourself.");
    }

    private void lift(MinecraftServer server, ICommandSender sender, CasinoStatsData data,
                      String[] args) throws CommandException {
        if (!sender.canUseCommand(LOOK_UP_OTHERS, getName())) {
            throw new CommandException("Only operators can lift an exclusion.");
        }
        if (args.length < 2) {
            throw new WrongUsageException("/casino lift <player>");
        }
        UUID id;
        EntityPlayer online = server.getPlayerList().getPlayerByUsername(args[1]);
        if (online != null) {
            id = online.getUniqueID();
        } else {
            Map.Entry<UUID, CasinoLedger.Player> known = data.ledger().playerNamed(args[1]);
            if (known == null) {
                throw new CommandException("No record of a player called " + args[1] + ".");
            }
            id = known.getKey();
        }
        data.responsible().lift(id);
        data.changed();
        send(sender, TextFormatting.GREEN, "Lifted " + args[1] + "'s exclusion.");
    }

    /** A name as the public board shows it: the name, unless the server keeps them private. */
    static String displayName(String name) {
        return LbeConfig.leaderboardShowsNames ? name : "A player";
    }

    /** "+$12.50" or "-$3.00", which reads better than "$-3.00". */
    static String signed(double amount) {
        return (amount < 0.0 ? "-" : "+") + LbeEconomy.format(Math.abs(amount));
    }

    private static void send(ICommandSender sender, TextFormatting colour, String text) {
        TextComponentString line = new TextComponentString(text);
        line.getStyle().setColor(colour);
        sender.sendMessage(line);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "stats", "top", "comps", "redeem",
                "limit", "exclude", "lift");
        }
        if (args.length == 2 && "redeem".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "common", "uncommon", "rare", "legendary");
        }
        if (args.length == 2 && "lift".equalsIgnoreCase(args[0])
                && sender.canUseCommand(LOOK_UP_OTHERS, getName())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])
                && sender.canUseCommand(LOOK_UP_OTHERS, getName())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
