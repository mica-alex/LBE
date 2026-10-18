package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * Casino carpet as a full block, the way wool is to vanilla carpet. Laid into the floor rather than
 * on it, so a machine, a stool or a rope post can stand on the pattern without floating a pixel
 * above the ground. One per colourway, sharing {@link BlockCasinoCarpet}'s texture.
 */
public class BlockCasinoCarpetSolid extends Block {

    public BlockCasinoCarpetSolid(String name) {
        super(Material.CLOTH);
        setRegistryName(LbeConstants.MOD_NAMESPACE, name);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(0.8F);
        setSoundType(SoundType.CLOTH);
    }
}
