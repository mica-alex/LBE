package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoMachine;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntityCasinoMachine;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.coinflip.CoinFlipGame;
import com.micatechnologies.minecraft.lbe.casino.mines.MinesGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.roulette.RouletteGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSymbol;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * Draws what a casino machine shows the room: the last round played on it, animated for anyone
 * watching, and a lamp over the machine when that round was a win.
 *
 * <p>The cabinet itself is the block model, in the chunk mesh where it costs nothing. This adds only
 * what changes: the reels on a slot machine's screen, the wheel on a roulette table, the cards on a
 * card table. It draws from the tile entity's <b>display state</b>, a copy of a round that has
 * already been paid, sent to every client watching the chunk. Nothing drawn here is a claim about
 * money, and nothing it does can change any.
 *
 * <p>Timing matches the player's own screen: a round animates for its reveal time and then shows
 * its result, so someone watching over a player's shoulder sees the reels stop when they do.
 *
 * <h2>Coordinates</h2>
 *
 * <p>Everything is drawn on a flat "screen" in units of 1/64 of a block, with x to the viewer's
 * right and y down, the same way a GUI is drawn. A tall cabinet's screen is the front of its upper
 * half; a table's is its top, the right way up for someone standing at the front.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityCasinoMachineRenderer extends TileEntitySpecialRenderer<TileEntityCasinoMachine> {

    private static final ResourceLocation SYMBOLS = new ResourceLocation(
        LbeConstants.MOD_NAMESPACE, "textures/gui/slot_symbols.png");

    private static final ResourceLocation GLINT = new ResourceLocation(
        LbeConstants.MOD_NAMESPACE, "textures/blocks/glint.png");

    /** One screen unit, in blocks. */
    private static final float UNIT = 1.0F / 64.0F;

    /** Beyond this, a screen is a few pixels and not worth the draw calls. */
    private static final double MAX_DISTANCE_SQ = 32.0D * 32.0D;

    /** How long a finished round stays on show before the machine goes back to attracting players. */
    private static final double ATTRACT_AFTER_TICKS = 20.0D * 30.0D;

    /** How long a win's payout floats over the machine. */
    private static final double PAYOUT_TICKS = 50.0D;

    /** Layers, toward the viewer, in screen units. Enough apart not to z-fight at a distance. */
    private static final float Z_BACK = 0.0F;
    private static final float Z_MID = 0.4F;
    private static final float Z_FRONT = 0.8F;

    @Override
    public void render(TileEntityCasinoMachine tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        if (world == null || x * x + y * y + z * z > MAX_DISTANCE_SQ) {
            return;
        }
        IBlockState state = world.getBlockState(tile.getPos());
        Block block = state.getBlock();
        if (!(block instanceof BlockCasinoMachine) || state.getValue(BlockCasinoMachine.HALF)) {
            return;
        }
        CasinoGame game = ((BlockCasinoMachine) block).game();
        EnumFacing facing = state.getValue(BlockCasinoMachine.FACING);
        Round round = new Round(tile, world.getTotalWorldTime() + partialTicks, partialTicks);

        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        // Turns local +Z to face the side the player stands at.
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);

        GlStateManager.pushMatrix();
        if (game.isTall()) {
            // The screen recess in the upper half: its face is SCREEN_Z = 4/16 behind the front
            // edge, and it runs from the bottom of the upper block to the marquee at 12/16. Both
            // numbers come from tools/gen_casino_models.py.
            GlStateManager.translate(0.0D, 1.0D + 6.0D / 16.0D, 4.0D / 16.0D + 0.004D);
        } else {
            // The table top, laid flat, with the top of the screen at the far side.
            GlStateManager.translate(0.0D, 0.875D + 0.004D, 0.0D);
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
        }
        GlStateManager.scale(UNIT, -UNIT, UNIT);
        beginScreen();
        drawTrim(game, tile.trimColour(), round.time);
        drawGame(game, round);
        endScreen();
        GlStateManager.popMatrix();

        drawLamp(game, round);

        GlStateManager.popMatrix();
        drawPayout(game, round, tile.displayPayout(), x, y, z);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    // ---------------------------------------------------------------------------------------------
    // The round being shown
    // ---------------------------------------------------------------------------------------------

    /** The display state, read once per frame, with the timing worked out. */
    private static final class Round {
        final int[] show;
        @Nullable final CasinoFanfare fanfare;
        /** Ticks into the reveal; meaningless unless {@link #revealing}. */
        final double tick;
        final int revealTicks;
        /** True while the round is still animating toward its result. */
        final boolean revealing;
        /** Ticks since the result showed, or -1 if it is not known (arrived already settled). */
        final double sinceResult;
        /** World time plus partial ticks, for idle motion. */
        final double time;
        /**
         * True when there is no recent round to show: nothing played yet, the chunk loaded after the
         * last one, or it was long enough ago. The machine then runs its attract loop instead.
         */
        final boolean idle;

        Round(TileEntityCasinoMachine tile, double time, float partialTicks) {
            this.show = tile.display();
            this.fanfare = tile.displayFanfare();
            this.revealTicks = tile.displayRevealTicks();
            this.time = time;
            double since = tile.ticksSinceRound(partialTicks);
            this.tick = since;
            this.revealing = since >= 0.0 && since < revealTicks;
            this.sinceResult = since < 0.0 ? -1.0 : since - revealTicks;
            this.idle = fanfare == null || since < 0.0 || sinceResult > ATTRACT_AFTER_TICKS;
        }

        /** Whether there is a round to show; false means the attract loop. */
        boolean played() {
            return !idle;
        }

        /** Whether the result is on show: something has been played and it is not still spinning. */
        boolean settled() {
            return played() && !revealing;
        }

        int value(int index, int fallback) {
            return index >= 0 && index < show.length ? show[index] : fallback;
        }

        /** 0 at the start of the reveal, 1 at the end. */
        double progress() {
            return revealTicks <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, tick / revealTicks));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Per game
    // ---------------------------------------------------------------------------------------------

    private void drawGame(CasinoGame game, Round round) {
        switch (game) {
            case SLOTS:
                drawSlots(round);
                break;
            case COIN_FLIP:
                drawCoin(round);
                break;
            case ROULETTE:
                drawWheel(round);
                break;
            case WAR:
                // Big enough to cover the cards painted on the table's felt.
                drawPair(round, "YOU", "DEALER", false, 11.0F, -3.0F, 18.0F, 26.0F);
                break;
            case HIGH_LOW:
                panel(-24, -20, 24, 20);
                drawPair(round, "SHOWN", "NEXT", true, 10.0F, -5.0F, 16.0F, 22.0F);
                break;
            case BACCARAT:
                drawBaccarat(round);
                break;
            case VIDEO_POKER:
                drawVideoPoker(round);
                break;
            case PLINKO:
                drawPlinko(round);
                break;
            case KENO:
                drawKeno(round);
                break;
            case MINES:
                drawMines(round);
                break;
            default:
                break;
        }
    }

    private void drawSlots(Round round) {
        panel(-22, -12, 22, 12);
        int symbols = SlotSymbol.values().length;
        for (int reel = 0; reel < 3; reel++) {
            int index;
            if (round.revealing && round.tick < CasinoFanfare.REEL_STOP_TICKS[reel]) {
                // A blur, not a draw from the real weights: a spinning reel carries no odds.
                index = (int) (round.time / 1.5D + reel * 2) % symbols;
            } else if (round.played()) {
                index = SlotSymbol.byIndex(round.value(reel, 0)).index();
            } else {
                // Attract: the reels drift, one step at a time, out of step with each other.
                index = (int) (round.time / 16.0D + reel * 3) % symbols;
            }
            float left = -19.0F + reel * 13.0F;
            symbol(index, symbols, left, -6.0F, left + 12.0F, 6.0F);
        }
        if (round.settled() && round.fanfare == CasinoFanfare.JACKPOT
                && ((int) (round.time / 5.0D)) % 2 == 0) {
            text("JACKPOT", 0.0F, 8.0F, 0xFFD040, 0.6F);
        }
    }

    private void drawCoin(Round round) {
        float halfWidth = 9.0F;
        if (round.revealing) {
            // Edge-on and face-on in turn: a coin in the air.
            halfWidth = (float) (9.0D * Math.abs(Math.cos(round.tick * 0.45D)));
        } else if (!round.played()) {
            // Attract: a slow, lazy turn.
            halfWidth = (float) (9.0D * Math.abs(Math.cos(round.time * 0.04D)));
        }
        rect(-halfWidth - 1.0F, -10.0F, halfWidth + 1.0F, 10.0F, 0xFF7A5A10, Z_BACK);
        rect(-halfWidth, -9.0F, halfWidth, 9.0F, 0xFFE0B030, Z_MID);
        if (!round.revealing && halfWidth > 4.0F) {
            String face = !round.played() ? "$"
                : round.value(0, 0) == CoinFlipGame.codeFor(CoinFlipGame.Side.HEADS) ? "H" : "T";
            text(face, 0.0F, -6.0F, 0x5A3A00, 1.6F);
        }
    }

    private void drawWheel(Round round) {
        int pockets = RouletteGame.POCKETS;
        double segment = Math.PI * 2.0D / pockets;
        double rotation;
        if (!round.played()) {
            rotation = round.time * 0.01D;
        } else {
            int pocket = round.value(0, 0);
            // The winning pocket comes to rest under the pointer at the top.
            double rest = -Math.PI / 2.0D - (pocket + 0.5D) * segment;
            double remaining = 1.0D - round.progress();
            rotation = rest - 7.0D * Math.PI * remaining * remaining;
        }
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(4, DefaultVertexFormats.POSITION_COLOR);
        double outer = 22.0D;
        double inner = 13.0D;
        for (int pocket = 0; pocket < pockets; pocket++) {
            int colour = pocketColour(pocket);
            double a0 = rotation + pocket * segment;
            double a1 = a0 + segment;
            vertex(buffer, Math.cos(a0) * inner, Math.sin(a0) * inner, Z_BACK, colour);
            vertex(buffer, Math.cos(a0) * outer, Math.sin(a0) * outer, Z_BACK, colour);
            vertex(buffer, Math.cos(a1) * outer, Math.sin(a1) * outer, Z_BACK, colour);
            vertex(buffer, Math.cos(a0) * inner, Math.sin(a0) * inner, Z_BACK, colour);
            vertex(buffer, Math.cos(a1) * outer, Math.sin(a1) * outer, Z_BACK, colour);
            vertex(buffer, Math.cos(a1) * inner, Math.sin(a1) * inner, Z_BACK, colour);
        }
        // The pointer, just outside the rim at the top.
        int pointer = 0xFFF0F0F0;
        vertex(buffer, -2.5D, -outer - 3.0D, Z_MID, pointer);
        vertex(buffer, 0.0D, -outer + 1.5D, Z_MID, pointer);
        vertex(buffer, 2.5D, -outer - 3.0D, Z_MID, pointer);
        tessellator.draw();
        GlStateManager.enableTexture2D();
        rect(-10.0F, -10.0F, 10.0F, 10.0F, 0xFF3A2410, Z_MID);
        if (round.settled()) {
            int pocket = round.value(0, 0);
            // Black pockets read in white: black ink on the dark hub would vanish.
            int ink = RouletteGame.colourOf(pocket) == RouletteGame.Colour.BLACK ? 0xFFFFFF
                : pocketColour(pocket) & 0xFFFFFF;
            text(String.valueOf(pocket), 0.0F, -6.0F, ink, 1.5F);
        }
    }

    /** Two cards side by side: war's hands, or high-low's base and next. */
    private void drawPair(Round round, String leftLabel, String rightLabel, boolean firstIsShown,
                          float offset, float cy, float width, float height) {
        Card left = round.played() ? cardOf(round.value(0, 0)) : null;
        Card right = round.played() ? cardOf(round.value(1, 0)) : null;
        boolean faceUp = round.settled();
        card(-offset, cy, width, height, faceUp || firstIsShown && round.played() ? left : null);
        card(offset, cy, width, height, faceUp ? right : null);
        float labelY = cy + height / 2.0F + 1.5F;
        text(leftLabel, -offset, labelY, 0xF0C040, 0.5F);
        text(rightLabel, offset, labelY, 0xF0C040, 0.5F);
    }

    private void drawBaccarat(Round round) {
        // Each label sits clear above its own row of cards.
        text("PLAYER", -14.0F, -24.0F, 0xF0C040, 0.5F);
        text("BANKER", -14.0F, 0.0F, 0xF0C040, 0.5F);
        int at = 0;
        for (int row = 0; row < 2; row++) {
            int count = round.played() ? Math.min(3, Math.max(0, round.value(at, 0))) : 2;
            at++;
            float top = row == 0 ? -12.0F : 11.0F;
            for (int i = 0; i < count; i++) {
                Card card = round.settled() ? cardOf(round.value(at + i, 0)) : null;
                card(-2.0F + i * 11.0F, top, 9.0F, 13.0F, card);
            }
            at += round.played() ? count : 0;
        }
    }

    private void drawVideoPoker(Round round) {
        panel(-24, -14, 24, 14);
        for (int i = 0; i < 5; i++) {
            Card card = round.settled() ? cardOf(round.value(i, 0)) : null;
            card(-19.0F + i * 9.5F, 0.0F, 8.5F, 12.0F, card);
        }
    }

    private void drawPlinko(Round round) {
        panel(-20, -20, 20, 20);
        int rows = PlinkoGame.ROWS;
        float spacing = 4.0F;
        float top = -16.0F;
        for (int row = 0; row < rows; row++) {
            for (int peg = 0; peg <= row; peg++) {
                float px = (peg - row / 2.0F) * spacing;
                float py = top + row * 3.4F;
                rect(px - 0.5F, py - 0.5F, px + 0.5F, py + 0.5F, 0xFF9090A0, Z_MID);
            }
        }
        int landed = round.settled() ? round.value(rows, -1) : -1;
        for (int slot = 0; slot <= rows; slot++) {
            float sx = (slot - rows / 2.0F) * spacing;
            rect(sx - 1.6F, 13.0F, sx + 1.6F, 17.0F,
                slot == landed ? 0xFFF0A81E : 0xFF3A2A50, Z_MID);
        }
        if (round.revealing || !round.played()) {
            // The ball follows the path the server chose, one row at a time. Idle, it follows a
            // made-up path every few seconds, to show what the machine does.
            double along;
            long cycle = 0L;
            if (round.revealing) {
                along = round.progress() * rows;
            } else {
                cycle = (long) (round.time / 80.0D);
                along = (round.time % 80.0D) / 80.0D * rows;
            }
            int row = (int) Math.min(rows, along);
            int rights = 0;
            for (int i = 0; i < row; i++) {
                boolean right = round.revealing ? round.value(i, 0) == 1
                    : ((cycle * 31L + i * 17L) >> 2 & 1L) == 1L;
                rights += right ? 1 : 0;
            }
            float bx = (rights - row / 2.0F) * spacing;
            float by = top + (float) along * 3.4F - 2.0F;
            rect(bx - 1.2F, by - 1.2F, bx + 1.2F, by + 1.2F, 0xFFFFFFFF, Z_FRONT);
        }
    }

    private void drawKeno(Round round) {
        panel(-24, -18, 24, 18);
        text("KENO", 0.0F, -16.0F, 0xF0C040, 0.6F);
        if (!round.played()) {
            if (((int) (round.time / 10.0D)) % 2 == 0) {
                text("PLAY", 0.0F, -3.0F, 0xFFFFFF, 1.2F);
            }
            return;
        }
        int drawn = round.show.length;
        int visible = round.revealing ? (int) (round.progress() * drawn) : drawn;
        for (int i = 0; i < Math.min(visible, 20); i++) {
            float cx = -18.0F + (i % 5) * 9.0F;
            float cy = -9.0F + (i / 5) * 6.5F;
            text(String.valueOf(round.show[i]), cx, cy, 0xFFFFFF, 0.5F);
        }
    }

    private void drawMines(Round round) {
        panel(-23, -16, 23, 16);
        boolean showMines = round.settled();
        int columns = 6;
        for (int tile = 0; tile < MinesGame.GRID_SIZE; tile++) {
            float cx = -17.5F + (tile % columns) * 7.0F;
            float cy = -10.5F + (tile / columns) * 7.0F;
            int colour = 0xFF505060;
            if (showMines) {
                colour = isMine(round, tile) ? 0xFFD03030 : 0xFF2F6A34;
            } else if (!round.played() && tile == (int) (round.time / 6.0D) % MinesGame.GRID_SIZE) {
                colour = 0xFFC08A20;   // attract: a lit tile wanders the board
            }
            rect(cx - 3.0F, cy - 3.0F, cx + 3.0F, cy + 3.0F, colour, Z_MID);
        }
    }

    private static boolean isMine(Round round, int tile) {
        for (int mine : round.show) {
            if (mine == tile) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Neon trim and the floating payout
    // ---------------------------------------------------------------------------------------------

    /**
     * A glowing outline in the machine's dye colour: round a cabinet's front, or a table's top. Only
     * drawn once somebody has dyed the machine; a gentle breathing keeps it reading as neon rather
     * than paint.
     */
    private static void drawTrim(CasinoGame game, int rgb, double time) {
        if (rgb < 0) {
            return;
        }
        float breathe = (float) (0.8D + 0.2D * Math.sin(time * 0.08D));
        int r = (int) (((rgb >> 16) & 0xFF) * breathe);
        int g = (int) (((rgb >> 8) & 0xFF) * breathe);
        int b = (int) ((rgb & 0xFF) * breathe);
        int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
        // Round the screen recess on a cabinet (inside its light posts), round the felt on a table.
        float halfW = game.isTall() ? 23.5F : 31.0F;
        float halfH = game.isTall() ? 23.5F : 31.0F;
        float t = 2.0F;
        rect(-halfW, -halfH, halfW, -halfH + t, argb, Z_BACK);
        rect(-halfW, halfH - t, halfW, halfH, argb, Z_BACK);
        rect(-halfW, -halfH, -halfW + t, halfH, argb, Z_BACK);
        rect(halfW - t, -halfH, halfW, halfH, argb, Z_BACK);
    }

    /**
     * "WIN $94.00", rising and fading over the machine just after a win, turned to face whoever is
     * looking. The text is formatted by the server, which is also what decides whether to send it.
     */
    private void drawPayout(CasinoGame game, Round round, String payout, double x, double y,
                            double z) {
        if (payout.isEmpty() || round.sinceResult < 0.0 || round.sinceResult > PAYOUT_TICKS) {
            return;
        }
        FontRenderer font = getFontRenderer();
        if (font == null) {
            return;
        }
        double progress = round.sinceResult / PAYOUT_TICKS;
        int alpha = (int) (255.0D * (1.0D - progress * progress));
        if (alpha < 8) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + (game.isTall() ? 2.55D : 1.45D) + progress * 0.6D,
            z + 0.5D);
        // Billboarded like a name tag.
        GlStateManager.rotate(-rendererDispatcher.entityYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rendererDispatcher.entityPitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        font.drawString(payout, -font.getStringWidth(payout) / 2, 0, (alpha << 24) | 0xFFD040);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    // ---------------------------------------------------------------------------------------------
    // The win lamp
    // ---------------------------------------------------------------------------------------------

    /**
     * A pulsing light over the machine for a while after a win, bigger and longer for bigger wins.
     *
     * <p>Drawn rather than lit: making the block emit light would mean a relight of the area on every
     * win, which is the most expensive thing a block can ask for. Shown only for rounds seen being
     * played; a chunk that loads after a jackpot does not celebrate it again.
     */
    private void drawLamp(CasinoGame game, Round round) {
        if (round.fanfare == null || !round.fanfare.isHeardByBystanders()
                || round.sinceResult < 0.0) {
            return;
        }
        double duration;
        float size;
        switch (round.fanfare) {
            case JACKPOT:
                duration = 200.0D;
                size = 0.6F;
                break;
            case BIG_WIN:
                duration = 100.0D;
                size = 0.45F;
                break;
            default:
                duration = 50.0D;
                size = 0.32F;
                break;
        }
        if (round.sinceResult > duration) {
            return;
        }
        float fade = (float) (1.0D - round.sinceResult / duration);
        float pulse = (float) (0.55D + 0.45D * Math.sin(round.time * 0.6D));

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, game.isTall() ? 2.25D : 1.15D, 0.0D);
        GlStateManager.rotate((float) (round.time * 4.0D % 360.0D), 0.0F, 1.0F, 0.0F);
        bindTexture(GLINT);
        GlStateManager.disableLighting();
        // Ordinary alpha blending, not additive like the loot box glint: a casino is usually lit,
        // and additive light cannot brighten a bright sky or a lit room, so it simply vanished.
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.color(1.0F, 0.78F, 0.1F, Math.min(1.0F, 1.1F * fade * pulse));
        glintQuad(size);
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        glintQuad(size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void glintQuad(float half) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-half, -half, 0.0D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(half, -half, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(half, half, 0.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(-half, half, 0.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing on the screen
    // ---------------------------------------------------------------------------------------------

    /** Full-bright and unculled: a screen glows, and the y flip reverses every face's winding. */
    private static void beginScreen() {
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void endScreen() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
    }

    /** The dark glass behind a cabinet's display. */
    private static void panel(float x1, float y1, float x2, float y2) {
        rect(x1 - 1.0F, y1 - 1.0F, x2 + 1.0F, y2 + 1.0F, 0xFF3A2A18, Z_BACK);
        rect(x1, y1, x2, y2, 0xFF120C08, Z_BACK + 0.1F);
    }

    /** A card centred on (cx, cy): its face, or its back when {@code card} is null. */
    private void card(float cx, float cy, float width, float height, @Nullable Card card) {
        float x1 = cx - width / 2.0F;
        float y1 = cy - height / 2.0F;
        float x2 = cx + width / 2.0F;
        float y2 = cy + height / 2.0F;
        rect(x1 - 0.5F, y1 - 0.5F, x2 + 0.5F, y2 + 0.5F, 0xFF202020, Z_MID);
        if (card == null) {
            rect(x1, y1, x2, y2, 0xFF2A4AA0, Z_MID + 0.1F);
            rect(x1 + 1.0F, y1 + 1.0F, x2 - 1.0F, y2 - 1.0F, 0xFF3A62C8, Z_MID + 0.2F);
            return;
        }
        rect(x1, y1, x2, y2, 0xFFF4F0E6, Z_MID + 0.1F);
        int ink = card.suit().isRed() ? 0xC02020 : 0x101010;
        String label = card.toString();
        // Fitted to the card: "10" plus a suit is wider than a small card at full size.
        FontRenderer font = getFontRenderer();
        float textWidth = font == null ? 1.0F : Math.max(1.0F, font.getStringWidth(label));
        float scale = Math.min(Math.min(1.0F, (width - 2.0F) / textWidth), height / 16.0F);
        text(label, cx, cy - 4.0F * scale, ink, scale);
    }

    @Nullable
    private static Card cardOf(int id) {
        return TileEntityCasinoMachine.cardFromId(id);
    }

    private void symbol(int index, int count, float x1, float y1, float x2, float y2) {
        bindTexture(SYMBOLS);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        double v0 = index / (double) count;
        double v1 = (index + 1) / (double) count;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x1, y2, Z_MID).tex(0.0D, v1).endVertex();
        buffer.pos(x2, y2, Z_MID).tex(1.0D, v1).endVertex();
        buffer.pos(x2, y1, Z_MID).tex(1.0D, v0).endVertex();
        buffer.pos(x1, y1, Z_MID).tex(0.0D, v0).endVertex();
        tessellator.draw();
    }

    private static void rect(float x1, float y1, float x2, float y2, int argb, float z) {
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vertex(buffer, x1, y2, z, argb);
        vertex(buffer, x2, y2, z, argb);
        vertex(buffer, x2, y1, z, argb);
        vertex(buffer, x1, y1, z, argb);
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private static void vertex(BufferBuilder buffer, double x, double y, float z, int argb) {
        buffer.pos(x, y, z)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .endVertex();
    }

    /** Text centred on {@code cx}, top at {@code y}, drawn in front of everything else. */
    private void text(String text, float cx, float y, int rgb, float scale) {
        FontRenderer font = getFontRenderer();
        if (font == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, y, Z_FRONT);
        GlStateManager.scale(scale, scale, 1.0F);
        font.drawString(text, -font.getStringWidth(text) / 2, 0, rgb);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int pocketColour(int pocket) {
        RouletteGame.Colour colour = RouletteGame.colourOf(pocket);
        return colour == RouletteGame.Colour.RED ? 0xFFC02828
            : colour == RouletteGame.Colour.GREEN ? 0xFF2E9A3A : 0xFF181818;
    }
}
