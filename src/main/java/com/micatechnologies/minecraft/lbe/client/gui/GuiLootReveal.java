package com.micatechnologies.minecraft.lbe.client.gui;

import com.micatechnologies.minecraft.lbe.catalog.LootCatalog;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;

/**
 * The loot-box opening screen: a strip of items scrolls past a fixed marker, decelerates, and lands
 * on each real reward in turn, which then drops into a collected row along the bottom.
 *
 * <p>A case-opening reel rather than three slot-machine wheels. The reel earns its keep because the
 * anticipation is horizontal and shared — you can see what is <i>coming</i> as it slows, which is the
 * whole feeling a loot box is trying to sell. Three independent wheels give you three small
 * uncertainties instead of one big one.</p>
 *
 * <h2>This screen cannot cost anyone anything</h2>
 *
 * <p>The items were placed in the player's inventory <b>before</b> this screen was told they exist —
 * see {@code PacketRevealLoot}. Nothing here is authoritative, nothing is awaited, and no reward
 * depends on the animation finishing. Escape, a mouse click, or the screen never opening at all are
 * all fine outcomes. That is a deliberate constraint on the design: a reveal animation that gates a
 * reward is a reveal animation that eats rewards on disconnect.</p>
 *
 * <p><b>Client only.</b> Reached exclusively through {@code LbeClientProxy}.</p>
 */
public class GuiLootReveal extends GuiScreen {

    /** Pixels per item cell along the reel. */
    private static final int CELL = 24;

    /** Item icons are 16px; this centres them in a {@link #CELL}-wide cell. */
    private static final int ICON_INSET = (CELL - 16) / 2;

    /** Height of the reel strip. */
    private static final int REEL_HEIGHT = 28;

    /** How many decoy cells precede the winning cell. More cells = longer runway = more tension. */
    private static final int RUNWAY_CELLS = 28;

    /** Ticks the first reel spends spinning. */
    private static final int FIRST_SPIN_TICKS = 44;

    /**
     * Ticks knocked off each subsequent spin, so a five-item box does not outstay its welcome.
     * The floor in {@link #spinTicksFor(int)} keeps the last one from being instantaneous.
     */
    private static final int SPIN_SPEEDUP_PER_ITEM = 7;

    /** Shortest a spin may be, however many items are in the box. */
    private static final int MIN_SPIN_TICKS = 18;

    /** Ticks the landed item pauses centre-stage before dropping into the collected row. */
    private static final int SETTLE_TICKS = 9;

    private final Rarity tier;
    private final List<ItemStack> contents;

    /** The decoy strip for the current item, with the real reward at {@link #RUNWAY_CELLS}. */
    private final List<ItemStack> reel = new ArrayList<>();

    /** Deterministic only within one screen; the decoys are pure decoration. */
    private final Random random = new Random();

    /** Index into {@link #contents} of the item currently spinning. */
    private int currentItem;

    /** Ticks elapsed in the current spin. */
    private int spinTicks;

    /** Ticks elapsed since the current item landed, or -1 while still spinning. */
    private int settleTicks = -1;

    /** Items already revealed, drawn along the bottom. */
    private final List<ItemStack> collected = new ArrayList<>();

    /** Set once every item has been revealed; the screen then waits for the player to dismiss it. */
    private boolean finished;

    /** Cell index whose "tick" sound has already played, so one sound plays per cell crossed. */
    private int lastTickedCell = -1;

    public GuiLootReveal(Rarity tier, List<ItemStack> contents) {
        this.tier = tier;
        this.contents = new ArrayList<>(contents);
        if (this.contents.isEmpty()) {
            // An empty box is a legitimate outcome (an over-aggressive blacklist, a tiny pack). Show
            // the frame and the "nothing" message rather than dividing by zero in the reel maths.
            finished = true;
        }
        else {
            buildReel();
        }
    }

    // Deliberately no buttons and no initGui: the only interactions are "skip" and "close", and both
    // are any-click or any-key, so a player mashing to get back to the game always gets what they
    // expect instead of hunting for a hitbox.

