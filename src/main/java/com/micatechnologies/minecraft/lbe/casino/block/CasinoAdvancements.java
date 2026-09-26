package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

/**
 * Grants the casino's advancements, which live under {@code assets/lbe/advancements/casino/}.
 *
 * <p>Every criterion there uses vanilla's {@code minecraft:impossible} trigger and is granted from
 * here instead. A custom trigger would mean registering into vanilla's private trigger table for
 * no gain: the casino already knows exactly when each one is earned, at the moment it settles or
 * reveals a round.
 *
 * <p>Server thread only. Granting a criterion the player already has does nothing.
 */
final class CasinoAdvancements {

    private CasinoAdvancements() {
    }

    /**
     * Grants {@code criterion} of {@code lbe:casino/<path>} to the player, if that advancement is
     * loaded. A pack that has removed or replaced the file loses the advancement, not the game.
     */
    static void grant(EntityPlayerMP player, String path, String criterion) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancementManager().getAdvancement(
            new ResourceLocation(LbeConstants.MOD_NAMESPACE, "casino/" + path));
        if (advancement != null) {
            player.getAdvancements().grantCriterion(advancement, criterion);
        }
    }
}
