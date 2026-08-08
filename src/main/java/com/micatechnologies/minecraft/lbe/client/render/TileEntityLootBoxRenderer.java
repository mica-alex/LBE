package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.block.BlockLootBox;
import com.micatechnologies.minecraft.lbe.block.TileEntityLootBox;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

/**
 * Draws a slowly turning, bobbing glint above each loot box.
 *
 * <p>The box's own crate is drawn by the ordinary block model — it is a static cube and belongs in
 * the chunk mesh, where it costs nothing. This renderer adds only the part that has to move: a
 * tier-coloured sparkle hovering over the lid, there to catch your eye across a dark cave and to say
 * "this is worth walking to" before you are close enough to read the gem on the side.</p>
 *
 * <h2>Why there is no opening animation here</h2>
 *
 * <p>An obvious feature to want, and the wrong place for it: opening a box puts a full-screen reveal
 * up immediately, so a lid swinging open in the world would play entirely behind it and be seen by
 * nobody. The box's moment is handled by a particle burst and a sound from the tile entity — visible
 * to <i>other</i> players nearby, and to the opener if they skip the screen — and the drama lives in
 * {@code GuiLootReveal} where there is actually someone looking at it.</p>
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.</p>
 */
public class TileEntityLootBoxRenderer extends TileEntitySpecialRenderer<TileEntityLootBox> {

    private static final ResourceLocation GLINT =
        new ResourceLocation(LbeConstants.MOD_NAMESPACE, "textures/blocks/glint.png");

    /** Height above the block origin the glint floats at, before bobbing. */
    private static final double HOVER = 1.35D;

    /** How far the bob travels, in blocks. Small — a subtle breath, not a bouncing pickup. */
    private static final double BOB_AMPLITUDE = 0.06D;

    /** Half-width of the glint quad, in blocks. */
    private static final float HALF_SIZE = 0.18F;

    @Override
    public void render(TileEntityLootBox tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        Rarity tier = tierOf(tile);
        if (tier == null) {
            return;
        }

        // World time rather than a per-tile counter, offset by position: every glint animates without
        // any of them needing to tick, and neighbouring boxes are visibly out of phase instead of
        // pulsing in lockstep like a row of Christmas lights.
        double phase = tile.getPos().getX() * 0.7D + tile.getPos().getZ() * 1.3D;
        double time = (tile.getWorld() == null ? 0L : tile.getWorld().getTotalWorldTime())
            + partialTicks;
        double bob = Math.sin(time * 0.09D + phase) * BOB_AMPLITUDE;
        float spin = (float) ((time * 1.6D + phase * 20.0D) % 360.0D);

        // Higher tiers turn faster and glow harder, so tier is legible from a distance without
        // reading the colour — which matters to anyone who cannot easily distinguish these hues.
        spin *= 1.0F + tier.ordinal() * 0.35F;
        float scale = 1.0F + tier.ordinal() * 0.12F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + HOVER + bob, z + 0.5D);

        bindTexture(GLINT);
        GlStateManager.disableLighting();
        // Additive blending: the glint reads as emitted light rather than as a decal, and it stays
        // visible against both a dark cave wall and a bright snowfield.
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.color(tier.red(), tier.green(), tier.blue(), 0.9F);

        // Two quads crossed at right angles: readable from any angle, and far cheaper than
        // billboarding against the camera every frame for something this small.
        GlStateManager.rotate(spin, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(scale, scale, scale);
        drawQuad();
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        drawQuad();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * The tier, read from the block rather than stored on the tile entity.
     *
     * <p>Tier is a property of which of the four blocks this is, so asking the block keeps a single
     * source of truth and saves syncing a field that can never change for a given position.</p>
     */
    private static Rarity tierOf(TileEntityLootBox tile) {
        if (tile.getWorld() == null) {
            return null;
        }
        net.minecraft.block.Block block = tile.getWorld().getBlockState(tile.getPos()).getBlock();
        return block instanceof BlockLootBox ? ((BlockLootBox) block).rarity() : null;
    }

    private static void drawQuad() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-HALF_SIZE, -HALF_SIZE, 0.0D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(HALF_SIZE, -HALF_SIZE, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(HALF_SIZE, HALF_SIZE, 0.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(-HALF_SIZE, HALF_SIZE, 0.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
    }
}
