package com.micatechnologies.minecraft.lbe.casino.decor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The bouncer's tile entity. It holds nothing and does not tick: it exists only because a
 * renderer has to be bound to one. That is what makes a bouncer free on the server — there is
 * no AI to run and no entity to track, and a hundred of them cost what a hundred stone blocks do.
 *
 * <p>The two head fields are the client's own, eased toward whoever is watching from frame to
 * frame. They are never saved or sent.
 */
public class TileEntityBouncer extends TileEntity {

    /** Client: where the head is turned now, in degrees from the body. */
    public float headYaw;
    public float headPitch;

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        // Two blocks tall: without this, the head is culled whenever the feet are off-screen.
        return new AxisAlignedBB(pos, pos.add(1, 2, 1));
    }
}
