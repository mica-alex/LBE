package com.micatechnologies.minecraft.lbe.client.gui;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotPaytable;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSpin;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSymbol;
import com.micatechnologies.minecraft.lbe.network.LbeNetwork;
import com.micatechnologies.minecraft.lbe.network.PacketSlotResult;
import com.micatechnologies.minecraft.lbe.network.PacketSlotSpin;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

/**
 * The slot machine's screen: three reels, a bet, and a lever.
 *
 * <p><b>This screen decides nothing.</b> It sends a bet and animates whatever comes back. The reels
 * it spins during the wait are cosmetic — the real result arrives in a
 * {@link PacketSlotResult} and the animation is steered to land on it. A client that tampers with
 * anything here changes what one person sees and not one cent of what they are paid.
 *
 * <p>Not a {@code GuiContainer}: there is no inventory involved, so there is no container. That is
 * also why {@code BlockSlotMachine#onBlockActivated} must not guard on {@code world.isRemote} — with
 * no container to open server-side, the client is the side that opens this window.
 */
public class GuiSlotMachine extends GuiScreen {

    private static final ResourceLocation SYMBOLS = new ResourceLocation(
        LbeConstants.MOD_NAMESPACE, "textures/gui/slot_symbols.png");

    /** Each symbol is a 32×32 tile, stacked vertically in the sheet in {@link SlotSymbol} order. */
    private static final int TILE = 32;

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 150;

    /** Ticks a reel keeps spinning before it is allowed to stop. Staggered, so they land 1-2-3. */
    private static final int[] REEL_STOP_TICKS = {24, 34, 44};

    private static final int BUTTON_SPIN = 0;
    private static final int BUTTON_BET_DOWN = 1;
    private static final int BUTTON_BET_UP = 2;

    private final BlockPos pos;
    private final Random cosmetic = new Random();

    private double bet;
    private double balance = PacketSlotResult.UNKNOWN_BALANCE;

    /** Null until the server answers; then the reels are steered onto it. */
    @Nullable
    private SlotSpin pending;

    private double lastPayout;
    @Nullable
    private SlotSpin lastResult;

    private boolean spinning;
    private int spinTicks;

    /** What each reel is showing right now. Nonsense while spinning; the answer once stopped. */
    private final SlotSymbol[] shown = {SlotSymbol.CHERRY, SlotSymbol.LEMON, SlotSymbol.BELL};

    private GuiButton spinButton;

    public GuiSlotMachine(BlockPos pos) {
        this.pos = pos;
        this.bet = LbeConfig.minimumBet;
    }