    /**
     * Build the decoy strip for {@link #currentItem}, with the real reward sitting at
     * {@link #RUNWAY_CELLS} and a few cells of overrun after it so the strip never runs dry mid-spin.
     */
    private void buildReel() {
        reel.clear();
        List<ItemStack> decoys = LootCatalog.randomStacks(RUNWAY_CELLS + 8, tier, random);
        for (int i = 0; i < RUNWAY_CELLS; i++) {
            reel.add(decoyAt(decoys, i));
        }
        reel.add(contents.get(currentItem));
        for (int i = 0; i < 8; i++) {
            reel.add(decoyAt(decoys, RUNWAY_CELLS + i));
        }
        spinTicks = 0;
        settleTicks = -1;
        lastTickedCell = -1;
    }

    /**
     * A decoy for one cell, falling back to the real contents if the catalogue has nothing to offer.
     *
     * <p>The fallback matters on a client whose own catalogue failed to build — the reel would
     * otherwise be a row of empty slots, which reads as a bug rather than as a spin.</p>
     */
    private ItemStack decoyAt(List<ItemStack> decoys, int index) {
        if (!decoys.isEmpty()) {
            return decoys.get(index % decoys.size());
        }
        return contents.get(random.nextInt(contents.size()));
    }

    private int spinTicksFor(int itemIndex) {
        return Math.max(MIN_SPIN_TICKS, FIRST_SPIN_TICKS - itemIndex * SPIN_SPEEDUP_PER_ITEM);
    }

    @Override
    public void updateScreen() {
        if (finished) {
            return;
        }
        if (settleTicks >= 0) {
            settleTicks++;
            if (settleTicks >= SETTLE_TICKS) {
                collected.add(contents.get(currentItem));
                currentItem++;
                if (currentItem >= contents.size()) {
                    finished = true;
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
                }
                else {
                    buildReel();
                }
            }
            return;
        }

        spinTicks++;
        if (spinTicks >= spinTicksFor(currentItem)) {
            settleTicks = 0;
            // Pitch rises with the tier, so a legendary lands on a noticeably brighter note.
            playSound(SoundEvents.BLOCK_NOTE_PLING, 0.7F + tier.ordinal() * 0.25F);
        }
    }

