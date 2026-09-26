package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.baccarat.BaccaratGame;
import com.micatechnologies.minecraft.lbe.casino.blackjack.BlackjackGame;
import com.micatechnologies.minecraft.lbe.casino.blackjack.BlackjackHand;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.craps.CrapsGame;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.economy.Wager;
import com.micatechnologies.minecraft.lbe.casino.economy.WagerSet;
import com.micatechnologies.minecraft.lbe.casino.highlow.HighLowGame;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import com.micatechnologies.minecraft.lbe.casino.mines.MinesGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSpin;
import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
import com.micatechnologies.minecraft.lbe.casino.videopoker.VideoPokerGame;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

/**
 * One casino machine of any kind. Holds no money, and the only thing it keeps across a restart is
 * its dyed trim, which is decoration rather than game state.
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

    // ---------------------------------------------------------------------------------------------
    // Display state — what the machine shows the room. Cosmetic, one-way, never saved.
    //
    // Set on the server when a round settles, sent to every client watching the chunk, and drawn by
    // the machine's renderer. Nothing on the server ever reads it back: it is a copy of a result
    // that has already been paid, for people who were not playing. Not written to NBT either — a
    // restarted server has nothing to show until somebody plays, which is the truth.
    // ---------------------------------------------------------------------------------------------

    /** The last settled round's reveal, in the same encoding the player's screen was sent. */
    private int[] display = new int[0];

    /** The last round's {@link CasinoFanfare}, or -1 before anyone has played. */
    private int displayFanfare = -1;

    /** How long the last round animates before its result shows, in ticks. */
    private int displayRevealTicks;

    /** What the last round paid, formatted for display, or empty when there is nothing to show. */
    private String displayPayout = "";

    /** Client only: local world time the last round arrived, or -1 when it arrived pre-settled. */
    private long displayArrivedAt = -1L;

    /**
     * Counts settled rounds, so a client can tell a new round from any other update. Without it,
     * dyeing a machine (or anything else that re-sends its state) replayed the last round's reveal.
     */
    private int displayRound;

    /**
     * The neon trim's dye colour ({@code EnumDyeColor} metadata), or -1 for none.
     *
     * <p>The one thing about a machine that <b>is</b> saved: it is decoration somebody chose, not
     * game state, so there is nothing to reconcile against a wager after a restart.
     */
    private int trim = -1;

    /** Server only: the redstone level a revealed win gives off, and when it stops. Not saved. */
    private int signalLevel;
    private long signalUntil;

    /**
     * A two-step game mid-deal, with the money already taken.
     *
     * <p>Exactly one of the game fields is set, chosen by {@link #kind}. Two nullable fields rather
     * than a shared interface because the two games have nothing in common beyond "the stake is
     * already down" — inventing a base type for that would be a bigger lie than a discriminator.
     */
    private static final class OpenHand {
        final CasinoGame kind;
        @Nullable final HighLowGame highLow;
        @Nullable final VideoPokerGame videoPoker;
        @Nullable final MinesGame mines;
        @Nullable final BlackjackGame blackjack;
        final Wager wager;
        /** Craps only: a pass or don't-pass round waiting on its point. */
        @Nullable CrapsGame craps;
        /** Blackjack only: every stake in the round, the base bet included. */
        @Nullable final WagerSet stakes;
        final double bet;

        OpenHand(CasinoGame kind, @Nullable HighLowGame highLow,
                 @Nullable VideoPokerGame videoPoker, @Nullable MinesGame mines,
                 Wager wager, double bet) {
            this(kind, highLow, videoPoker, mines, null, wager, null, bet);
        }

        OpenHand(CasinoGame kind, @Nullable HighLowGame highLow,
                 @Nullable VideoPokerGame videoPoker, @Nullable MinesGame mines,
                 @Nullable BlackjackGame blackjack, Wager wager, @Nullable WagerSet stakes,
                 double bet) {
            this.kind = kind;
            this.highLow = highLow;
            this.videoPoker = videoPoker;
            this.mines = mines;
            this.blackjack = blackjack;
            this.wager = wager;
            this.stakes = stakes;
            this.bet = bet;
        }

        /** Refunds everything this hand has staked: one wager, or a blackjack round's whole set. */
        void refund() {
            if (stakes != null) {
                stakes.cancelAll();
            } else {
                wager.cancel();
            }
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
        OpenHand open = openHands.get(player.getUniqueID());
        if (open != null) {
            // A hand left open, by closing the screen or walking off, picks up where it was. The
            // screen opens fresh, so without this its next click would be read as a move in a
            // hand the player cannot see: in blackjack, a hit.
            resendOpenHand(player, open);
            return;
        }
        LbeNetwork.CHANNEL.sendTo(
            PacketCasinoResult.balanceOnly(game, balanceOf(player)), player);
    }

    /** Sends an open hand to the player's screen again, as it was when they left it. */
    private void resendOpenHand(EntityPlayerMP player, OpenHand open) {
        int[] reveal;
        String message;
        switch (open.kind) {
            case HIGH_LOW:
                reveal = new int[] {cardId(open.highLow.base())};
                message = "Your hand is still open: higher or lower than " + open.highLow.base()
                    + "?";
                break;
            case VIDEO_POKER:
                reveal = new int[VideoPokerGame.HAND_SIZE];
                for (int i = 0; i < reveal.length; i++) {
                    reveal[i] = cardId(open.videoPoker.hand().get(i));
                }
                message = "Your hand is still open. Hold what you want, then draw.";
                break;
            case MINES:
                reveal = toIntArray(open.mines.revealed());
                message = String.format(java.util.Locale.ROOT,
                    "Still in play: %.2fx, next tile pays %.2fx", open.mines.currentMultiplier(),
                    open.mines.nextMultiplier());
                break;
            case BLACKJACK:
                reveal = blackjackReveal(open.blackjack, false);
                message = describeTurn(open.blackjack);
                break;
            case CRAPS:
                reveal = revealFor(CasinoGame.CRAPS, open.craps);
                message = open.craps.describe();
                break;
            default:
                return;
        }
        LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(open.kind, balanceOf(player), reveal,
            message), player);
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

        // A two-step game's second half settles a hand that is already paid for, so it must run
        // before any of the bet checks below — there is no new bet to check.
        if (game.takesStakeUpFront() && openHands.containsKey(player.getUniqueID())) {
            continueOpenHand(player, optionA, optionB);
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
        // Not at a shared table: a bet there only joins the round, and placing two or three in a
        // row is how the game is played. The cooldown silently dropped the second of them, while
        // the table's own cap on bets per round already stops a held-down button.
        if (!game.isSharedRound() && !cooldownExpired(player)) {
            // Silent: somebody spamming the button does not need a wall of chat about it, and an
            // attacker learns nothing either way.
            return;
        }
        String refusal = validate(game, optionA, optionB, numbers);
        if (refusal != null) {
            reject(player, refusal);
            return;
        }
        // Exclusion and loss limits, before any money moves.
        String care = CasinoStatsData.get(world).refusal(player.getUniqueID(),
            rounded + atRisk(player.getUniqueID()));
        if (care != null) {
            reject(player, care);
            return;
        }

        Wager wager = LbeEconomy.bank().stake(player, rounded, game.displayName() + " wager");
        if (wager == null) {
            reject(player, LbeEconomy.bank().lastFailure());
            return;
        }
        markPlayed(player);

        // Past this point the money is held and MUST be settled on every path.
        if (game.isSharedRound()) {
            joinRound(player, game, optionA, wager, rounded);
            return;
        }
        if (game.takesStakeUpFront()) {
            deal(player, game, optionA, wager, rounded);
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
        settle(player, game, wager, result, rounded, CasinoFanfare.REVEAL_TICKS);
    }

    /**
     * Settles a finished game and reports it.
     *
     * @param revealTicks how long the player's screen animates before showing this result, which is
     *     how long everything the rest of the floor notices has to wait. See {@link CasinoEffects}.
     */
    private void settle(EntityPlayerMP player, CasinoGame game, Wager wager, GameResult result,
                        double bet, int revealTicks) {
        double spinReturn = round(bet * result.totalReturnMultiplier());
        // The progressive: slot machines only. Three sevens win the pool on top of the spin. The
        // pool is only read here; it is fed and emptied below, once the payout has gone through,
        // so a failed settlement leaves it exactly where it was.
        CasinoStatsData stats = CasinoStatsData.get(world);
        boolean progressive = game == CasinoGame.SLOTS && LbeConfig.progressiveEnabled;
        boolean topPrize = result instanceof SlotSpin && ((SlotSpin) result).isJackpot();
        double bonus = progressive && topPrize ? stats.progressive().pool() : 0.0;
        double totalReturn = spinReturn + bonus;
        boolean settled = totalReturn > 0.0 ? wager.payOut(totalReturn) : wager.loseToHouse();
        if (!settled) {
            // The bank has already logged why and left the hold open, so the money is not lost.
            reject(player, "Your bet could not be settled. It is safe — tell an operator.");
            return;
        }
        if (progressive) {
            stats.contributeProgressive(bet);
            if (bonus > 0.0) {
                stats.collectProgressive();
            }
        }
        boolean nerves = game == CasinoGame.MINES && revealTicks > 0
            && result.totalReturnMultiplier() >= 5.0;
        afterSettle(player, game, result.totalReturnMultiplier(), bet, totalReturn,
            revealFor(game, result), result.describe(), fanfareOf(result), nerves, revealTicks,
            bonus);
    }

    /**
     * Everything that follows a settled round, whatever the game and however many stakes it had:
     * the result to the player, the display for the room, advancements, the ledger, and the effects
     * held for the reveal.
     *
     * @param multiplier what the round returned per unit staked, for the player's screen
     * @param staked everything the player staked in the round
     * @param bonus the progressive's share of {@code totalReturn}, or 0
     */
    protected void afterSettle(EntityPlayerMP player, CasinoGame game, double multiplier,
                             double staked, double totalReturn, int[] reveal, String describe,
                             CasinoFanfare fanfare, boolean nerves, int revealTicks,
                             double bonus) {
        CasinoStatsData stats = CasinoStatsData.get(world);
        LbeNetwork.CHANNEL.sendTo(new PacketCasinoResult(game, multiplier, totalReturn,
            balanceOf(player), reveal, describe), player);

        String payoutText = LbeConfig.showPayouts && fanfare.isHeardByBystanders()
            ? "WIN " + LbeEconomy.format(totalReturn) : "";
        showRound(reveal, fanfare, revealTicks, payoutText);

        // Playing counts at once; nothing about it gives the result away.
        CasinoAdvancements.grant(player, "root", "played");
        CasinoAdvancements.grant(player, "grand_tour", game.registryName());
        // Winning counts at the reveal, with everything else the floor notices.
        java.util.List<String> earned = new java.util.ArrayList<>();
        if (fanfare.isHeardByBystanders()) {
            earned.add("first_win");
        }
        if (fanfare == CasinoFanfare.BIG_WIN || fanfare == CasinoFanfare.JACKPOT) {
            earned.add("big_win");
        }
        if (fanfare == CasinoFanfare.JACKPOT) {
            earned.add("jackpot");
        }
        if (nerves) {
            earned.add("nerves_of_steel");
        }
        ITextComponent announcement = LbeConfig.announceJackpots && fanfare == CasinoFanfare.JACKPOT
            ? announcement(player, game, totalReturn, bonus) : null;
        // The ledger records money that has already moved, so totals are written now. A big win
        // only reaches the public board at the reveal, like everything else the floor notices.
        stats.record(player.getUniqueID(), player.getName(), game.registryName(), staked,
            totalReturn);
        Runnable atReveal = null;
        if (fanfare == CasinoFanfare.BIG_WIN || fanfare == CasinoFanfare.JACKPOT) {
            CasinoLedger.BigWin win = new CasinoLedger.BigWin(player.getName(),
                game.displayName(), totalReturn, System.currentTimeMillis());
            atReveal = () -> stats.bigWin(win);
        }
        CasinoEffects.roundSettled((WorldServer) world, pos, game.isTall(), player, fanfare,
            revealTicks, announcement, earned, atReveal);
    }

    // ---------------------------------------------------------------------------------------------
    // Per-game dispatch
    // ---------------------------------------------------------------------------------------------

    /** Checks a game's options before any money moves. Null when they are fine. */
    @Nullable
    private static String validate(CasinoGame game, int optionA, int optionB, int[] numbers) {
        switch (game) {
            case COIN_FLIP:
                // sideFor tolerates anything, so this is the check that a nonsense option is a
                // refusal rather than a silent "heads".
                return optionA == CoinFlipGame.codeFor(CoinFlipGame.Side.HEADS)
                    || optionA == CoinFlipGame.codeFor(CoinFlipGame.Side.TAILS)
                    ? null : "Call heads or tails.";
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
            case BACCARAT:
                return BaccaratGame.sideFor(optionA) != null ? null
                    : "Back the player, the banker, or a tie.";
            case MINES:
                // Clamped by the game rather than refused, so a nonsense count still gives a board.
                return null;
            case CRAPS:
                return CrapsGame.Bet.byCode(optionA) != null ? null
                    : "Pass line, don't pass, or the field.";
            case BIG_WHEEL:
                return com.micatechnologies.minecraft.lbe.casino.wheel.BigWheel.Segment
                    .bettable(optionA) != null ? null : "Back a segment on the wheel.";
            case PIG_RACE:
                return com.micatechnologies.minecraft.lbe.casino.race.PigRace.Pig
                    .byCode(optionA) != null ? null : "Back one of the pigs.";
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
                return CoinFlipGame.flip(CoinFlipGame.sideFor(optionA), random);
            case WAR:
                return WarGame.play(random);
            case ROULETTE:
                return RouletteGame.spin(betType(optionA), optionB, random);
            case PLINKO:
                return PlinkoGame.drop(PlinkoGame.Risk.values()[optionA], random);
            case KENO:
                return KenoGame.play(toPicks(numbers), random);
            case BACCARAT:
                return BaccaratGame.play(BaccaratGame.sideFor(optionA), random);
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
                return new int[] {CoinFlipGame.codeFor(flip.landed())};
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
            case MINES: {
                MinesGame.Result round = (MinesGame.Result) result;
                // Where the mines were. Only ever sent once the round is over — mid-round this
                // would be the entire game, handed to the client that must not know it.
                return toIntArray(round.mines());
            }
            case VIDEO_POKER: {
                VideoPokerGame.Result hand = (VideoPokerGame.Result) result;
                int[] reveal = new int[VideoPokerGame.HAND_SIZE];
                for (int i = 0; i < reveal.length; i++) {
                    reveal[i] = cardId(hand.finalHand().get(i));
                }
                return reveal;
            }
            case BACCARAT: {
                BaccaratGame.Result coup = (BaccaratGame.Result) result;
                // Both hands, each prefixed by its length so the screen knows where one ends.
                int[] reveal = new int[2 + coup.playerHand().size() + coup.bankerHand().size()];
                int at = 0;
                reveal[at++] = coup.playerHand().size();
                for (Card card : coup.playerHand()) {
                    reveal[at++] = cardId(card);
                }
                reveal[at++] = coup.bankerHand().size();
                for (Card card : coup.bankerHand()) {
                    reveal[at++] = cardId(card);
                }
                return reveal;
            }
            case CRAPS: {
                CrapsGame round = (CrapsGame) result;
                return new int[] {round.die1(), round.die2(), round.point(), round.bet().ordinal()};
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

    /** How much of an event a finished round is. Slots names its own top prize; nothing else does. */
    private static CasinoFanfare fanfareOf(GameResult result) {
        boolean topPrize = result instanceof SlotSpin && ((SlotSpin) result).isJackpot();
        return CasinoFanfare.of(result.totalReturnMultiplier(), topPrize);
    }

    /**
     * What a player already has riding on this machine that has not settled yet, so a loss limit
     * counts it. Only a shared round lets bets pile up before settling.
     */
    protected double atRisk(UUID player) {
        return 0.0;
    }

    /**
     * A bet on a shared round. Only {@link TileEntitySharedTable} takes these; anything else that
     * gets here returns the stake.
     */
    protected void joinRound(EntityPlayerMP player, CasinoGame game, int option, Wager wager,
                             double bet) {
        wager.cancel();
        reject(player, "This machine cannot take that bet.");
    }

    // ---------------------------------------------------------------------------------------------
    // Blackjack
    // ---------------------------------------------------------------------------------------------

    /**
     * One decision on a blackjack hand: hit, stand, double or split.
     *
     * <p>A double or a split stakes one more bet of the original size, through the bank like any
     * other, <b>before</b> the cards move. If the player cannot cover it, the action is refused and
     * the hand carries on exactly as it was.
     */
    private void continueBlackjack(EntityPlayerMP player, OpenHand open, int optionA) {
        BlackjackGame table = open.blackjack;
        BlackjackGame.Action action = BlackjackGame.Action.byCode(optionA);
        if (table == null || open.stakes == null) {
            return;
        }
        if (!table.canDo(action)) {
            openHands.put(player.getUniqueID(), open);
            sendBlackjackState(player, table, action == BlackjackGame.Action.SPLIT
                ? "Only a pair can be split, and only once."
                : "You can only double on your first two cards.");
            return;
        }
        if (action == BlackjackGame.Action.DOUBLE || action == BlackjackGame.Action.SPLIT) {
            String care = CasinoStatsData.get(world).refusal(player.getUniqueID(),
                open.stakes.staked() + open.bet);
            if (care != null) {
                openHands.put(player.getUniqueID(), open);
                sendBlackjackState(player, table, care);
                return;
            }
            Wager extra = LbeEconomy.bank().stake(player, open.bet,
                "Blackjack " + (action == BlackjackGame.Action.DOUBLE ? "double" : "split"));
            if (extra == null) {
                openHands.put(player.getUniqueID(), open);
                sendBlackjackState(player, table, LbeEconomy.bank().lastFailure());
                return;
            }
            // A double rides on the hand being played; a split's new stake on the second hand.
            open.stakes.add(action == BlackjackGame.Action.SPLIT ? 1 : table.activeHand(), extra);
        }
        try {
            table.apply(action, random);
        } catch (RuntimeException e) {
            Lbe.LOGGER.error("[casino] A blackjack hand failed; refunding it.", e);
            open.stakes.cancelAll();
            reject(player, "The table jammed. Your bets have been returned.");
            return;
        }
        if (table.isFinished()) {
            settleBlackjack(player, open);
        } else {
            openHands.put(player.getUniqueID(), open);
            sendBlackjackState(player, table, describeTurn(table));
        }
    }

    /** Settles every stake in a finished blackjack round, each by its own hand. */
    private void settleBlackjack(EntityPlayerMP player, OpenHand open) {
        BlackjackGame table = open.blackjack;
        WagerSet stakes = open.stakes;
        double[] returns = new double[table.hands().size()];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = table.returnFor(i);
        }
        double staked = stakes.staked();
        WagerSet.Outcome outcome = stakes.settle(returns, TileEntityCasinoMachine::round);
        if (outcome.failures() > 0) {
            // Each failed stake is still held by the bank, which has logged why.
            reject(player, "Part of your bet could not be settled. It is safe — tell an operator.");
        }
        double totalReturn = outcome.paid();
        double multiplier = staked > 0.0 ? totalReturn / staked : 0.0;
        afterSettle(player, CasinoGame.BLACKJACK, multiplier, staked, totalReturn,
            blackjackReveal(table, true), describeResult(table), CasinoFanfare.of(multiplier, false),
            false, CasinoFanfare.REVEAL_TICKS, 0.0);
    }

    private void sendBlackjackState(EntityPlayerMP player, BlackjackGame table, String message) {
        LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(CasinoGame.BLACKJACK,
            balanceOf(player), blackjackReveal(table, false), message), player);
    }

    /**
     * A blackjack table as the screen draws it: {@code [dealerCount, dealer..., handCount, active,
     * then per hand: stakes, cardCount, cards...]}. Mid-round only the dealer's upcard is sent —
     * a client that knows the hole card is a client that can play perfectly.
     */
    public static int[] blackjackReveal(BlackjackGame table, boolean showDealer) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int dealerCards = showDealer ? table.dealer().size() : 1;
        out.add(dealerCards);
        for (int i = 0; i < dealerCards; i++) {
            out.add(cardId(table.dealer().get(i)));
        }
        out.add(table.hands().size());
        out.add(table.activeHand());
        for (BlackjackHand hand : table.hands()) {
            out.add(hand.stakes());
            out.add(hand.cards().size());
            for (Card card : hand.cards()) {
                out.add(cardId(card));
            }
        }
        int[] reveal = new int[out.size()];
        for (int i = 0; i < reveal.length; i++) {
            reveal[i] = out.get(i);
        }
        return reveal;
    }

    private static String describeTurn(BlackjackGame table) {
        BlackjackHand hand = table.hands().get(table.activeHand());
        String which = table.hands().size() > 1 ? "Hand " + (table.activeHand() + 1) + ": " : "";
        // Short enough for one line of the screen after a split; the buttons list the choices.
        return which + (hand.isSoft() ? "soft " : "") + hand.total() + " against "
            + table.dealer().get(0) + ". Your move.";
    }

    private static String describeResult(BlackjackGame table) {
        StringBuilder text = new StringBuilder("Dealer ")
            .append(table.dealerTotal()).append(". ");
        for (int i = 0; i < table.hands().size(); i++) {
            BlackjackHand hand = table.hands().get(i);
            double r = table.returnFor(i);
            String verdict = hand.isBlackjack() && r > 1.0 ? "blackjack!"
                : hand.isBust() ? "bust" : r > 1.0 ? "wins" : r == 1.0 ? "pushes" : "loses";
            if (table.hands().size() > 1) {
                // Each hand's total is already drawn beside it; repeating it ran off the screen.
                text.append("Hand ").append(i + 1).append(' ');
            } else {
                text.append(hand.total()).append(' ');
            }
            text.append(verdict).append(". ");
        }
        return text.toString().trim();
    }

    // ---------------------------------------------------------------------------------------------
    // High-low's two steps
    // ---------------------------------------------------------------------------------------------

    /** Step one: the stake is taken and the round begins. Nothing is decided yet. */
    private void deal(EntityPlayerMP player, CasinoGame game, int optionA, Wager wager,
                      double bet) {
        if (game == CasinoGame.CRAPS) {
            CrapsGame round = CrapsGame.start(CrapsGame.Bet.byCode(optionA), random);
            if (round.isFinished()) {
                settle(player, game, wager, round, bet, CasinoFanfare.REVEAL_TICKS);
            } else {
                OpenHand open = new OpenHand(game, null, null, null, wager, bet);
                open.craps = round;
                openHands.put(player.getUniqueID(), open);
                LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(game, balanceOf(player),
                    revealFor(game, round), round.describe()), player);
            }
            return;
        }
        if (game == CasinoGame.BLACKJACK) {
            BlackjackGame table = BlackjackGame.deal(random);
            WagerSet stakes = new WagerSet();
            stakes.add(0, wager);
            OpenHand open = new OpenHand(game, null, null, null, table, wager, stakes, bet);
            if (table.isFinished()) {
                settleBlackjack(player, open);   // a blackjack on the deal, either side
            } else {
                openHands.put(player.getUniqueID(), open);
                sendBlackjackState(player, table, describeTurn(table));
            }
            return;
        }
        if (game == CasinoGame.MINES) {
            MinesGame board = new MinesGame(optionA, random);
            openHands.put(player.getUniqueID(),
                new OpenHand(game, null, null, board, wager, bet));
            LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(game, balanceOf(player),
                new int[0], board.mineCount() + " mines. Turn a tile."), player);
            return;
        }
        if (game == CasinoGame.HIGH_LOW) {
            HighLowGame hand = new HighLowGame(random);
            openHands.put(player.getUniqueID(), new OpenHand(game, hand, null, null, wager, bet));
            Card base = hand.base();
            LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(game, balanceOf(player),
                new int[] {cardId(base)}, "Higher or lower than " + base + "?"), player);
            return;
        }
        VideoPokerGame poker = new VideoPokerGame(random);
        openHands.put(player.getUniqueID(), new OpenHand(game, null, poker, null, wager, bet));
        int[] reveal = new int[VideoPokerGame.HAND_SIZE];
        for (int i = 0; i < reveal.length; i++) {
            reveal[i] = cardId(poker.hand().get(i));
        }
        LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(game, balanceOf(player), reveal,
            "Hold what you want, then draw."), player);
    }

    /**
     * A move in a round that is already paid for.
     *
     * <p>Most games settle here. Mines may not: turning a safe tile leaves the round open, so the
     * hand goes back into the map and the player is sent the new multiplier instead of a result.
     */
    private void continueOpenHand(EntityPlayerMP player, int optionA, int optionB) {
        OpenHand open = openHands.remove(player.getUniqueID());
        if (open == null) {
            return;
        }
        if (open.kind == CasinoGame.MINES) {
            continueMines(player, open, optionA, optionB);
            return;
        }
        if (open.kind == CasinoGame.BLACKJACK) {
            continueBlackjack(player, open, optionA);
            return;
        }
        if (open.kind == CasinoGame.CRAPS && open.craps != null) {
            open.craps.roll(random);
            if (open.craps.isFinished()) {
                settle(player, CasinoGame.CRAPS, open.wager, open.craps, open.bet,
                    CasinoFanfare.REVEAL_TICKS);
            } else {
                openHands.put(player.getUniqueID(), open);
                LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(CasinoGame.CRAPS,
                    balanceOf(player), revealFor(CasinoGame.CRAPS, open.craps),
                    open.craps.describe()), player);
            }
            return;
        }
        GameResult result;
        try {
            result = open.kind == CasinoGame.HIGH_LOW
                ? resolveHighLow(player, open, optionA)
                : open.videoPoker.draw(holdsFrom(optionA));
        } catch (RuntimeException e) {
            Lbe.LOGGER.error("[casino] A {} hand failed to resolve; refunding it.",
                open.kind.displayName(), e);
            open.wager.cancel();
            reject(player, "The machine jammed. Your bet has been returned.");
            return;
        }
        if (result == null) {
            return;   // already refunded and explained
        }
        settle(player, open.kind, open.wager, result, open.bet, CasinoFanfare.REVEAL_TICKS);
    }

    /**
     * One move in a mines round: turn a tile, or stop and take the multiplier.
     *
     * <p>The only place a round can survive a move. Everything else about the money is unchanged —
     * the stake is still held, and it is still settled exactly once, just possibly several packets
     * later than it was taken.
     */
    private void continueMines(EntityPlayerMP player, OpenHand open, int tile, int command) {
        MinesGame board = open.mines;
        MinesGame.Result result;
        try {
            result = command == MINES_CASH_OUT && !board.revealed().isEmpty()
                ? board.cashOut() : board.reveal(tile);
        } catch (RuntimeException e) {
            Lbe.LOGGER.error("[casino] A mines round failed; refunding it.", e);
            open.wager.cancel();
            reject(player, "The machine jammed. Your bet has been returned.");
            return;
        }
        if (result == null) {
            // Still going: put the round back and tell the player what it is worth now.
            openHands.put(player.getUniqueID(), open);
            int[] reveal = toIntArray(board.revealed());
            LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.dealt(CasinoGame.MINES,
                balanceOf(player), reveal, String.format(java.util.Locale.ROOT,
                    "%.2fx — next tile pays %.2fx", board.currentMultiplier(),
                    board.nextMultiplier())), player);
            return;
        }
        // Cashing out animates like any other result; turning a tile is shown the moment it lands.
        int revealTicks = command == MINES_CASH_OUT ? CasinoFanfare.REVEAL_TICKS : 0;
        settle(player, CasinoGame.MINES, open.wager, result, open.bet, revealTicks);
    }

    /**
     * High-low's call, or null when it could not be made and the stake has been returned.
     */
    @Nullable
    private GameResult resolveHighLow(EntityPlayerMP player, OpenHand open, int optionA) {
        HighLowGame.Call call = HighLowGame.callFor(optionA);
        if (!HighLowGame.isCallable(open.highLow.base(), call)) {
            // Should be impossible — such a base card is never dealt — but a hand that cannot be
            // called must give the money back rather than sit there holding it.
            open.wager.cancel();
            reject(player, "That call cannot win here; your bet has been returned.");
            return null;
        }
        return open.highLow.call(call);
    }

    /**
     * Unpacks video poker's holds from the option bitmask.
     *
     * <p>Five bits, one per card. A bitmask rather than five booleans because the play packet
     * already carries an int and a hostile client can do no more damage with a wrong bit than
     * discard a card it meant to keep — its own money, its own mistake.
     */
    /** The option-B command meaning "stop and take the multiplier" in a mines round. */
    public static final int MINES_CASH_OUT = 1;

    private static int[] toIntArray(java.util.Collection<Integer> values) {
        int[] array = new int[values.size()];
        int i = 0;
        for (int value : values) {
            array[i++] = value;
        }
        return array;
    }

    private static boolean[] holdsFrom(int mask) {
        boolean[] holds = new boolean[VideoPokerGame.HAND_SIZE];
        for (int i = 0; i < holds.length; i++) {
            holds[i] = (mask & (1 << i)) != 0;
        }
        return holds;
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
            open.refund();
            Lbe.LOGGER.info("[casino] Refunded an open {} hand for {}, who left.",
                open.kind.displayName(), playerId);
        }
    }

    /** Refunds every hand left open here. For chunk unload and server stop. */
    public void refundAllOpenHands() {
        for (Map.Entry<UUID, OpenHand> entry : openHands.entrySet()) {
            entry.getValue().refund();
            Lbe.LOGGER.info("[casino] Refunded an open {} hand for {} as the machine unloaded.",
                entry.getValue().kind.displayName(), entry.getKey());
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

    protected static double round(double amount) {
        return Math.floor(amount * 100.0) / 100.0;
    }

    protected double balanceOf(EntityPlayerMP player) {
        OptionalDouble balance = LbeEconomy.bank().balance(player);
        // -1 is the wire's "unknown", which a screen draws as a dash rather than as zero. Showing a
        // player $0.00 when the truth is "we could not ask" would read as being robbed.
        return balance.isPresent() ? balance.getAsDouble() : PacketCasinoResult.UNKNOWN_BALANCE;
    }

    protected void reject(EntityPlayerMP player, String message) {
        String text = message == null || message.isEmpty() ? "That bet was refused." : message;
        player.sendMessage(new TextComponentString(text)
            .setStyle(new Style().setColor(TextFormatting.RED)));
        // And to the screen, which is otherwise left spinning on a bet that was never taken until
        // it gives up. Chat keeps the whole line; the screen trims a long one to fit.
        net.minecraft.block.Block block = getBlockType();
        CasinoGame game = block instanceof BlockCasinoMachine
            ? ((BlockCasinoMachine) block).game() : CasinoGame.SLOTS;
        LbeNetwork.CHANNEL.sendTo(PacketCasinoResult.notice(game, balanceOf(player), text),
            player);
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

    private static ITextComponent announcement(EntityPlayerMP player, CasinoGame game,
                                               double payout, double progressive) {
        String text = player.getName() + " won " + LbeEconomy.format(payout) + " at "
            + game.displayName() + "!";
        if (progressive > 0.0) {
            text += " That includes the progressive jackpot of " + LbeEconomy.format(progressive)
                + ".";
        }
        if (LbeConfig.announceJackpotLocation) {
            net.minecraft.util.math.BlockPos at = player.getPosition();
            text += " (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
        }
        return new TextComponentString(text).setStyle(new Style().setColor(TextFormatting.GOLD));
    }

    // ---------------------------------------------------------------------------------------------
    // Display state sync
    // ---------------------------------------------------------------------------------------------

    /** Server: records a settled round for the room to see, and sends it to whoever is watching. */
    private void showRound(int[] reveal, CasinoFanfare fanfare, int revealTicks, String payout) {
        display = reveal.clone();
        displayRound++;
        displayPayout = payout;
        displayFanfare = fanfare.ordinal();
        displayRevealTicks = revealTicks;
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            // Flag 2: send to clients. The block itself has not changed, so no neighbour updates.
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("lbeTrim", (byte) trim);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        trim = tag.hasKey("lbeTrim") ? clampTrim(tag.getByte("lbeTrim")) : -1;
    }

    private static int clampTrim(int value) {
        return value >= 0 && value < 16 ? value : -1;
    }

    /** Server: dyes the machine's neon trim and tells watching clients. */
    public void setTrim(net.minecraft.item.EnumDyeColor colour) {
        trim = colour.getMetadata();
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    /** The trim's colour as RGB, or -1 when the machine has not been dyed. */
    public int trimColour() {
        return trim < 0 ? -1
            : net.minecraft.item.EnumDyeColor.byMetadata(trim).getColorValue();
    }

    protected NBTTagCompound writeDisplay(NBTTagCompound tag) {
        tag.setIntArray("lbeShow", display);
        tag.setByte("lbeFanfare", (byte) displayFanfare);
        tag.setShort("lbeRevealTicks", (short) displayRevealTicks);
        tag.setString("lbePayout", displayPayout);
        tag.setInteger("lbeRound", displayRound);
        tag.setByte("lbeTrim", (byte) trim);
        return tag;
    }

    /**
     * Client: takes a display state from the server.
     *
     * @param live true for a round that has just been played, which animates; false for the state
     *     sent with the chunk, which is shown as already settled
     */
    protected void readDisplay(NBTTagCompound tag, boolean live) {
        if (tag.hasKey("lbeTrim")) {
            trim = clampTrim(tag.getByte("lbeTrim"));
        }
        if (!tag.hasKey("lbeShow")) {
            return;
        }
        int[] incoming = tag.getIntArray("lbeShow");
        // Bounded like everything else a client is handed: a display never needs more than a
        // baccarat coup or a keno draw, and a renderer walking a huge array every frame is a lag
        // machine.
        display = incoming.length <= 64 ? incoming : new int[0];
        displayFanfare = tag.getByte("lbeFanfare");
        displayRevealTicks = Math.max(0, tag.getShort("lbeRevealTicks"));
        String payout = tag.getString("lbePayout");
        displayPayout = payout.length() <= 32 ? payout : "";
        int round = tag.getInteger("lbeRound");
        if (!live) {
            displayArrivedAt = -1L;
        } else if (round != displayRound && world != null) {
            // Only a round that has not been seen animates; anything else keeps its timing.
            displayArrivedAt = world.getTotalWorldTime();
        }
        displayRound = round;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeDisplay(super.getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readDisplay(tag, false);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeDisplay(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readDisplay(packet.getNbtCompound(), true);
    }

    // ---------------------------------------------------------------------------------------------
    // Redstone
    // ---------------------------------------------------------------------------------------------

    /** The redstone level for each fanfare: nothing for a loss or push, 15 for a jackpot. */
    public static int signalFor(CasinoFanfare fanfare) {
        switch (fanfare) {
            case JACKPOT:
                return 15;
            case BIG_WIN:
                return 11;
            case WIN:
                return 6;
            default:
                return 0;
        }
    }

    /** How long a win's signal lasts, in ticks. Long enough to ring a bell; repeaters extend it. */
    private static int signalTicksFor(CasinoFanfare fanfare) {
        return fanfare == CasinoFanfare.JACKPOT ? 100 : fanfare == CasinoFanfare.BIG_WIN ? 40 : 20;
    }

    /** Server: gives off a win's signal, from the moment its reveal ends. */
    void pulseSignal(CasinoFanfare fanfare) {
        int level = signalFor(fanfare);
        if (level <= 0 || world == null || world.isRemote) {
            return;
        }
        int ticks = signalTicksFor(fanfare);
        signalLevel = level;
        signalUntil = world.getTotalWorldTime() + ticks;
        notifySignalChanged();
        world.scheduleUpdate(pos, getBlockType(), ticks + 1);
    }

    /** The redstone level right now: a win's signal while it lasts, otherwise 0. */
    public int signal() {
        return world != null && world.getTotalWorldTime() < signalUntil ? signalLevel : 0;
    }

    /** Tells wiring next to either half that the signal has changed. */
    void notifySignalChanged() {
        if (world == null || world.isRemote) {
            return;
        }
        net.minecraft.block.Block block = getBlockType();
        world.notifyNeighborsOfStateChange(pos, block, false);
        world.updateComparatorOutputLevel(pos, block);
        net.minecraft.util.math.BlockPos up = pos.up();
        if (world.getBlockState(up).getBlock() == block) {
            world.notifyNeighborsOfStateChange(up, block, false);
            world.updateComparatorOutputLevel(up, block);
        }
    }

    /** The last round's reveal, for the renderer. Empty before anyone has played. */
    public int[] display() {
        return display;
    }

    /** The last round's fanfare, or null before anyone has played. */
    @Nullable
    public CasinoFanfare displayFanfare() {
        CasinoFanfare[] all = CasinoFanfare.values();
        return displayFanfare >= 0 && displayFanfare < all.length ? all[displayFanfare] : null;
    }

    /**
     * Client: ticks since the last round arrived, or -1 when it arrived already settled (the chunk
     * loaded after it was played). Compare with {@link #displayRevealTicks()}.
     */
    public double ticksSinceRound(float partialTicks) {
        if (displayArrivedAt < 0L || world == null) {
            return -1.0;
        }
        return world.getTotalWorldTime() - displayArrivedAt + partialTicks;
    }

    /** How long the last round animates before its result shows. */
    public int displayRevealTicks() {
        return displayRevealTicks;
    }

    /** What the last round paid, ready to draw, or empty when there is nothing to show. */
    public String displayPayout() {
        return displayPayout;
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
