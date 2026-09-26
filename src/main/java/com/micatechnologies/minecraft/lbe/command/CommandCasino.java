package com.micatechnologies.minecraft.lbe.command;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.block.CasinoStatsData;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
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
        return "/casino <stats [player]|top>";
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
        CasinoLedger ledger = CasinoStatsData.get(server.getEntityWorld()).ledger();
        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("stats".equals(subcommand)) {
            stats(sender, ledger, args);
        } else if ("top".equals(subcommand)) {
            top(sender, ledger);
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
            return getListOfStringsMatchingLastWord(args, "stats", "top");
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])
                && sender.canUseCommand(LOOK_UP_OTHERS, getName())) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
