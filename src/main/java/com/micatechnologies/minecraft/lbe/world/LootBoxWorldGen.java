package com.micatechnologies.minecraft.lbe.world;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.block.BlockLootBox;
import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Scatters loot boxes through the world, one chunk at a time.
 *
 * <p>Each tier gets an independent per-chunk roll, so a chunk may contain several boxes or (much
 * more often) none. Independent rather than "pick one tier weighted" because the tiers are meant to
 * be found at genuinely different rates — at the defaults a common box turns up roughly every eight
 * chunks and a legendary roughly every thousand — and a single weighted pick would tie those rates
 * together so that making legendaries rarer would silently make commons more frequent.</p>
 *
 * <p><b>Everything here runs on the server</b>, inside chunk population, on the world-gen thread.
 * That budget is the reason this class does no scoring, no registry lookups and no catalogue work:
 * it places a block, and the box does not decide what is inside it until someone opens it.</p>
 */
public class LootBoxWorldGen implements IWorldGenerator {

    /** How many random positions to try per placement before giving up on that box. */
    private static final int PLACEMENT_ATTEMPTS = 12;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
                         IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (!LbeConfig.enableWorldGen || world.isRemote) {
            return;
        }
        if (!LbeConfig.generatesIn(world.provider.getDimension())) {
            return;
        }
        for (Rarity rarity : Rarity.values()) {
            double chance = LbeConfig.spawnChance(rarity);
            if (chance > 0.0D && random.nextDouble() < chance) {
                place(random, chunkX, chunkZ, world, rarity);
            }
        }
    }

    private void place(Random random, int chunkX, int chunkZ, World world, Rarity rarity) {
        BlockLootBox block = LbeBlocks.box(rarity);
        if (block == null) {
            return;
        }
        int minY = Math.min(LbeConfig.worldGenMinY, LbeConfig.worldGenMaxY);
        int maxY = Math.max(LbeConfig.worldGenMinY, LbeConfig.worldGenMaxY);

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            // Offset by 8 to stay inside the chunk being populated. Writing outside it is the classic
            // world-gen cascade bug: it forces a neighbouring chunk to generate mid-population, which
            // recurses and can hang a server on first world load.
            int x = chunkX * 16 + 8 + random.nextInt(16);
            int z = chunkZ * 16 + 8 + random.nextInt(16);

            BlockPos candidate = surfaceCandidate(world, x, z, minY, maxY);
            if (candidate == null && LbeConfig.worldGenUnderground) {
                candidate = undergroundCandidate(world, random, x, z, minY, maxY);
            }
            if (candidate != null) {
                world.setBlockState(candidate, block.getDefaultState(), 2);
                return;
            }
        }
    }

    /** The first air block above the terrain height, if it is in range and stands on something solid. */
    private static BlockPos surfaceCandidate(World world, int x, int z, int minY, int maxY) {
        BlockPos surface = world.getHeight(new BlockPos(x, 0, z));
        if (surface.getY() < minY || surface.getY() > maxY) {
            return null;
        }
        return isPlaceable(world, surface) ? surface : null;
    }

    /** A random air pocket in the given Y band that has a solid floor — i.e. somewhere in a cave. */
    private static BlockPos undergroundCandidate(World world, Random random, int x, int z,
                                                 int minY, int maxY) {
        int span = Math.max(1, maxY - minY);
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos candidate = new BlockPos(x, minY + random.nextInt(span), z);
            if (isPlaceable(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Somewhere a box can sit: empty space, with head room, on a full solid top face.
     *
     * <p>The head-room check is what stops boxes generating in the single-block gap under an
     * overhang, where they are invisible; the top-face check is what stops them generating on
     * a fence post or a layer of snow and immediately looking wrong.</p>
     */
    private static boolean isPlaceable(World world, BlockPos pos) {
        if (pos.getY() < 1 || pos.getY() > 254) {
            return false;
        }
        if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) {
            return false;
        }
        IBlockState below = world.getBlockState(pos.down());
        return below.isTopSolid() && below.getMaterial().isSolid();
    }
}
