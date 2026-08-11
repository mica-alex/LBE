package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.economy.Wager;
import com.micatechnologies.minecraft.lbe.casino.highlow.HighLowGame;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSpin;
import com.micatechnologies.minecraft.lbe.casino.war.WarGame;
import com.micatechnologies.minecraft.lbe.network.LbeNetwork;
import com.micatechnologies.minecraft.lbe.network.PacketCasinoResult;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * One casino machine of any kind. Holds no money and remembers nothing across a restart.
 *
 * <p><b>Every game is decided here, on the server.</b> The client asks to bet and is told what
 * happened; it is never asked what came up. A reel, a card or a wheel that stops where the client
 * says is a machine that pays what the client says.
 *
 * <p>Almost all of this is game-agnostic. {@link #play} takes a bet and up to a few option numbers,
 * hands them to the right pure game, and settles whatever {@link GameResult} comes back through the
 * one money path. Adding a game means a branch in {@link #resolve} and nothing else here.
 *
 * <h2>The one stateful game</h2>
 *
 * <p>High-low is dealt in two steps — the base card is shown, then the player calls — so a hand in
 * progress is held in {@link #openHands} with its stake. That state is deliberately <b>not</b>
 * persisted: a server restart mid-hand leaves the stake held in SUM's escrow, where an operator can
 * refund it and the orphan sweep will collect it if LBE is ever removed. Writing it to NBT would
 * mean reconciling a saved hand against a wager that may or may not still exist, which is more ways
 * to lose money than it saves.
 */
public class TileEntityCasinoMachine extends TileEntity {

    /** Server-side only: when each player may play again, in world time. */
    private final Map<UUID, Long> nextPlayAllowed = new HashMap<>();

    /** Server-side only: high-low hands dealt and waiting for a call. */
    private final Map<UUID, OpenHand> openHands = new HashMap<>();

    /**
     * Deliberately unseeded. {@link Random}'s no-arg constructor seeds from something a player
     * cannot see or reproduce, whereas anything derived from world time or position would let
     * somebody with the source work out when to play.
     */
    private final Random random = new Random();

    /** A high-low hand mid-deal, with the money already taken. */
    private static final class OpenHand {
        final HighLowGame game;
        final Wager wager;
        final double bet;

        OpenHand(HighLowGame game, Wager wager, double bet) {
            this.game = game;
            this.wager = wager;
            this.bet = bet;
        }
    }

    /**
     * A player right-clicked the cabinet. Runs on <b>both</b> sides.
     *
     * <p>The client opens the screen; the server sends what the screen needs to draw, because a
     * client cannot read a wallet balance it does not hold.
     */
    public void onActivated(EntityPlayer player, CasinoGame game) {
        if (world.isRemote) {
            Lbe.proxy.openCasinoGui(pos, game);
            return;
        }
        if (player instanceof EntityPlayerMP) {
            sendState((EntityPlayerMP) player, game);
        }
    }

    /** Sends the player their balance, with no result attached. */
    public void sendState(EntityPlayerMP player, CasinoGame game) {
        LbeNetwork.CHANNEL.sendTo(
            PacketCasinoResult.balanceOnly(game, balanceOf(player)), player);
    }

    // ---------------------------------------------------------------------------------------------
    // Playing
    // ---------------------------------------------------------------------------------------------

    /**
     * Takes a bet, plays, settles, and tells the player what happened.
     *
     * <p>Server side only. Every path either settles the wager or never opens one.
     *
     * @param optionA first game-specific choice — a coin side, a bet type, a risk level, a high-low
     *     call. Meaning is the game's; validity is checked by the game.
     * @param optionB second choice, where a game needs one (roulette's number).
     * @param numbers keno's picks. Empty for everything else.
     */
    public void play(EntityPlayerMP player, CasinoGame game, double bet, int optionA, int optionB,
                     int[] numbers) {
        if (!LbeConfig.enableCasino) {
            reject(player, "The casino is closed on this server.");
            return;
        }

        // High-low's second step settles a hand that is already paid for, so it must run before any
        // of the bet checks below — there is no new bet to check.
        if (game == CasinoGame.HIGH_LOW && openHands.containsKey(player.getUniqueID())) {
            resolveHighLowCall(player, optionA);
            return;
        }

        if (!LbeEconomy.isOpen()) {
            reject(player, LbeEconomy.bank().unavailableReason());
            return;
        }
        double rounded = Math.floor(bet * 100.0) / 100.0;
        if (!LbeConfig.isBetAllowed(rounded)) {
            reject(player, "Bets here are between " + LbeEconomy.format(LbeConfig.minimumBet)
                + " and " + LbeEconomy.format(LbeConfig.maximumBet) + ".");
            return;
        }
        if (!cooldownExpired(player)) {
            // Silent: somebody spamming the button does not need a wall of chat about it, and an
            // attacker learns nothing either way.
            return;
        }
        String refusal = validate(game, optionA, optionB, numbers);
        if (refusal != null) {
            reject(player, refusal);
            return;
        }

        Wager wager = LbeEconomy.bank().stake(player, rounded, game.displayName() + " wager");
        if (wager == null) {
            reject(player, LbeEconomy.bank().lastFailure());
            return;
        }
        markPlayed(player);

        // Past this point the money is held and MUST be settled on every path.
        if (game == CasinoGame.HIGH_LOW) {
            dealHighLow(player, wager, rounded);
            return;
        }

        GameResult result;
        try {
            result = resolve(game, optionA, optionB, numbers);
        } catch (RuntimeException e) {
            // Nothing here should throw, but a stake already held must not be stranded by a bug in
            // the code that decides what it was for.
            Lbe.LOGGER.error("[casino] {} failed after the bet was taken; refunding it.",
                game.displayName(), e);
            wager.cancel();
            reject(player, "The machine jammed. Your bet has been returned.");
            return;
        }
        settle(player, game, wager, result, rounded);
    }

    /** Settles a finished game and reports it. */
    private void settle(EntityPlayerMP player, CasinoGame game, Wager wager, GameResult result,
                        double bet) {
        double totalReturn = round(bet * result.totalReturnMultiplier());
        boolean settled = totalReturn > 0.0 ? wager.payOut(totalReturn) : wager.loseToHouse();
        if (!settled) {
            // The bank has already logged why and left the hold open, so the money is not lost.
            reject(player, "Your bet could not be settled. It is safe — tell an operator.");
            return;
        }
        LbeNetwork.CHANNEL.sendTo(new PacketCasinoResult(game, result.totalReturnMultiplier(),
            totalReturn, balanceOf(player), revealFor(game, result), result.describe()), player);

        if (LbeConfig.announceJackpots && isJackpot(result)) {
            announce(player, game, totalReturn);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Per-game dispatch
    // ---------------------------------------------------------------------------------------------

    /** Checks a game's options before any money moves. Null when they are fine. */
    @Nullable
    private static String validate(CasinoGame game, int optionA, int optionB, int[] numbers) {
        switch (game) {
            case COIN_FLIP:
                return optionA == 0 || optionA == 1 ? null : "Call heads or tails.";
            case ROULETTE: {
                RouletteGame.BetType type = betType(optionA);
                if (type == null) {
                    return "That is not a bet this table takes.";
                }
                return RouletteGame.isValidValue(type, optionB) ? null
                    : "That number is not valid for that bet.";
            }
            case PLINKO:
                return optionA >= 0 && optionA < PlinkoGame.Risk.values().length ? null
                    : "Pick a risk level.";
            case KENO:
                return KenoGame.isValid(toPicks(numbers)) ? null
                    : "Pick between 1 and " + KenoGame.MAX_PICKS + " numbers from 1 to "
                        + KenoGame.BOARD_SIZE + ".";
            default:
                return null;
        }
    }

    /** Plays a game that resolves in one step. */
    private GameResult resolve(CasinoGame game, int optionA, int optionB, int[] numbers) {
        switch (game) {
            case SLOTS:
                return SlotSpin.roll(random);
            case COIN_FLIP:
                return CoinFlipGame.flip(
                    optionA == 0 ? CoinFlipGame.Side.HEADS : CoinFlipGame.Side.TAILS, random);
            case WAR:
                return WarGame.play(random);
            case ROULETTE:
                return RouletteGame.spin(betType(optionA), optionB, random);
            case PLINKO:
                return PlinkoGame.drop(PlinkoGame.Risk.values()[optionA], random);
            case KENO:
                return KenoGame.play(toPicks(numbers), random);
            default:
                throw new IllegalStateException("No one-step resolution for " + game);
        }
    }

    /**
     * What the client needs to draw the outcome, as plain ints.
     *
     * <p>Deliberately not the result object: the client is being told what to animate, and anything
     * it could use to decide a payout would be something worth forging. Nothing here affects money —
     * the money moved before this was built.
     */
    private static int[] revealFor(CasinoGame game, GameResult result) {
        switch (game) {
            case SLOTS: {
                SlotSpin spin = (SlotSpin) result;
                return new int[] {spin.reel(0).index(), spin.reel(1).index(), spin.reel(2).index()};
            }
            case COIN_FLIP: {
                CoinFlipGame.Result flip = (CoinFlipGame.Result) result;
                return new int[] {flip.landed() == CoinFlipGame.Side.HEADS ? 0 : 1};
            }
            case WAR: {
                WarGame.Result war = (WarGame.Result) result;
                return new int[] {cardId(war.player()), cardId(war.dealer())};
            }
            case HIGH_LOW: {
                HighLowGame.Result hand = (HighLowGame.Result) result;
                return new int[] {cardId(hand.base()), cardId(hand.next())};
            }
            case ROULETTE:
                return new int[] {((RouletteGame.Result) result).pocket()};
            case PLINKO: {
                PlinkoGame.Result drop = (PlinkoGame.Result) result;
                boolean[] path = drop.path();
                int[] reveal = new int[path.length + 1];
                for (int i = 0; i < path.length; i++) {
                    reveal[i] = path[i] ? 1 : 0;
                }
                reveal[path.length] = drop.slot();
                return reveal;
            }
            case KENO: {
                KenoGame.Result ticket = (KenoGame.Result) result;
                int[] reveal = new int[ticket.drawn().size()];
                int i = 0;
                for (int drawn : ticket.drawn()) {
                    reveal[i++] = drawn;
                }
                return reveal;
            }
            default:
                return new int[0];
        }
    }

    /** Whether an outcome is worth telling the whole server about. */
    private static boolean isJackpot(GameResult result) {
        if (result instanceof SlotSpin) {
            return ((SlotSpin) result).isJackpot();
        }
        // Everything else: a payout of 50x or more is rare enough to be an event.
        return result.totalReturnMultiplier() >= 50.0;
    }

    // ---------------------------------------------------------------------------------------------
    // High-low's two steps
    // ---------------------------------------------------------------------------------------------

    /** Step one: the stake is taken and the base card shown. Nothing is decided yet. */
    private void dealHighLow(EntityPlayerMP player, Wager wager, double bet) {
        HighLowGame hand = new HighLowGame(random);
        openHands.put(player.getUniqueID(), new OpenHand(hand, wager, bet));
        Card base = hand.base();
        LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(CasinoGame.HIGH_LOW,
            balanceOf(player), new int[] {cardId(base)},
            "Higher or lower than " + base + "?"), player);
    }

    /** Step two: the call settles the hand that is already paid for. */
    private void resolveHighLowCall(EntityPlayerMP player, int optionA) {
        OpenHand open = openHands.remove(player.getUniqueID());
        if (open == null) {
            return;
        }
        HighLowGame.Call call = optionA == 0 ? HighLowGame.Call.HIGHER : HighLowGame.Call.LOWER;
        if (!HighLowGame.isCallable(open.game.base(), call)) {
            // Should be impossible — such a base card is never dealt — but a hand that cannot be
            // called must give the money back rather than sit there holding it.
            open.wager.cancel();
            reject(player, "That call cannot win here; your bet has been returned.");
            return;
        }
        GameResult result;
        try {
            result = open.game.call(call);
        } catch (RuntimeException e) {
            Lbe.LOGGER.error("[casino] A high-low hand failed to resolve; refunding it.", e);
            open.wager.cancel();
            reject(player, "The machine jammed. Your bet has been returned.");
            return;
        }
        settle(player, CasinoGame.HIGH_LOW, open.wager, result, open.bet);
    }

    /**
     * Refunds any hand this player left mid-deal.
     *
     * <p>Called when they log out or the machine unloads. Without it the stake would sit held until
     * SUM's orphan sweep noticed, which only fires if LBE is removed entirely — so for a player who
     * simply walked away it would sit there forever.
     */
    public void refundOpenHand(UUID playerId) {
        OpenHand open = openHands.remove(playerId);
        if (open != null) {
            open.wager.cancel();
            Lbe.LOGGER.info("[casino] Refunded an abandoned high-low hand for {}.", playerId);
        }
    }

    /** Refunds every hand left open here. For chunk unload and server stop. */
    public void refundAllOpenHands() {
        for (Map.Entry<UUID, OpenHand> entry : openHands.entrySet()) {
            entry.getValue().wager.cancel();
            Lbe.LOGGER.info("[casino] Refunded an open high-low hand for {} as the machine "
                + "unloaded.", entry.getKey());
        }
        openHands.clear();
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            refundAllOpenHands();
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) {
            refundAllOpenHands();
        }
        super.onChunkUnload();
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static RouletteGame.BetType betType(int ordinal) {
        RouletteGame.BetType[] all = RouletteGame.BetType.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    private static SortedSet<Integer> toPicks(int[] numbers) {
        SortedSet<Integer> picks = new TreeSet<>();
        if (numbers != null) {
            for (int number : numbers) {
                picks.add(number);
            }
        }
        return picks;
    }

    /** 0-51, rank-major, so a card survives the wire as one byte. */
    public static int cardId(Card card) {
        return card.rank().ordinal() * 4 + card.suit().ordinal();
    }

    /** The inverse of {@link #cardId}, for the client. */
    public static Card cardFromId(int id) {
        int wrapped = ((id % 52) + 52) % 52;
        return new Card(com.micatechnologies.minecraft.lbe.casino.cards.Rank.values()[wrapped / 4],
            com.micatechnologies.minecraft.lbe.casino.cards.Suit.values()[wrapped % 4]);
    }

    private static double round(double amount) {
        return Math.floor(amount * 100.0) / 100.0;
    }

    private double balanceOf(EntityPlayerMP player) {
        OptionalDouble balance = LbeEconomy.bank().balance(player);
        // -1 is the wire's "unknown", which a screen draws as a dash rather than as zero. Showing a
        // player $0.00 when the truth is "we could not ask" would read as being robbed.
        return balance.isPresent() ? balance.getAsDouble() : PacketCasinoResult.UNKNOWN_BALANCE;
    }

    private void reject(EntityPlayerMP player, String message) {
        String text = message == null || message.isEmpty() ? "That bet was refused." : message;
        player.sendMessage(new TextComponentString(text)
            .setStyle(new Style().setColor(TextFormatting.RED)));
    }

    private boolean cooldownExpired(EntityPlayer player) {
        Long allowedAt = nextPlayAllowed.get(player.getUniqueID());
        return allowedAt == null || world.getTotalWorldTime() >= allowedAt;
    }

    private void markPlayed(EntityPlayer player) {
        long ticks = Math.max(1L, (long) (LbeConfig.spinCooldownSeconds * 20.0));
        nextPlayAllowed.put(player.getUniqueID(), world.getTotalWorldTime() + ticks);
        // The map would otherwise grow one entry per player who ever touched this machine, for the
        // lifetime of the chunk.
        if (nextPlayAllowed.size() > 64) {
            long now = world.getTotalWorldTime();
            nextPlayAllowed.values().removeIf(when -> when < now);
        }
    }

    private void announce(EntityPlayerMP player, CasinoGame game, double payout) {
        String text = player.getName() + " won " + LbeEconomy.format(payout) + " at "
            + game.displayName() + "!";
        player.getServer().getPlayerList().sendMessage(
            new TextComponentString(text).setStyle(new Style().setColor(TextFormatting.GOLD)));
    }

    /**
     * A tall machine occupies two blocks, so the render box has to as well.
     *
     * <p>Without this the upper half is culled the moment the lower one leaves the frustum, and a
     * player standing close enough to use the machine watches its top disappear.
     */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos, pos.add(1, 2, 1));
    }
}
