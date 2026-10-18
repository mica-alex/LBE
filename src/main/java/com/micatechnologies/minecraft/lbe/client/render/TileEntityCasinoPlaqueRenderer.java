package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoLeaderboard;
import com.micatechnologies.minecraft.lbe.casino.decor.TileEntityCasinoPlaque;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws a plaque's message: the first line in gold, the rest in white, each line as large as fits
 * inside the brass frame, the stack centred. Lit by the world, like the plaque it is printed on.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityCasinoPlaqueRenderer
    extends TileEntitySpecialRenderer<TileEntityCasinoPlaque> {

    private static final double MAX_DISTANCE_SQ = 32.0D * 32.0D;

    /** The face is 64 units across at this scale; the frame takes a little off each side. */
    private static final float WIDTH = 52.0F;
    private static final float HEIGHT = 50.0F;
    private static final float MAX_SCALE = 1.8F;
    private static final float GAP = 3.0F;

    private static final int GOLD = 0xF0C040;
    private static final int WHITE = 0xF2F2F6;

    @Override
    public void render(TileEntityCasinoPlaque tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null || x * x + y * y + z * z > MAX_DISTANCE_SQ) {
            return;
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockCasinoLeaderboard)) {
            return;
        }
        FontRenderer font = getFontRenderer();
        if (font == null) {
            return;
        }
        String[] lines = tile.lines();
        float[] scales = new float[lines.length];
        float total = -GAP;
        for (int i = 0; i < lines.length; i++) {
            scales[i] = Math.min(MAX_SCALE, WIDTH / Math.max(1, font.getStringWidth(lines[i])));
            total += 8.0F * scales[i] + GAP;
        }
        // Too tall with every line at its widest: shrink the whole stack evenly.
        float fit = total > HEIGHT ? HEIGHT / total : 1.0F;

        EnumFacing facing = state.getValue(BlockCasinoLeaderboard.FACING);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        // The panel's face: 6/16 behind the block centre, like the leaderboard's.
        GlStateManager.translate(0.0D, 0.0D, -6.0D / 16.0D + 0.004D);
        GlStateManager.scale(fit / 64.0F, -fit / 64.0F, 1.0F / 64.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        float top = -total / 2.0F;
        for (int i = 0; i < lines.length; i++) {
            float scale = scales[i];
            GlStateManager.pushMatrix();
            GlStateManager.translate(-font.getStringWidth(lines[i]) * scale / 2.0F, top, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            font.drawString(lines[i], 0, 0, i == 0 ? GOLD : WHITE);
            GlStateManager.popMatrix();
            top += 8.0F * scale + GAP;
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
