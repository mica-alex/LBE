package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoLeaderboard;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntityCasinoLeaderboard;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws a leaderboard's two lists on its face: recent big wins, then the biggest winners.
 *
 * <p>Every line arrives from the server already formatted, so this only lays text out. Coordinates
 * are 1/64 of a block with y down, like the machines' screens; the face is the panel's front, 2/16
 * out from the wall behind it.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityCasinoLeaderboardRenderer
        extends TileEntitySpecialRenderer<TileEntityCasinoLeaderboard> {

    private static final float UNIT = 1.0F / 64.0F;
    private static final double MAX_DISTANCE_SQ = 24.0D * 24.0D;
    private static final float TEXT_SCALE = 0.45F;
    private static final float ROW = 4.6F;

    @Override
    public void render(TileEntityCasinoLeaderboard tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null || x * x + y * y + z * z > MAX_DISTANCE_SQ) {
            return;
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockCasinoLeaderboard)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockCasinoLeaderboard.FACING);
        FontRenderer font = getFontRenderer();
        if (font == null) {
            return;
        }

        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);
        // The panel is the 2/16 against the wall, which is 8/16 behind the block centre, so its face
        // is 6/16 behind the centre (toward the wall).
        GlStateManager.translate(0.0D, 0.0D, -6.0D / 16.0D + 0.004D);
        GlStateManager.scale(UNIT, -UNIT, UNIT);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        float top = -28.0F;
        top = section(font, "BIG WINS", tile.bigWins(), "Nobody yet", top);
        section(font, "TOP WINNERS", tile.topWinners(), "Nobody ahead", top + 2.0F);

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    /** A heading and its rows. Returns where the next section starts. */
    private float section(FontRenderer font, String heading, List<String> rows, String empty,
                          float top) {
        text(font, heading, 0.0F, top, 0xF0C040, true);
        float y = top + ROW + 1.0F;
        if (rows.isEmpty()) {
            text(font, empty, 0.0F, y, 0x808090, true);
            return y + ROW * TileEntityCasinoLeaderboard.ROWS;
        }
        for (String row : rows) {
            int tab = row.indexOf('\t');
            String left = tab < 0 ? row : row.substring(0, tab);
            String right = tab < 0 ? "" : row.substring(tab + 1);
            text(font, left, -27.0F, y, 0xFFFFFF, false);
            float width = font.getStringWidth(right) * TEXT_SCALE;
            text(font, right, 27.0F - width, y, 0x7CFC7C, false);
            y += ROW;
        }
        return top + ROW + 1.0F + ROW * TileEntityCasinoLeaderboard.ROWS;
    }

    private static void text(FontRenderer font, String text, float x, float y, int rgb,
                             boolean centred) {
        GlStateManager.pushMatrix();
        float left = centred ? x - font.getStringWidth(text) * TEXT_SCALE / 2.0F : x;
        GlStateManager.translate(left, y, 0.5F);
        GlStateManager.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        font.drawString(text, 0, 0, rgb);
        GlStateManager.popMatrix();
    }
}
