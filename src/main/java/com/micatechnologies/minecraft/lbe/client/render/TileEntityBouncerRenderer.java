package com.micatechnologies.minecraft.lbe.client.render;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.casino.decor.BlockBouncer;
import com.micatechnologies.minecraft.lbe.casino.decor.TileEntityBouncer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/**
 * Draws the bouncer: a biped model in a suit, hands clasped in front, turned the way the block
 * faces, with the head following whoever is watching when they come close.
 *
 * <p>Every part of the pose is set here and nothing calls {@link ModelBiped#render}, which wants
 * a living entity to animate from. There is none, which is the point.
 *
 * <p><b>Client only.</b> Registered from {@code LbeClientProxy}.
 */
public class TileEntityBouncerRenderer extends TileEntitySpecialRenderer<TileEntityBouncer> {

    private static final ResourceLocation SKIN = new ResourceLocation(LbeConstants.MOD_NAMESPACE,
        "textures/entity/casino_bouncer.png");

    private static final double MAX_DISTANCE_SQ = 64.0D * 64.0D;

    /** Somebody closer than this is watched; anybody further off is not worth turning for. */
    private static final double WATCH_DISTANCE_SQ = 8.0D * 8.0D;

    /** Head-centre height above the block, in blocks. */
    private static final double HEAD_HEIGHT = 1.75D;

    private static final float MAX_YAW = 60.0F;
    private static final float MAX_PITCH = 30.0F;

    /** How much of the way to the target the head turns each frame. */
    private static final float EASE = 0.12F;

    private static final float SCALE = 1.0F / 16.0F;

    private final ModelBiped model = new ModelBiped(0.0F);

    public TileEntityBouncerRenderer() {
        // Hands clasped in front of the belt: both arms forward and in toward each other.
        model.bipedRightArm.rotateAngleX = -0.5F;
        model.bipedRightArm.rotateAngleZ = -0.35F;
        model.bipedLeftArm.rotateAngleX = -0.5F;
        model.bipedLeftArm.rotateAngleZ = 0.35F;
    }

    @Override
    public void render(TileEntityBouncer tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (tile.getWorld() == null || x * x + y * y + z * z > MAX_DISTANCE_SQ) {
            return;
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlockBouncer) || state.getValue(BlockBouncer.HALF)) {
            return;
        }
        float bodyYaw = state.getValue(BlockBouncer.FACING).getHorizontalAngle();
        look(tile, rendererDispatcher.entity, x, y, z, bodyYaw);

        model.bipedHead.rotateAngleY = tile.headYaw * 0.017453292F;
        model.bipedHead.rotateAngleX = tile.headPitch * 0.017453292F;

        bindTexture(SKIN);
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        // As RenderLivingBase places an entity: feet on the block, turned by its body yaw, and
        // flipped into the model's y-down space.
        GlStateManager.translate(x + 0.5D, y + 1.5D, z + 0.5D);
        GlStateManager.rotate(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        model.bipedHead.render(SCALE);
        model.bipedBody.render(SCALE);
        model.bipedRightArm.render(SCALE);
        model.bipedLeftArm.render(SCALE);
        model.bipedRightLeg.render(SCALE);
        model.bipedLeftLeg.render(SCALE);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /** Eases the head toward the viewer if they are close, or back to straight ahead if not. */
    private static void look(TileEntityBouncer tile, Entity viewer, double x, double y, double z,
                             float bodyYaw) {
        float yaw = 0.0F;
        float pitch = 0.0F;
        if (viewer != null) {
            // x, y, z are the block relative to the viewer's feet.
            double dx = -x - 0.5D;
            double dy = -y + viewer.getEyeHeight() - HEAD_HEIGHT;
            double dz = -z - 0.5D;
            double flat = dx * dx + dz * dz;
            if (flat + dy * dy < WATCH_DISTANCE_SQ) {
                float toward = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                yaw = MathHelper.wrapDegrees(toward - bodyYaw);
                pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(flat)) * (180.0D / Math.PI));
                if (Math.abs(yaw) > MAX_YAW + 30.0F) {
                    // Behind the bouncer: face front rather than snap the neck round.
                    yaw = 0.0F;
                    pitch = 0.0F;
                }
            }
        }
        yaw = MathHelper.clamp(yaw, -MAX_YAW, MAX_YAW);
        pitch = MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
        tile.headYaw += (yaw - tile.headYaw) * EASE;
        tile.headPitch += (pitch - tile.headPitch) * EASE;
    }
}
