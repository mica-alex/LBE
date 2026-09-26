package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.casino.block.BlockLootVendor;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntityLootVendor;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * Shows what a vendor is selling: the tier's loot box turning slowly behind the glass, with its name
 * and price beneath. The price comes from the server, which is what will actually be charged.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityLootVendorRenderer extends TileEntitySpecialRenderer<TileEntityLootVendor> {

    private static final double MAX_DISTANCE_SQ = 24.0D * 24.0D;

    /**
     * The front face is 2/16 in from the block edge, so 6/16 in front of the centre. The window
     * behind it is recessed to 10/16, so the box turns 4/16 behind the face, inside the recess.
     * All three come from tools/gen_casino_models.py.
     */
    private static final double FACE = 6.0D / 16.0D;
    private static final double BOX_DEPTH = 4.0D / 16.0D;

    @Override
    public void render(TileEntityLootVendor tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (tile.getWorld() == null || x * x + y * y + z * z > MAX_DISTANCE_SQ) {
            return;
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockLootVendor)) {
            return;
        }
        EnumFacing facing = state.getValue(BlockLootVendor.FACING);
        Rarity tier = tile.tier();
        double time = tile.getWorld().getTotalWorldTime() + partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-facing.getHorizontalAngle(), 0.0F, 1.0F, 0.0F);

        // The box, turning in the window.
        ItemStack box = new ItemStack(LbeBlocks.box(tier));
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, 9.0D / 16.0D, FACE - BOX_DEPTH);
        GlStateManager.rotate((float) (time * 2.0D % 360.0D), 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(box,
            ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();

        // The tier and price, on the panel under the window.
        FontRenderer font = getFontRenderer();
        if (font != null) {
            float lastX = OpenGlHelper.lastBrightnessX;
            float lastY = OpenGlHelper.lastBrightnessY;
            GlStateManager.pushMatrix();
            // The panel under the window runs from the floor to 5/16.
            GlStateManager.translate(0.0D, 2.5D / 16.0D, FACE + 0.004D);
            GlStateManager.scale(1.0F / 64.0F, -1.0F / 64.0F, 1.0F / 64.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            centred(font, I18n.format("lbe.tier." + tier.id()).toUpperCase(java.util.Locale.ROOT), -5.0F,
                tier.rgb(), 0.55F);
            String price = tile.priceText().isEmpty() ? "" : tile.priceText();
            centred(font, price, 1.0F, 0x7CFC7C, 0.55F);
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void centred(FontRenderer font, String text, float y, int rgb, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(-font.getStringWidth(text) * scale / 2.0F, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        font.drawString(text, 0, 0, rgb & 0xFFFFFF);
        GlStateManager.popMatrix();
    }
}