    @Override
    public void initGui() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        buttonList.clear();
        buttonList.add(new GuiButton(BUTTON_BET_DOWN, left + 12, top + PANEL_HEIGHT - 30, 20, 20,
            "-"));
        buttonList.add(new GuiButton(BUTTON_BET_UP, left + 36, top + PANEL_HEIGHT - 30, 20, 20,
            "+"));
        spinButton = new GuiButton(BUTTON_SPIN, left + 66, top + PANEL_HEIGHT - 30, 140, 20,
            "Spin");
        buttonList.add(spinButton);
    }

    /** The screen stays open while the world runs, so a player can watch the machine and the room. */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Server messages
    // ---------------------------------------------------------------------------------------------

    /** A result, or an opening balance, arrived. Called on the client thread. */
    public void accept(PacketSlotResult message) {
        balance = message.balance();
        if (message.spin() == null) {
            return;
        }
        pending = message.spin();
        lastPayout = message.payout();
        if (!spinning) {
            // The server answered before the animation started — land it immediately rather than
            // showing a spin the player never asked to watch.
            settle();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BUTTON_SPIN:
                startSpin();
                break;
            case BUTTON_BET_UP:
                bet = clampBet(nextStepUp(bet));
                break;
            case BUTTON_BET_DOWN:
                bet = clampBet(nextStepDown(bet));
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }

    private void startSpin() {
        if (spinning) {
            return;
        }
        spinning = true;
        spinTicks = 0;
        pending = null;
        lastResult = null;
        lastPayout = 0.0;
        LbeNetwork.CHANNEL.sendToServer(new PacketSlotSpin(pos, bet));
    }

    /** Bet steps that feel like a slot machine: 1, 5, 10, 25, 50, 100, then round hundreds. */
    private static double nextStepUp(double current) {
        double[] steps = {1, 5, 10, 25, 50, 100, 250, 500, 1000};
        for (double step : steps) {
            if (step > current + 1.0e-9) {
                return step;
            }
        }
        return current * 2.0;
    }

    private static double nextStepDown(double current) {
        double[] steps = {1000, 500, 250, 100, 50, 25, 10, 5, 1};
        for (double step : steps) {
            if (step < current - 1.0e-9) {
                return step;
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
        if (!spinning) {
            return;
        }
        spinTicks++;
        for (int reel = 0; reel < shown.length; reel++) {
            if (spinTicks < REEL_STOP_TICKS[reel]) {
                // Free-running: pure decoration, and deliberately not the weighted distribution —
                // a blur has no odds, and drawing from the real table here would invite somebody
                // to read the blur for information it does not carry.
                shown[reel] = SlotSymbol.byIndex(cosmetic.nextInt(SlotSymbol.values().length));
            } else if (pending != null) {
                shown[reel] = pending.reel(reel);
            }
        }
        boolean animationDone = spinTicks >= REEL_STOP_TICKS[REEL_STOP_TICKS.length - 1];
        if (animationDone && pending != null) {
            settle();
        } else if (spinTicks > 200) {
            // The server never answered — a rejected bet, a dropped packet, a disconnect. Stop
            // spinning rather than turning forever; the reason was sent to chat.
            spinning = false;
        }
    }

    private void settle() {
        if (pending == null) {
            return;
        }
        for (int reel = 0; reel < shown.length; reel++) {
            shown[reel] = pending.reel(reel);
        }
        lastResult = pending;
        pending = null;
        spinning = false;
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0100A18);
        drawRect(left, top, left + PANEL_WIDTH, top + 1, 0xFFF0A81E);
        drawRect(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFF0A81E);

        String title = "Lucky Bucks";
        drawCenteredString(fontRenderer, TextFormatting.GOLD + title, width / 2, top + 8, 0xFFFFFF);

        drawReels(left, top + 24);
        drawStatus(left, top);

        if (spinButton != null) {
            spinButton.enabled = !spinning;
            spinButton.displayString = spinning ? "Spinning…" : "Spin " + money(bet);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawReels(int left, int top) {
        int windowWidth = TILE * 3 + 16;
        int windowLeft = left + (PANEL_WIDTH - windowWidth) / 2;

        drawRect(windowLeft - 2, top - 2, windowLeft + windowWidth + 2, top + TILE + 2, 0xFF3A2A18);
        drawRect(windowLeft, top, windowLeft + windowWidth, top + TILE, 0xFF120C08);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(SYMBOLS);
        for (int reel = 0; reel < shown.length; reel++) {
            int x = windowLeft + 4 + reel * (TILE + 4);
            drawModalRectWithCustomSizedTexture(x, top, 0, shown[reel].index() * TILE,
                TILE, TILE, TILE, TILE * SlotSymbol.values().length);
        }
    }

    private void drawStatus(int left, int top) {
        int textTop = top + 24 + TILE + 10;
        String balanceText = balance == PacketSlotResult.UNKNOWN_BALANCE
            ? "Balance: —" : "Balance: " + money(balance);
        drawCenteredString(fontRenderer, balanceText, width / 2, textTop, 0xB0B0C0);

        String message;
        int colour;
        if (spinning) {
            message = "Good luck…";
            colour = 0x909090;
        } else if (lastResult == null) {
            message = String.format(Locale.ROOT, "Three of a kind pays up to %dx",
                SlotSymbol.SEVEN.tripleMultiplier());
            colour = 0x808090;
        } else if (lastResult.isJackpot()) {
            message = "JACKPOT!  " + money(lastPayout);
            colour = 0xFFD54F;
        } else if (lastResult.isWin()) {
            message = "You win " + money(lastPayout) + "  (" + lastResult.multiplier() + "x)";
            colour = 0x7BE86C;
        } else {
            message = "No luck.";
            colour = 0x909090;
        }
        drawCenteredString(fontRenderer, message, width / 2, textTop + 12, colour);

        // The honest number. A machine that hides its edge is a machine that has something to hide,
        // and a player who can see it is a player who is choosing rather than being taken.
        String rtp = String.format(Locale.ROOT, "Returns %.1f%% over time",
            SlotPaytable.returnToPlayer() * 100.0);
        drawCenteredString(fontRenderer, rtp, width / 2, top + PANEL_HEIGHT - 44, 0x606070);
    }

    private String money(double amount) {
        return String.format(Locale.ROOT, "$%.2f", amount);
    }
}
