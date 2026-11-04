package com.lach.ascompanion.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of a lens scan. Faces are stored flat: position, direction and visual weight of every face that's
 * exposed to air, which is what the heatmap draws.
 */
public record Analysis(ResourceKey<Level> dimension, BlockPos min, BlockPos max,
                       long[] facePos, byte[] faceDir, float[] faceWeight,
                       List<BlockPos> orphans, List<Cluster> clusters, Map<Block, Integer> counts,
                       float meanL, float spreadL, int[] scores, String[] verdicts, Object[][] verdictArgs) {
    public static final int CONTRAST = 0, BALANCE = 1, PALETTE = 2, SIMILARITY = 3, OVERALL = 4;
    public static final String[] NAMES = {"contrast", "balance", "palette", "similarity", "overall"};

    /** A group of similar face colours, with its share of the visible surface. */
    public record Cluster(int rgb, float[] lab, float share) {
    }

    /** Block types with at least one visible face. */
    public Set<Block> used() {
        return counts.keySet();
    }

    public int faces() {
        return facePos.length;
    }

    public BlockPos pos(int i) {
        return BlockPos.of(facePos[i]);
    }

    public Direction dir(int i) {
        return Direction.from3DDataValue(faceDir[i]);
    }
}
