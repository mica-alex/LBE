package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.economy.Wager;
import com.micatechnologies.minecraft.lbe.casino.race.PigRace;
import com.micatechnologies.minecraft.lbe.casino.wheel.BigWheel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * A machine whose players share one round: the big wheel and the pig race.
 *
 * <h2>A round</h2>
 *
 * <ol>
 *   <li><b>Idle</b> until someone bets. The first bet opens a betting window of
 *       {@link CasinoFanfare#BETTING_WINDOW_TICKS}; anyone can add bets until it closes.</li>
 *   <li>When it closes, one spin (or one race) decides <b>every</b> bet, and each is settled
 *       through the same bank as any other game. Each player then gets their result, the room its
 *       show, and the floor its effects, all held for {@link CasinoFanfare#SHARED_REVEAL_TICKS}
 *       like every other reveal.</li>
 *   <li>The result stays up briefly, then the machine is idle again.</li>
 * </ol>
 *
 * <p>Nothing about a round is saved. Bets waiting on a round are refunded if the machine unloads,
 * exactly as an open blackjack hand is. A player who logs off keeps their bets in the round; the
 * bank pays a winner only while they are online and otherwise leaves the stake held, so nothing is
 * lost either way.
 */
public class TileEntitySharedTable extends TileEntityCasinoMachine implements ITickable {

    /** Where a round is. The ordinals are synced to clients. */
    public enum Phase {
        IDLE, OPEN, REVEALING
    }

    /** How many bets one player may have on one round. */
    private static final int MAX_BETS_PER_PLAYER = 8;

    /** How long the result stays up after the reveal, before the machine goes idle again. */
    private static final int RESULT_TICKS = 60;

    private static final class Bet {
        final UUID player;
        final String name;
        final int option;
        final Wager wager;

        Bet(UUID player, String name, int option, Wager wager) {
            this.player = player;
            this.name = name;
            this.option = option;
            this.wager = wager;
        }
    }

    private final List<Bet> bets = new ArrayList<>();
    private final Random rounds = new Random();
    private Phase phase = Phase.IDLE;
    private long closesAt;
    private long idleAt;

    /** Synced for the renderer: the last outcome and the seed that animates it. */
    private int outcome = -1;
    private long seed;

    /** Client only: ticks left in the window as sent, and when it arrived. */
    private int closeIn;
    private long phaseArrivedAt = -1L;

    private CasinoGame game() {
        if (world == null) {
            return null;
        }
        Block block = world.getBlockState(pos).getBlock();
        return block instanceof BlockCasinoMachine ? ((BlockCasinoMachine) block).game() : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Taking bets
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void joinRound(EntityPlayerMP player, CasinoGame game, int option, Wager wager,
                             double bet) {
        if (phase == Phase.REVEALING) {
            wager.cancel();
            reject(player, "No more bets while the " + noun(game) + " runs. Bet again in a moment.");
            return;
        }
        int mine = 0;
        for (Bet existing : bets) {
            mine += existing.player.equals(player.getUniqueID()) ? 1 : 0;
        }
        if (mine >= MAX_BETS_PER_PLAYER) {
            wager.cancel();
            reject(player, "That is as many bets as one player can have on a round.");
            return;
        }
        bets.add(new Bet(player.getUniqueID(), player.getName(), option, wager));
        long now = world.getTotalWorldTime();
        if (phase == Phase.IDLE) {
            phase = Phase.OPEN;
            closesAt = now + CasinoFanfare.BETTING_WINDOW_TICKS;
        }
        sync();
        long seconds = Math.max(1L, (closesAt - now + 19L) / 20L);
        com.micatechnologies.minecraft.lbe.network.LbeNetwork.CHANNEL.sendTo(
            com.micatechnologies.minecraft.lbe.network.PacketCasinoResult.notice(game,
                balanceOf(player), LbeEconomy.format(bet) + " on " + optionName(game, option)
                    + ". " + (game == CasinoGame.PIG_RACE ? "Race" : "Spin") + " in " + seconds
                    + "s."),
            player);
    }

    @Override
    protected double atRisk(UUID player) {
        double total = 0.0;
        for (Bet bet : bets) {
            total += bet.player.equals(player) ? bet.wager.amount() : 0.0;
        }
        return total;
    }

    private static String noun(CasinoGame game) {
        return game == CasinoGame.PIG_RACE ? "race" : "wheel";
    }

    private static String optionName(CasinoGame game, int option) {
        if (game == CasinoGame.PIG_RACE) {
            PigRace.Pig pig = PigRace.Pig.byCode(option);
            return pig == null ? "?" : pig.displayName();
        }
        BigWheel.Segment segment = BigWheel.Segment.bettable(option);
        return segment == null ? "?" : segment.label();
    }

    // ---------------------------------------------------------------------------------------------
    // Running the round
    // ---------------------------------------------------------------------------------------------

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        long now = world.getTotalWorldTime();
        if (phase == Phase.OPEN && now >= closesAt) {
            resolve();
        } else if (phase == Phase.REVEALING && now >= idleAt) {
            phase = Phase.IDLE;
            sync();
        }
    }

    private void resolve() {
        CasinoGame game = game();
        if (game == null) {
            refundAllOpenHands();
            return;
        }
        seed = rounds.nextLong();
        outcome = game == CasinoGame.PIG_RACE ? PigRace.race(rounds).ordinal() : BigWheel.spin(rounds);
        phase = Phase.REVEALING;
        idleAt = world.getTotalWorldTime() + CasinoFanfare.SHARED_REVEAL_TICKS + RESULT_TICKS;
        sync();

        // Settle every bet, grouped by player so each gets one result for the round.
        // staked, returned, failures, best single bet's multiplier
        Map<UUID, double[]> totals = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Bet bet : bets) {
            double perStake = returnFor(game, bet.option);
            double total = round(bet.wager.amount() * perStake);
            boolean ok = total > 0.0 ? bet.wager.payOut(total) : bet.wager.loseToHouse();
            double[] sums = totals.computeIfAbsent(bet.player, id -> new double[4]);
            sums[0] += bet.wager.amount();
            if (ok) {
                sums[1] += total;
                sums[3] = Math.max(sums[3], perStake);
            } else {
                sums[2] += 1;
            }
            names.put(bet.player, bet.name);
        }
        bets.clear();

        int[] reveal = game == CasinoGame.PIG_RACE
            ? new int[] {outcome, (int) seed, (int) (seed >>> 32)} : new int[] {outcome};
        String describe = describe(game);
        // Smallest win first, so the room's display ends on the biggest.
        List<Map.Entry<UUID, double[]>> order = new ArrayList<>(totals.entrySet());
        order.sort((a, b) -> Double.compare(a.getValue()[1], b.getValue()[1]));
        for (Map.Entry<UUID, double[]> entry : order) {
            double staked = entry.getValue()[0];
            double returned = entry.getValue()[1];
            double multiplier = staked > 0.0 ? returned / staked : 0.0;
            EntityPlayerMP player = world.getMinecraftServer() == null ? null
                : world.getMinecraftServer().getPlayerList().getPlayerByUUID(entry.getKey());
            if (player == null) {
                // Not here to be told. The bank has settled or held their bets; the ledger still
                // hears about it.
                CasinoStatsData.get(world).record(entry.getKey(), names.get(entry.getKey()),
                    game.registryName(), staked, returned);
                continue;
            }
            if (entry.getValue()[2] > 0) {
                reject(player, "Part of your bet could not be settled. It is safe — tell an operator.");
            }
            // A big win is judged bet by bet, as a casino would: 15:1 landing is a big win for
            // whoever backed it, even if their other bets on the round lost. Judged on the round
            // as a whole it vanished into them, and never reached the board.
            CasinoFanfare fanfare = CasinoFanfare.of(Math.max(multiplier, entry.getValue()[3]),
                false);
            afterSettle(player, game, multiplier, staked, returned, reveal, describe, fanfare,
                false, CasinoFanfare.SHARED_REVEAL_TICKS, 0.0);
        }
        Lbe.LOGGER.debug("[casino] {} at {} settled {} player(s).", game.displayName(), pos,
            totals.size());
    }

    private double returnFor(CasinoGame game, int option) {
        if (game == CasinoGame.PIG_RACE) {
            PigRace.Pig backed = PigRace.Pig.byCode(option);
            return backed == null ? 0.0 : PigRace.returnFor(backed, PigRace.Pig.values()[outcome]);
        }
        BigWheel.Segment backed = BigWheel.Segment.bettable(option);
        return backed == null ? 0.0 : BigWheel.returnFor(backed, outcome);
    }

    private String describe(CasinoGame game) {
        if (game == CasinoGame.PIG_RACE) {
            return PigRace.Pig.values()[outcome].displayName() + " wins the race!";
        }
        return "The wheel stops on " + BigWheel.segmentAt(outcome).label() + ".";
    }

    /** Refunds every bet waiting on a round that will now never run. */
    @Override
    public void refundAllOpenHands() {
        super.refundAllOpenHands();
        if (!bets.isEmpty()) {
            for (Bet bet : bets) {
                bet.wager.cancel();
            }
            Lbe.LOGGER.info("[casino] Refunded {} bet(s) on a shared round as the machine unloaded.",
                bets.size());
            bets.clear();
        }
        phase = Phase.IDLE;
    }

    private void sync() {
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 2);
    }

    // ---------------------------------------------------------------------------------------------
    // What the room sees
    // ---------------------------------------------------------------------------------------------

    @Override
    protected NBTTagCompound writeDisplay(NBTTagCompound tag) {
        super.writeDisplay(tag);
        tag.setByte("lbePhase", (byte) phase.ordinal());
        long now = world == null ? 0L : world.getTotalWorldTime();
        tag.setInteger("lbeCloseIn", phase == Phase.OPEN ? (int) Math.max(0L, closesAt - now) : 0);
        tag.setInteger("lbeOutcome", outcome);
        tag.setLong("lbeSeed", seed);
        return tag;
    }

    @Override
    protected void readDisplay(NBTTagCompound tag, boolean live) {
        super.readDisplay(tag, live);
        if (!tag.hasKey("lbePhase")) {
            return;
        }
        Phase[] all = Phase.values();
        int p = tag.getByte("lbePhase");
        Phase incoming = p >= 0 && p < all.length ? all[p] : Phase.IDLE;
        // A reveal only animates when watched live, and only from when it began: later updates
        // during the same reveal keep its timing. An open window restarts its countdown from each
        // update, which carries the fresh time left.
        if (!live || world == null) {
            phaseArrivedAt = -1L;
        } else if (incoming != Phase.REVEALING || phase != Phase.REVEALING || phaseArrivedAt < 0L) {
            phaseArrivedAt = world.getTotalWorldTime();
        }
        phase = incoming;
        closeIn = Math.max(0, tag.getInteger("lbeCloseIn"));
        outcome = tag.getInteger("lbeOutcome");
        seed = tag.getLong("lbeSeed");
    }

    /** Client: the round's phase. */
    public Phase phase() {
        return phase;
    }

    /** Client: the last outcome (a wheel position or a pig ordinal), or -1. */
    public int outcome() {
        return outcome;
    }

    public long seed() {
        return seed;
    }

    /** Client: ticks since the current phase arrived, or -1 if it arrived with the chunk. */
    public double ticksInPhase(float partialTicks) {
        return phaseArrivedAt < 0L || world == null ? -1.0
            : world.getTotalWorldTime() - phaseArrivedAt + partialTicks;
    }

    /** Client: seconds left to bet, counting down from what the server last said. */
    public int secondsToClose(float partialTicks) {
        double since = ticksInPhase(partialTicks);
        double left = closeIn - Math.max(0.0, since);
        return (int) Math.max(0.0, Math.ceil(left / 20.0));
    }

    /** The wheel and the race board draw well beyond the cabinet, so the render box does too. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.add(-2, 0, -2), pos.add(3, 4, 3));
    }
}
