package com.lach.ascompanion.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Surroundings module: how well the build's colours sit in the terrain around it. Samples the top block of every
 * column in a ring around the region, groups those colours, and checks each chromatic palette colour against the
 * nearest terrain colour by hue and chroma only. Lightness is ignored, since a pale house on dark grass is fine.
 * Neutral colours (greys, black, white) fit anywhere.
 */
public final class Surroundings {
    public static final int RING = 16;
    private static final float NEUTRAL = 12f, FALLOFF = 25f;

    public record Result(int score, String verdict, List<Analysis.Cluster> terrain) {
    }

    private Surroundings() {
    }

    public static Result compute(net.minecraft.client.multiplayer.ClientLevel level, Analysis a) {
        BlockPos min = a.min(), max = a.max();
        Map<Integer, Integer> rgb = new HashMap<>();
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = min.getX() - RING; x <= max.getX() + RING; x++)
            for (int z = min.getZ() - RING; z <= max.getZ() + RING; z++) {
                if (x >= min.getX() && x <= max.getX() && z >= min.getZ() && z <= max.getZ()) continue;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (top < level.getMinY()) continue;
                p.set(x, top, z);
                BlockState st = level.getBlockState(p);
                if (st.isAir()) continue;
                FaceColors.Face f = FaceColors.get(st, Direction.UP, level, p);
                if (f == null) continue;
                rgb.merge(f.rgb(), 1, Integer::sum);
                n++;
            }
        if (n == 0) return new Result(-1, "none", List.of());
        List<Analysis.Cluster> terrain = Analyzer.cluster(rgb, n).stream().filter(c -> c.share() >= 0.05f).limit(6).toList();
        double sum = 0, wsum = 0;
        for (Analysis.Cluster c : a.clusters()) {
            if (c.share() < 0.03f) continue;
            double fit = 1;
            if (chroma(c.lab()) >= NEUTRAL) {
                double best = Double.MAX_VALUE;
                for (Analysis.Cluster t : terrain) best = Math.min(best, hueChroma(c.lab(), t.lab()));
                fit = Math.exp(-Math.pow(best / FALLOFF, 2));
            }
            sum += fit * c.share();
            wsum += c.share();
        }
        int score = wsum == 0 ? 100 : (int) Math.round(100 * sum / wsum);
        return new Result(score, score >= 75 ? "fits" : score >= 50 ? "okay" : "stands_out", terrain);
    }

    private static double chroma(float[] lab) {
        return Math.hypot(lab[1], lab[2]);
    }

    /** CIE94 with the lightness term dropped. */
    static double hueChroma(float[] r, float[] s) {
        double c1 = chroma(r), c2 = chroma(s), dc = c1 - c2;
        double da = r[1] - s[1], db = r[2] - s[2];
        double dh2 = Math.max(0, da * da + db * db - dc * dc);
        double sc = 1 + 0.045 * c1, sh = 1 + 0.015 * c1;
        return Math.sqrt((dc / sc) * (dc / sc) + dh2 / (sh * sh));
    }
}
