package com.micatechnologies.minecraft.lbe.client.gui;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntityCasinoMachine;
import com.micatechnologies.minecraft.lbe.casino.baccarat.BaccaratGame;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.highlow.HighLowGame;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import com.micatechnologies.minecraft.lbe.casino.mines.MinesGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotPaytable;
import com.micatechnologies.minecraft.lbe.casino.poker.PokerHand;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSymbol;
import com.micatechnologies.minecraft.lbe.casino.videopoker.VideoPokerGame;
import com.micatechnologies.minecraft.lbe.network.LbeNetwork;
import com.micatechnologies.minecraft.lbe.network.PacketCasinoPlay;
import com.micatechnologies.minecraft.lbe.network.PacketCasinoResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * One screen for every casino game.
 *
 * <p><b>This screen decides nothing.</b> It sends a bet and some choices, and draws whatever comes
 * back. Any animation it plays during the wait is cosmetic — the real outcome arrives in a
 * {@link PacketCasinoResult} and the drawing is steered onto it. A client that tampers with anything
 * here changes what one person sees and not one cent of what they are paid.
 *
 * <p>The games differ only in their <b>options</b> — the buttons above the bet — and their
 * <b>reveal</b>, the little picture of the outcome. Both are switched on {@link CasinoGame} in one
 * place each, so a new game is two short branches rather than a new screen.
 *
 * <p>Not a {@code GuiContainer}: there is no inventory involved, so there is no container. That is
 * also why {@code BlockCasinoMachine#onBlockActivated} must not guard on {@code world.isRemote} —
 * with no container to open server-side, the client is the side that opens this window.
 */
public class GuiCasinoMachine extends GuiScreen {

    private static final ResourceLocation SYMBOLS = new ResourceLocation(
        LbeConstants.MOD_NAMESPACE, "textures/gui/slot_symbols.png");

    private static final int TILE = 32;
    private static final int PANEL_WIDTH = 248;

    /** Space inside the panel edge that nothing is drawn in. */
    private static final int MARGIN = 10;

    /** Height of one row of option buttons, including the gap under it. */
    private static final int OPTION_ROW_HEIGHT = 22;

    /** Room for the title, the two status lines, the return line and the bet row. */
    private static final int CHROME_HEIGHT = 118;

    /** Ticks each slot reel keeps spinning. Staggered, so they land 1-2-3. */
    private static final int[] REEL_STOP_TICKS = {24, 34, 44};

    /** How long any game's reveal animation runs before it must settle. */
    private static final int ANIMATION_TICKS = 44;

    /** After this long with no answer, stop animating: the bet was refused or the packet was lost. */
    private static final int GIVE_UP_TICKS = 200;

    private static final int ID_PLAY = 0;
    private static final int ID_BET_DOWN = 1;
    private static final int ID_BET_UP = 2;
    private static final int ID_OPTION_BASE = 10;

    private final BlockPos pos;
    private final CasinoGame game;
    private final Random cosmetic = new Random();

    private double bet;
    private double balance = PacketCasinoResult.UNKNOWN_BALANCE;
    private double pendingBalance = PacketCasinoResult.UNKNOWN_BALANCE;

    /** The options this game offers, built once in {@link #initGui}. */
    private final List<Option> options = new ArrayList<>();
    private int selectedOption;

    /** Keno only: which numbers are ticked. */
    private final SortedSet<Integer> picks = new TreeSet<>();

    /**
     * Video poker only: which of the five cards are held, one bit each.
     *
     * <p>A bitmask because it travels as the option int the play packet already carries, and
     * because holds are the one choice in the casino that is not "pick one of these" — every other
     * game selects a single option, and this one toggles five independently.
     */
    private int heldMask;

    /** Mines only: how many mines the next round will hide. */
    private int mineCount = 3;

    /** Mines only: which tiles are turned over in the round being played. */
    private final SortedSet<Integer> revealedTiles = new TreeSet<>();

    /** Set while a two-step game has money down and is waiting for the player's second choice. */
    private boolean awaitingChoice;

    @Nullable
    private PacketCasinoResult pending;
    @Nullable
    private PacketCasinoResult settled;

    private boolean animating;
    private int animationTicks;
    private String status = "";

    /**
     * The label of the option actually sent with the last play.
     *
     * <p>Shown back to the player. Not decoration: a screen that displays the outcome but not the
     * choice it sent leaves "I called heads and it said I was wrong" impossible to tell apart from
     * "it sent tails", which is exactly the report this line exists to make answerable.
     */
    private String lastCallLabel = "";

    private GuiButton playButton;

    /**
     * Panel height, worked out in {@link #initGui} rather than fixed.
     *
     * <p>It has to be: roulette offers nine options and keno draws an eighty-cell board, while coin
     * flip offers two and slots none. A single height that suits all of them does not exist — the
     * first version used one and roulette's third row of buttons landed on top of the bet row.
     */
    private int panelHeight = 186;

    /** How tall this game's outcome drawing is, so nothing is laid out on top of it. */
    private int revealHeight = 40;

    /** Width of one option button, sized to the longest label this game has. */
    private int optionWidth = 56;

    /** Options per row, chosen so they fit the panel. */
    private int optionColumns = 4;

    public GuiCasinoMachine(BlockPos pos, CasinoGame game) {
        this.pos = pos;
        this.game = game;
        this.bet = LbeConfig.minimumBet;
    }

    /** One choice a player can make before betting: a coin side, a roulette bet, a risk level. */
    private static final class Option {
        final String label;
        final int valueA;
        final int valueB;

        Option(String label, int valueA, int valueB) {
            this.label = label;
            this.valueA = valueA;
            this.valueB = valueB;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        buttonList.clear();
        options.clear();
        buildOptions();
        if (selectedOption >= options.size()) {
            selectedOption = 0;
        }
        measure();

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight) / 2;
        int rows = optionRows();

        // Options sit directly above the bet row, however many rows they need.
        int optionsBottom = top + panelHeight - 36;
        int optionTop = optionsBottom - rows * OPTION_ROW_HEIGHT;
        for (int i = 0; i < options.size(); i++) {
            int row = i / optionColumns;
            int column = i % optionColumns;
            // Each row is centred on its own, so a short last row does not hug the left edge —
            // which is exactly what high-low's two buttons did.
            int inRow = Math.min(optionColumns, options.size() - row * optionColumns);
            int rowWidth = inRow * optionWidth + (inRow - 1) * 4;
            int rowLeft = left + (PANEL_WIDTH - rowWidth) / 2;
            buttonList.add(new GuiButton(ID_OPTION_BASE + i,
                rowLeft + column * (optionWidth + 4), optionTop + row * OPTION_ROW_HEIGHT,
                optionWidth, 20, options.get(i).label));
        }

        int betTop = top + panelHeight - 28;
        buttonList.add(new GuiButton(ID_BET_DOWN, left + MARGIN, betTop, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_BET_UP, left + MARGIN + 24, betTop, 20, 20, "+"));
        int playLeft = left + MARGIN + 52;
        playButton = new GuiButton(ID_PLAY, playLeft, betTop,
            left + PANEL_WIDTH - MARGIN - playLeft, 20, "Play");
        buttonList.add(playButton);
    }

    /**
     * Works out how wide the option buttons need to be, how many fit a row, and how tall the panel
     * must therefore become.
     *
     * <p>Driven by the actual rendered width of the labels. High-low's buttons read
     * "Higher 11.62x", which is wider than the 56 pixels the first version assumed and simply spilled
     * out of them.
     */
    private void measure() {
        revealHeight = revealHeightFor(game);

        int widest = 0;
        for (Option option : options) {
            widest = Math.max(widest, fontRenderer.getStringWidth(option.label));
        }
        // Label plus breathing room on both sides, never narrower than a "+" button.
        int wanted = Math.max(44, widest + 12);

        int usable = PANEL_WIDTH - MARGIN * 2;
        // As many columns as the widest label allows, capped at four so a row of nine roulette bets
        // does not become one unreadable strip.
        optionColumns = options.isEmpty() ? 1
            : Math.max(1, Math.min(4, (usable + 4) / (wanted + 4)));
        if (optionColumns > options.size()) {
            optionColumns = options.size();
        }
        // Grow the buttons to fill the row once the column count is settled, so two wide buttons
        // look deliberate rather than stranded.
        optionWidth = options.isEmpty() ? wanted
            : Math.min(96, (usable - (optionColumns - 1) * 4) / optionColumns);
        optionWidth = Math.max(optionWidth, Math.min(wanted, usable));

        panelHeight = CHROME_HEIGHT + revealHeight + optionRows() * OPTION_ROW_HEIGHT;
    }

    private int optionRows() {
        if (options.isEmpty()) {
            // High-low grows a row of buttons the moment a card is dealt. Reserving the space up
            // front costs one empty strip and avoids the whole window resizing and re-centring
            // itself under the player's cursor mid-hand.
            return game.takesStakeUpFront() ? 1 : 0;
        }
        return (options.size() + optionColumns - 1) / optionColumns;
    }

    /** How much vertical room this game's outcome drawing needs. */
    private static int revealHeightFor(CasinoGame game) {
        switch (game) {
            case SLOTS:
                return TILE + 8;
            case WAR:
            case HIGH_LOW:
                return 52;
            case BACCARAT:
                // Two rows of up to three cards, with a score beside each.
                return 60;
            case VIDEO_POKER:
                return 40;
            case MINES:
                // Four rows of six, plus a little under them.
                return (MinesGame.GRID_SIZE / 6) * 18 + 4;
            case PLINKO:
                return 46;
            case KENO:
                // Eight rows of cells, plus a little under them.
                return 8 * 11 + 6;
            default:
                return 34;
        }
    }

    /** The choices this game offers. Empty for a game with nothing to choose. */
    private void buildOptions() {
        switch (game) {
            case COIN_FLIP:
                for (CoinFlipGame.Side side : CoinFlipGame.Side.values()) {
                    // Label and value from the same place, so they cannot drift apart.
                    options.add(new Option(side.label(), CoinFlipGame.codeFor(side), 0));
                }
                break;
            case HIGH_LOW:
                // Filled in once a base card has been dealt — until then there is nothing to price.
                if (awaitingChoice && settledBase() != null) {
                    Card base = settledBase();
                    for (HighLowGame.Call call : HighLowGame.Call.values()) {
                        // Label, price and value all from the same place, so they cannot drift.
                        options.add(new Option(
                            call.label() + " " + money(HighLowGame.payoutFor(base, call)) + "x",
                            HighLowGame.codeFor(call), 0));
                    }
                }
                break;
            case ROULETTE:
                options.add(new Option("Red", RouletteGame.BetType.RED.ordinal(), 0));
                options.add(new Option("Black", RouletteGame.BetType.BLACK.ordinal(), 0));
                options.add(new Option("Even", RouletteGame.BetType.EVEN.ordinal(), 0));
                options.add(new Option("Odd", RouletteGame.BetType.ODD.ordinal(), 0));
                options.add(new Option("1-18", RouletteGame.BetType.LOW.ordinal(), 0));
                options.add(new Option("19-36", RouletteGame.BetType.HIGH.ordinal(), 0));
                for (int dozen = 1; dozen <= 3; dozen++) {
                    options.add(new Option("Dz " + dozen,
                        RouletteGame.BetType.DOZEN.ordinal(), dozen));
                }
                break;
            case PLINKO:
                for (PlinkoGame.Risk risk : PlinkoGame.Risk.values()) {
                    options.add(new Option(risk.name().charAt(0)
                        + risk.name().substring(1).toLowerCase(Locale.ROOT),
                        risk.ordinal(), 0));
                }
                break;
            case MINES:
                if (awaitingChoice) {
                    // Mid-round the only decision that is not a tile is "stop".
                    options.add(new Option("Cash out", 0, 0));
                } else {
                    // Before betting, how dangerous the board should be.
                    for (int count : new int[] {1, 3, 5, 10}) {
                        options.add(new Option(count + " mine" + (count == 1 ? "" : "s"),
                            count, 0));
                    }
                }
                break;
            case VIDEO_POKER:
                // One toggle per dealt card, and only once there are cards to hold.
                if (awaitingChoice && settled != null) {
                    for (int i = 0; i < VideoPokerGame.HAND_SIZE; i++) {
                        Card card = TileEntityCasinoMachine.cardFromId(settled.reveal(i, 0));
                        boolean held = (heldMask & (1 << i)) != 0;
                        options.add(new Option((held ? "[" + card + "]" : " " + card + " "), i, 0));
                    }
                }
                break;
            case BACCARAT:
                for (BaccaratGame.Side side : BaccaratGame.Side.values()) {
                    // Label, price and wire value from one place, as everywhere else.
                    options.add(new Option(
                        side.label() + " " + money(side.multiplier()) + "x",
                        BaccaratGame.codeFor(side), 0));
                }
                break;
            case KENO:
                options.add(new Option("Quick pick", 0, 0));
                options.add(new Option("Clear", 1, 0));
                break;
            default:
                break;   // slots and war have nothing to choose
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Server messages
    // ---------------------------------------------------------------------------------------------

    /** A balance, a deal, or a finished game arrived. Called on the client thread. */
    public void accept(PacketCasinoResult message) {
        status = message.message();
        switch (message.stage()) {
            case BALANCE:
                balance = message.balance();
                return;
            case DEALT:
                // Money is down and something has been dealt, but nothing is decided. Show it
                // immediately: the player has to see the card before they can call it.
                balance = message.balance();
                settled = message;
                awaitingChoice = true;
                animating = false;
                selectedOption = 0;
                if (message.game() == CasinoGame.MINES) {
                    // The server sends the whole revealed set each time, so this stays correct even
                    // if a packet is lost — no incremental state to drift.
                    revealedTiles.clear();
                    for (int tile : message.reveal()) {
                        revealedTiles.add(tile);
                    }
                }
                initGui();
                return;
            case SETTLED:
            default:
                pending = message;
                // The balance is deliberately held back until the reveal finishes — applying it now
                // would show the player the outcome up to two seconds before the animation does.
                pendingBalance = message.balance();
                awaitingChoice = false;
                if (!animating) {
                    settle();
                }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    /**
     * Mines' board is clicked directly rather than through buttons.
     *
     * <p>Twenty-four vanilla {@code GuiButton}s would work and would look like a spreadsheet; a grid
     * drawn and hit-tested here is both nicer and less code. Everything else about the move is
     * unchanged — the click sends a tile number and the server decides what is under it.
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (game != CasinoGame.MINES || !awaitingChoice || animating) {
            return;
        }
        int tile = tileAt(mouseX, mouseY);
        if (tile >= 0 && !revealedTiles.contains(tile)) {
            LbeNetwork.CHANNEL.sendToServer(new PacketCasinoPlay(pos, 0.0, tile, 0, null));
        }
    }

    /** Which mines tile is under the cursor, or -1. Mirrors the layout in {@link #drawMines}. */
    private int tileAt(int mouseX, int mouseY) {
        int cell = 18;
        int columns = 6;
        int rows = MinesGame.GRID_SIZE / columns;
        int boardLeft = width / 2 - (columns * cell) / 2;
        int boardTop = (height - panelHeight) / 2 + 24;
        int column = (mouseX - boardLeft) / cell;
        int row = (mouseY - boardTop) / cell;
        if (mouseX < boardLeft || mouseY < boardTop || column < 0 || column >= columns
                || row < 0 || row >= rows) {
            return -1;
        }
        return row * columns + column;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_PLAY) {
            play();
        } else if (button.id == ID_BET_UP) {
            bet = clampBet(nextStep(bet, true));
        } else if (button.id == ID_BET_DOWN) {
            bet = clampBet(nextStep(bet, false));
        } else if (button.id >= ID_OPTION_BASE) {
            chooseOption(button.id - ID_OPTION_BASE);
        } else {
            super.actionPerformed(button);
        }
    }

    private void chooseOption(int index) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        if (game == CasinoGame.MINES && awaitingChoice) {
            // The only option mid-round is Cash out; the tiles are clicked on the board itself.
            LbeNetwork.CHANNEL.sendToServer(new PacketCasinoPlay(pos, 0.0, 0,
                TileEntityCasinoMachine.MINES_CASH_OUT, null));
            awaitingChoice = false;
            animating = true;
            animationTicks = 0;
            return;
        }
        if (game == CasinoGame.MINES) {
            mineCount = options.get(index).valueA;
        }
        if (game == CasinoGame.VIDEO_POKER && awaitingChoice) {
            // Holds toggle independently; there is no "selected" card. The draw happens when the
            // player presses Draw, not when they touch a card, so they can change their mind.
            heldMask ^= 1 << index;
            initGui();
            return;
        }
        if (game == CasinoGame.KENO) {
            // Keno's two buttons are actions, not a selection.
            picks.clear();
            if (index == 0) {
                Random random = new Random();
                while (picks.size() < 5) {
                    picks.add(1 + random.nextInt(KenoGame.BOARD_SIZE));
                }
            }
            return;
        }
        selectedOption = index;
        if (awaitingChoice) {
            // A two-step game: choosing IS the second half of the play, and the money is already
            // down, so send it rather than waiting for the Play button.
            sendPlay(0.0);
            awaitingChoice = false;
            animating = true;
            animationTicks = 0;
        }
    }

    private void play() {
        if (animating) {
            return;
        }
        if (awaitingChoice) {
            // Video poker's second step: the stake is already down, so this draws rather than bets.
            if (game == CasinoGame.VIDEO_POKER) {
                LbeNetwork.CHANNEL.sendToServer(new PacketCasinoPlay(pos, 0.0, heldMask, 0, null));
                awaitingChoice = false;
                animating = true;
                animationTicks = 0;
            }
            return;
        }
        if (game == CasinoGame.KENO && picks.isEmpty()) {
            status = "Pick some numbers first.";
            return;
        }
        pending = null;
        settled = null;
        heldMask = 0;
        revealedTiles.clear();
        animating = true;
        animationTicks = 0;
        status = "";
        sendPlay(bet);
    }

    private void sendPlay(double amount) {
        Option option = options.isEmpty() || selectedOption >= options.size()
            ? null : options.get(selectedOption);
        int optionA = option == null ? 0 : option.valueA;
        int optionB = option == null ? 0 : option.valueB;
        if (game == CasinoGame.MINES) {
            optionA = mineCount;   // the board's danger, not a menu index
        }
        lastCallLabel = option == null ? "" : option.label;
        int[] numbers = new int[picks.size()];
        int i = 0;
        for (int pick : picks) {
            numbers[i++] = pick;
        }
        LbeNetwork.CHANNEL.sendToServer(
            new PacketCasinoPlay(pos, amount, optionA, optionB, numbers));
    }

    /** Bet steps that feel like a casino: 1, 5, 10, 25, 50, 100, then round hundreds. */
    private static double nextStep(double current, boolean up) {
        double[] steps = {1, 5, 10, 25, 50, 100, 250, 500, 1000};
        if (up) {
            for (double step : steps) {
                if (step > current + 1.0e-9) {
                    return step;
                }
            }
            return current * 2.0;
        }
        for (int i = steps.length - 1; i >= 0; i--) {
            if (steps[i] < current - 1.0e-9) {
                return steps[i];
            }
        }
        return LbeConfig.minimumBet;
    }

    private static double clampBet(double value) {
        return Math.max(LbeConfig.minimumBet, Math.min(LbeConfig.maximumBet, value));
    }

    // ---------------------------------------------------------------------------------------------
    // Animation
    // ---------------------------------------------------------------------------------------------

    @Override
    public void updateScreen() {
        if (!animating) {
            return;
        }
        animationTicks++;
        if (animationTicks >= ANIMATION_TICKS && pending != null) {
            settle();
        } else if (animationTicks > GIVE_UP_TICKS) {
            // The server never answered — a refused bet, a dropped packet, a disconnect. Stop
            // rather than animating forever; the reason went to chat.
            animating = false;
            if (pendingBalance != PacketCasinoResult.UNKNOWN_BALANCE) {
                balance = pendingBalance;
                pendingBalance = PacketCasinoResult.UNKNOWN_BALANCE;
            }
        }
    }

    private void settle() {
        if (pending == null) {
            return;
        }
        settled = pending;
        status = pending.message();
        pending = null;
        animating = false;
        // Now, with the reveal. The money moved seconds ago; this is when the player learns of it.
        balance = pendingBalance;
        pendingBalance = PacketCasinoResult.UNKNOWN_BALANCE;
        if (game.takesStakeUpFront()) {
            initGui();   // drop the mid-hand buttons until another hand is dealt
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight) / 2;

        drawRect(left, top, left + PANEL_WIDTH, top + panelHeight, 0xF0100A18);
        drawRect(left, top, left + PANEL_WIDTH, top + 1, 0xFFF0A81E);
        drawRect(left, top + panelHeight - 1, left + PANEL_WIDTH, top + panelHeight, 0xFFF0A81E);

        drawCenteredString(fontRenderer, TextFormatting.GOLD + game.displayName(),
            width / 2, top + 8, 0xFFFFFF);

        drawReveal(left, top + 24);
        drawStatus(left, top);

        if (playButton != null) {
            // Video poker keeps its button live while choosing, because pressing it IS the draw.
            playButton.enabled = !animating
                && (!awaitingChoice || game == CasinoGame.VIDEO_POKER);
            playButton.displayString = animating ? "..."
                : awaitingChoice
                    ? (game == CasinoGame.VIDEO_POKER ? "Draw" : "Choose above")
                    : "Play " + LbeEconomyFormat(bet);
        }
        // Mark the chosen option, since vanilla buttons have no selected state. Colour alone is
        // not enough — a player who cannot tell what is selected cannot tell whether the machine
        // sent what they meant, and a brighter shade of text does not read as "this one".
        for (GuiButton button : buttonList) {
            if (button.id < ID_OPTION_BASE) {
                continue;
            }
            int index = button.id - ID_OPTION_BASE;
            boolean chosen = index == selectedOption && game != CasinoGame.KENO
                && game != CasinoGame.VIDEO_POKER;
            button.packedFGColour = chosen ? 0xFFD54F : 0;
            if (index < options.size()) {
                button.displayString = (chosen ? "> " : "") + options.get(index).label;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The picture of the outcome. One branch per game, and the only game-specific drawing here. */
    private void drawReveal(int left, int top) {
        int centre = width / 2;
        switch (game) {
            case SLOTS:
                drawSlotReels(left, top);
                break;
            case COIN_FLIP:
                drawBigText(centre, top + 12, animating ? "?"
                    : settled == null ? "—" : settled.reveal(0, 0) == 0 ? "HEADS" : "TAILS");
                break;
            case WAR:
            case HIGH_LOW:
                drawCards(centre, top);
                break;
            case ROULETTE:
                drawRoulette(centre, top);
                break;
            case PLINKO:
                drawPlinko(centre, top);
                break;
            case MINES:
                drawMines(top);
                break;
            case VIDEO_POKER:
                drawVideoPoker(centre, top);
                break;
            case BACCARAT:
                drawBaccarat(centre, top);
                break;
            case KENO:
                drawKeno(left, top);
                break;
            default:
                break;
        }
    }

    private void drawSlotReels(int left, int top) {
        int windowWidth = TILE * 3 + 16;
        int windowLeft = left + (PANEL_WIDTH - windowWidth) / 2;
        drawRect(windowLeft - 2, top - 2, windowLeft + windowWidth + 2, top + TILE + 2, 0xFF3A2A18);
        drawRect(windowLeft, top, windowLeft + windowWidth, top + TILE, 0xFF120C08);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(SYMBOLS);
        for (int reel = 0; reel < 3; reel++) {
            SlotSymbol symbol;
            if (animating && animationTicks < REEL_STOP_TICKS[reel]) {
                // Free-running blur: deliberately NOT the weighted distribution, because a blur has
                // no odds and drawing from the real table would invite reading it for information
                // it does not carry.
                symbol = SlotSymbol.byIndex(cosmetic.nextInt(SlotSymbol.values().length));
            } else if (settled != null) {
                symbol = SlotSymbol.byIndex(settled.reveal(reel, 0));
            } else {
                symbol = SlotSymbol.byIndex(reel);
            }
            drawModalRectWithCustomSizedTexture(windowLeft + 4 + reel * (TILE + 4), top,
                0, symbol.index() * TILE, TILE, TILE, TILE, TILE * SlotSymbol.values().length);
        }
    }

    private void drawCards(int centre, int top) {
        String left;
        String right;
        if (settled == null) {
            left = "?";
            right = "?";
        } else if (game == CasinoGame.HIGH_LOW && settled.stage()
                == PacketCasinoResult.Stage.DEALT) {
            left = TileEntityCasinoMachine.cardFromId(settled.reveal(0, 0)).toString();
            right = "?";
        } else {
            left = TileEntityCasinoMachine.cardFromId(settled.reveal(0, 0)).toString();
            right = animating ? "?"
                : TileEntityCasinoMachine.cardFromId(settled.reveal(1, 0)).toString();
        }
        drawBigText(centre - 40, top + 12, left);
        drawBigText(centre + 40, top + 12, right);
        String labels = game == CasinoGame.WAR ? "you            dealer" : "shown           next";
        drawCenteredString(fontRenderer, labels, centre, top + 40, 0x707080);
    }

    private void drawRoulette(int centre, int top) {
        String text;
        int colour = 0xFFFFFF;
        if (settled == null || animating) {
            text = animating ? String.valueOf(cosmetic.nextInt(RouletteGame.POCKETS)) : "—";
            colour = 0x909090;
        } else {
            int pocket = settled.reveal(0, 0);
            text = String.valueOf(pocket);
            RouletteGame.Colour pocketColour = RouletteGame.colourOf(pocket);
            colour = pocketColour == RouletteGame.Colour.RED ? 0xD83A3A
                : pocketColour == RouletteGame.Colour.GREEN ? 0x4FC045 : 0xC0C0C8;
        }
        drawBigText(centre, top + 12, text, colour);
    }

    private void drawPlinko(int centre, int top) {
        // Nine slots along the bottom, the landing one lit.
        int slotWidth = 20;
        int totalWidth = slotWidth * (PlinkoGame.ROWS + 1);
        int slotsLeft = centre - totalWidth / 2;
        int landed = settled == null || animating ? -1 : settled.reveal(PlinkoGame.ROWS, -1);
        PlinkoGame.Risk risk = PlinkoGame.Risk.values()[
            Math.min(selectedOption, PlinkoGame.Risk.values().length - 1)];
        for (int slot = 0; slot <= PlinkoGame.ROWS; slot++) {
            int x = slotsLeft + slot * slotWidth;
            boolean hit = slot == landed;
            drawRect(x + 1, top + 22, x + slotWidth - 1, top + 40, hit ? 0xFFF0A81E : 0xFF241A30);
            String label = trimTrailingZero(risk.multiplierFor(slot));
            drawCenteredString(fontRenderer, label, x + slotWidth / 2, top + 27,
                hit ? 0x201000 : 0x9090A0);
        }
        if (animating) {
            // The ball, falling: cosmetic, and it lands wherever the server said.
            int row = Math.min(PlinkoGame.ROWS, animationTicks * PlinkoGame.ROWS / ANIMATION_TICKS);
            drawCenteredString(fontRenderer, "o", centre, top + row * 2, 0xFFFFFF);
        }
    }

    /**
     * The board: twenty-four tiles, six across.
     *
     * <p>Turned tiles are green, and once the round is over the mines are shown in red — but not one
     * moment before. Mid-round the client is never told where they are, because a client that knows
     * is a client that can play perfectly.
     */
    private void drawMines(int top) {
        int cell = 18;
        int columns = 6;
        int boardLeft = width / 2 - (columns * cell) / 2;
        boolean over = settled != null && !animating
            && settled.stage() == PacketCasinoResult.Stage.SETTLED;
        SortedSet<Integer> mines = new TreeSet<>();
        if (over) {
            for (int value : settled.reveal()) {
                mines.add(value);
            }
        }
        for (int tile = 0; tile < MinesGame.GRID_SIZE; tile++) {
            int x = boardLeft + (tile % columns) * cell;
            int y = top + (tile / columns) * cell;
            boolean turned = revealedTiles.contains(tile);
            boolean mine = over && mines.contains(tile);
            int colour = mine ? 0xFFD83A3A : turned ? 0xFF2E7A3A : 0xFF241A30;
            drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, colour);
            if (mine) {
                drawCenteredString(fontRenderer, "X", x + cell / 2, y + 5, 0x201010);
            } else if (turned) {
                drawCenteredString(fontRenderer, "*", x + cell / 2, y + 5, 0xD0FFD0);
            }
        }
    }

    /** The five cards, plus what the finished hand was worth. */
    private void drawVideoPoker(int centre, int top) {
        if (settled == null) {
            drawCenteredString(fontRenderer, "Deal to begin", centre, top + 14, 0x909090);
            return;
        }
        StringBuilder hand = new StringBuilder();
        for (int i = 0; i < VideoPokerGame.HAND_SIZE; i++) {
            hand.append(TileEntityCasinoMachine.cardFromId(settled.reveal(i, 0))).append("  ");
        }
        drawCenteredString(fontRenderer, hand.toString().trim(), centre, top + 10, 0xE0E0F0);
        if (awaitingChoice) {
            drawCenteredString(fontRenderer, "Click a card to hold it", centre, top + 26,
                0x707080);
        }
    }

    /** Both hands, one per row, with the score that decided the coup. */
    private void drawBaccarat(int centre, int top) {
        if (settled == null || animating) {
            drawCenteredString(fontRenderer, animating ? "Dealing…" : "Place your bet",
                centre, top + 20, 0x909090);
            return;
        }
        int[] reveal = settled.reveal();
        int at = 0;
        int playerCount = reveal.length > 0 ? reveal[at++] : 0;
        StringBuilder playerHand = new StringBuilder();
        for (int i = 0; i < playerCount && at < reveal.length; i++) {
            playerHand.append(TileEntityCasinoMachine.cardFromId(reveal[at++])).append(' ');
        }
        int bankerCount = at < reveal.length ? reveal[at++] : 0;
        StringBuilder bankerHand = new StringBuilder();
        for (int i = 0; i < bankerCount && at < reveal.length; i++) {
            bankerHand.append(TileEntityCasinoMachine.cardFromId(reveal[at++])).append(' ');
        }
        drawCenteredString(fontRenderer, "Player   " + playerHand.toString().trim(),
            centre, top + 8, 0xC0C0D0);
        drawCenteredString(fontRenderer, "Banker   " + bankerHand.toString().trim(),
            centre, top + 26, 0xC0C0D0);
    }

    private void drawKeno(int left, int top) {
        // A 10x8 board. Ticked numbers are gold; drawn ones outlined; hits are both.
        int cell = 14;
        int boardLeft = left + (PANEL_WIDTH - cell * 10) / 2;
        SortedSet<Integer> drawn = new TreeSet<>();
        if (settled != null && !animating) {
            for (int value : settled.reveal()) {
                drawn.add(value);
            }
        }
        for (int number = 1; number <= KenoGame.BOARD_SIZE; number++) {
            int column = (number - 1) % 10;
            int row = (number - 1) / 10;
            int x = boardLeft + column * cell;
            int y = top + row * (cell - 3);
            boolean picked = picks.contains(number);
            boolean hit = drawn.contains(number);
            int background = hit && picked ? 0xFFF0A81E : hit ? 0xFF3F6E8C
                : picked ? 0xFF6A4A16 : 0xFF1A1424;
            drawRect(x, y, x + cell - 1, y + cell - 4, background);
            drawCenteredString(fontRenderer, String.valueOf(number), x + cell / 2 - 1, y + 1,
                hit && picked ? 0x201000 : 0xB0B0C0);
        }
    }

    private void drawStatus(int left, int top) {
        // Directly under the reveal, which is the only thing whose height varies above it.
        int textTop = top + 24 + revealHeight + 4;
        String balanceText = balance == PacketCasinoResult.UNKNOWN_BALANCE
            ? "Balance: —" : "Balance: " + LbeEconomyFormat(balance);
        drawCenteredString(fontRenderer, balanceText, width / 2, textTop, 0xB0B0C0);

        int colour = 0x909090;
        if (settled != null && settled.multiplier() > 1.0) {
            colour = settled.multiplier() >= 50.0 ? 0xFFD54F : 0x7BE86C;
        }
        String line = animating ? "Good luck…" : status;
        if (!lastCallLabel.isEmpty() && !options.isEmpty()) {
            drawCenteredString(fontRenderer, "You called: " + lastCallLabel, width / 2,
                textTop - 11, 0x8080A0);
        }
        // Trimmed rather than allowed to run past the panel edge: a backend message can be longer
        // than anything written here, and one that overflows looks like a rendering fault.
        drawCenteredString(fontRenderer, trimToPanel(line), width / 2, textTop + 11, colour);

        // The honest number. A machine that hides its edge is a machine with something to hide.
        drawCenteredString(fontRenderer, returnLine(), width / 2, textTop + 23, 0x606070);
    }

    /** Shortens a line to something that fits between the panel edges. */
    private String trimToPanel(String text) {
        int usable = PANEL_WIDTH - MARGIN * 2;
        if (text == null || fontRenderer.getStringWidth(text) <= usable) {
            return text == null ? "" : text;
        }
        return fontRenderer.trimStringToWidth(text, usable - fontRenderer.getStringWidth("...."))
            + "...";
    }

    /** What this game returns over time, stated plainly. */
    private String returnLine() {
        double rtp;
        switch (game) {
            case SLOTS:
                rtp = SlotPaytable.returnToPlayer();
                break;
            case COIN_FLIP:
                rtp = com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame
                    .returnToPlayer();
                break;
            case WAR:
                rtp = com.micatechnologies.minecraft.lbe.casino.war.WarGame.returnToPlayer();
                break;
            case HIGH_LOW:
                rtp = HighLowGame.worstReturnToPlayer();
                break;
            case ROULETTE:
                rtp = RouletteGame.returnToPlayer(RouletteGame.BetType.RED);
                break;
            case PLINKO:
                rtp = PlinkoGame.returnToPlayer(PlinkoGame.Risk.values()[
                    Math.min(selectedOption, PlinkoGame.Risk.values().length - 1)]);
                break;
            case KENO:
                rtp = KenoGame.returnToPlayer(Math.max(1, picks.size()));
                break;
            case BACCARAT:
                // Each side has its own return, so show the one being backed.
                return baccaratReturnLine();
            case VIDEO_POKER:
                // The only game here whose return depends on how well it is played, so stating one
                // number would be a lie in either direction.
                return "Returns up to 99.5% — with perfect play";
            case MINES:
                // Exactly the same at every stopping point, which is the nice thing about it.
                rtp = 1.0 - MinesGame.HOUSE_EDGE;
                break;
            default:
                return "";
        }
        return String.format(Locale.ROOT, "Returns %.1f%% over time", rtp * 100.0);
    }

    /**
     * Baccarat's return depends on which side is backed, and the tableau makes a closed form hard,
     * so the well-established figures are named rather than computed.
     */
    private String baccaratReturnLine() {
        BaccaratGame.Side side = BaccaratGame.sideFor(
            options.isEmpty() ? 0 : options.get(Math.min(selectedOption, options.size() - 1)).valueA);
        if (side == null) {
            return "";
        }
        switch (side) {
            case PLAYER:
                return "Returns 98.6% over time";
            case BANKER:
                return "Returns 98.9% over time";
            default:
                return "Returns 85.6% over time";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private Card settledBase() {
        return settled == null ? null
            : TileEntityCasinoMachine.cardFromId(settled.reveal(0, 0));
    }

    private void drawBigText(int centreX, int y, String text) {
        drawBigText(centreX, y, text, 0xFFFFFF);
    }

    private void drawBigText(int centreX, int y, String text, int colour) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centreX, y, 0);
        GlStateManager.scale(2.0F, 2.0F, 1.0F);
        drawCenteredString(fontRenderer, text, 0, 0, colour);
        GlStateManager.popMatrix();
    }

    private static String trimTrailingZero(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    /**
     * Formats an amount with the server's currency symbol.
     *
     * <p>Goes through the economy rather than hard-coding a dollar sign, so a server running a
     * remote economy with its own symbol shows that symbol here too.
     */
    private static String LbeEconomyFormat(double amount) {
        return com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy.format(amount);
    }
}