    /**
     * How far along the reel we are, {@code 0}–{@code 1}, eased so the strip decelerates.
     *
     * <p>A quintic ease-out: fast early, and a long crawl at the end where the winning cell is
     * already visible and creeping toward the marker. That tail is the entire point — a linear
     * scroll that simply stops has no tension in it at all.</p>
     */
    private float easedProgress(float partialTicks) {
        float total = spinTicksFor(currentItem);
        float elapsed = settleTicks >= 0 ? total : Math.min(total, spinTicks + partialTicks);
        float t = elapsed / total;
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse * inverse * inverse;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int centreX = width / 2;
        int reelY = height / 2 - REEL_HEIGHT / 2 - 10;

        drawTitle(centreX, reelY);

        if (!contents.isEmpty()) {
            drawReel(centreX, reelY, partialTicks);
            drawMarker(centreX, reelY);
        }
        else {
            drawCenteredString(fontRenderer, I18n.format("lbe.reveal.empty"),
                centreX, reelY + REEL_HEIGHT / 2 - 4, 0xFFAAAAAA);
        }

        drawCollected(centreX, reelY + REEL_HEIGHT + 26);
        drawFooter(centreX);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle(int centreX, int reelY) {
        String title = I18n.format("lbe.reveal.title",
            I18n.format("lbe.tier." + tier.id()));
        drawCenteredString(fontRenderer, tier.colourCode() + title, centreX, reelY - 26,
            0xFFFFFFFF);
    }

    /**
     * Draw the scrolling strip, clipped to a window either side of the marker.
     *
     * <p>Clipping is done by drawing only the cells that fall inside the window rather than with a
     * GL scissor box. Scissor coordinates are in real framebuffer pixels, not GUI pixels, so using
     * one means dragging {@code ScaledResolution} and the framebuffer height into the maths and
     * getting it subtly wrong on every GUI scale except the one it was tested at.</p>
     */
    private void drawReel(int centreX, int reelY, float partialTicks) {
        int halfWindow = Math.min(centreX - 12, CELL * 5);
        float scrolled = easedProgress(partialTicks) * RUNWAY_CELLS * CELL;

        // Bevelled well for the strip to sit in.
        drawRect(centreX - halfWindow - 2, reelY - 2, centreX + halfWindow + 2,
            reelY + REEL_HEIGHT + 2, 0xFF1A1A1A);
        drawRect(centreX - halfWindow, reelY, centreX + halfWindow, reelY + REEL_HEIGHT, 0xFF2E2E33);

        int crossedCell = (int) (scrolled / CELL);
        if (crossedCell != lastTickedCell && settleTicks < 0) {
            lastTickedCell = crossedCell;
            // One quiet click per cell crossed: the spin's deceleration becomes audible, which is
            // most of why a real reel feels like it is slowing rather than just moving less.
            playSound(SoundEvents.UI_BUTTON_CLICK, 1.6F);
        }

        RenderHelper.enableGUIStandardItemLighting();
        RenderItem renderer = mc.getRenderItem();
        for (int i = 0; i < reel.size(); i++) {
            int x = centreX - Math.round(scrolled) + i * CELL - CELL / 2;
            if (x < centreX - halfWindow - CELL || x > centreX + halfWindow) {
                continue;
            }
            ItemStack stack = reel.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            renderer.renderItemAndEffectIntoGUI(stack, x + ICON_INSET, reelY + ICON_INSET + 2);
            renderer.renderItemOverlayIntoGUI(fontRenderer, stack, x + ICON_INSET,
                reelY + ICON_INSET + 2, null);
        }
        RenderHelper.disableStandardItemLighting();

        // Fade the strip toward the edges so items enter and leave rather than popping.
        drawGradientRect(centreX - halfWindow, reelY, centreX - halfWindow + 22,
            reelY + REEL_HEIGHT, 0xFF2E2E33, 0x002E2E33);
        drawGradientRect(centreX + halfWindow - 22, reelY, centreX + halfWindow,
            reelY + REEL_HEIGHT, 0x002E2E33, 0xFF2E2E33);
    }

    /** The tier-coloured marker the winning item lands under. */
    private void drawMarker(int centreX, int reelY) {
        int colour = 0xFF000000 | tier.rgb();
        drawRect(centreX - 1, reelY - 6, centreX + 1, reelY + REEL_HEIGHT + 6, colour);
        // Small arrowheads top and bottom, drawn as a stack of shortening bars.
        for (int i = 0; i < 4; i++) {
            drawRect(centreX - 4 + i, reelY - 6 - 4 + i, centreX + 4 - i, reelY - 6 - 3 + i, colour);
            drawRect(centreX - 4 + i, reelY + REEL_HEIGHT + 8 - i,
                centreX + 4 - i, reelY + REEL_HEIGHT + 9 - i, colour);
        }
    }

    /** The row of already-revealed items, with the newest still pulsing. */
    private void drawCollected(int centreX, int y) {
        if (collected.isEmpty()) {
            return;
        }
        int totalWidth = collected.size() * CELL;
        int startX = centreX - totalWidth / 2;

        RenderHelper.enableGUIStandardItemLighting();
        RenderItem renderer = mc.getRenderItem();
        for (int i = 0; i < collected.size(); i++) {
            int x = startX + i * CELL;
            drawRect(x, y, x + CELL - 2, y + CELL - 2, 0xFF23232A);
            ItemStack stack = collected.get(i);
            renderer.renderItemAndEffectIntoGUI(stack, x + ICON_INSET - 1, y + ICON_INSET - 1);
            renderer.renderItemOverlayIntoGUI(fontRenderer, stack, x + ICON_INSET - 1,
                y + ICON_INSET - 1, null);
        }
        RenderHelper.disableStandardItemLighting();
    }

    private void drawFooter(int centreX) {
        String key = finished ? "lbe.reveal.close" : "lbe.reveal.skip";
        drawCenteredString(fontRenderer, I18n.format(key), centreX, height - 28,
            0xFF9A9AA2);
    }

    /**
     * Any click skips ahead: to the end of the current spin, or out of the screen if finished.
     *
     * <p>Skipping is not a concession, it is a requirement. This screen will be seen hundreds of
     * times by anyone who plays with the mod for a weekend, and the hundredth viewing of an
     * animation is an obstacle between the player and their inventory.</p>
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        skipOrClose();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        skipOrClose();
    }

    private void skipOrClose() {
        if (finished) {
            mc.displayGuiScreen(null);
            return;
        }
        // Collect everything still pending in one go rather than fast-forwarding the animation:
        // a player who skips wants the screen gone, not a faster version of the same wait.
        while (currentItem < contents.size()) {
            collected.add(contents.get(currentItem));
            currentItem++;
        }
        finished = true;
    }

    private void playSound(SoundEvent sound, float pitch) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(sound, pitch));
    }

    /** The reveal never pauses the game — it is cosmetic, and the world should keep running. */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
