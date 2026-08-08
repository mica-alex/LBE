package com.micatechnologies.minecraft.lbe;

import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Creative inventory tab for all LBE content.
 */
public final class LbeTab {

    public static final CreativeTabs LBE_TAB = new CreativeTabs(LbeConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            return ICON;
        }
    };

    /**
     * Tab icon. Deliberately a mutable static rather than an inline {@code new ItemStack(...)} in
     * {@code createIcon()}: {@link CreativeTabs} is constructed during class-load, long before block
     * registration, so referencing an LBE block directly there yields an air stack.
     * {@link #initTabElements()} swaps in the real icon once registration is done.
     */
    private static ItemStack ICON = new ItemStack(Items.GOLD_NUGGET);

    private LbeTab() {
        throw new AssertionError("No instances.");
    }

    /**
     * Called from {@code preInit} after {@link LbeBlocks} has populated the registry. Points
     * {@link #ICON} at the legendary box so the creative tab shows the mod's most recognisable item.
     */
    public static void initTabElements() {
        Item legendaryBox = LbeBlocks.boxItem(Rarity.LEGENDARY);
        if (legendaryBox != null) {
            ICON = new ItemStack(legendaryBox);
        }
    }
}
