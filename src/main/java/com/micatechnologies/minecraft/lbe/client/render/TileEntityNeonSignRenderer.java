package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoLeaderboard;
import com.micatechnologies.minecraft.lbe.casino.decor.TileEntityNeonSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws a neon sign's word, glowing: a soft halo in a darker shade behind the bright tube, and
 * every so often a brief flicker, out of step with the signs around it, the way real neon does.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityNeonSignRenderer extends TileEntitySpecialRenderer<TileEntityNeonSign> {

    private static final double MAX_DISTANCE_SQ = 48.0D * 48.0D;

    @Override
    public void render(TileEntityNeonSign tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
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
        EnumFacing facing = state.getValue(BlockCasinoLeaderboard.FACING);
        long time = tile.getWorld().getTotalWorldTime();
        // A flicker: two ticks dark, once in a while, at a time set by the sign's position.
        long phase = time + (tile.getPos().toLong() & 0xFFL) * 7L;
        boolean flicker = phase % 173L < 2L;

        int rgb = tile.rgb();
        int halo = ((rgb >> 1) & 0x7F7F7F);
        int tube = flicker ? halo : brighten(rgb);

        String word = tile.word();
        float scale = Math.min(1.1F, 56.0F / Math.max(1, font.getStringWidth(word)));
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        // The panel's face: 6/16 behind the block centre, like the leaderboard's.
        GlStateManager.translate(0.0D, 0.0D, -6.0D / 16.0D + 0.004D);
        GlStateManager.scale(1.0F / 64.0F, -1.0F / 64.0F, 1.0F / 64.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        float left = -font.getStringWidth(word) * scale / 2.0F;
        float top = -4.0F * scale;
        // The halo: the word drawn a pixel out in each direction, behind the tube.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    draw(font, word, left + dx * 0.8F, top + dy * 0.8F, 0.2F, scale, halo);
                }
            }
        }
        draw(font, word, left, top, 0.4F, scale, tube);

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    private static void draw(FontRenderer font, String text, float x, float y, float z,
                             float scale, int rgb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(scale, scale, 1.0F);
        font.drawString(text, 0, 0, rgb);
        GlStateManager.popMatrix();
    }

    /** Pushes a dye colour toward white, so the tube reads as lit rather than painted. */
    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + 70);
        int b = Math.min(255, (rgb & 0xFF) + 70);
        return (r << 16) | (g << 8) | b;
    }
}
