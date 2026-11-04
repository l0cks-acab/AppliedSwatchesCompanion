package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.ColorMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores a region the way a painter would squint at it. Only faces you can see (exposed to air or see-through
 * blocks) count, each weighted by its area, which is one per face.
 * <ul>
 * <li>Contrast: spread of lightness (L*). Too little reads flat, too much reads noisy.</li>
 * <li>Balance: where the visual weight sits. Dark, saturated and busy faces are heavy. Weight should sit near the
 * middle sideways and a little low.</li>
 * <li>Palette: face colours grouped by CIELAB distance, compared to the 60-30-10 rule.</li>
 * <li>Similarity: one-off blocks with no matching neighbour break the pattern (Gestalt similarity).</li>
 * </ul>
 */
public final class Analyzer {
    /** CIE76 distance under which two face colours count as the same palette colour. */
    private static final float CLUSTER_DE = 18f;
    private static final float[] IDEAL = {0.6f, 0.3f, 0.1f};
    private static final float TARGET_SPREAD = 18f;

    private Analyzer() {
    }

    public static Analysis scan(net.minecraft.client.multiplayer.ClientLevel level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        List<Long> pos = new ArrayList<>();
        List<Byte> dirs = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        List<float[]> labs = new ArrayList<>();
        Map<Integer, Integer> rgbCount = new HashMap<>();
        Map<Block, List<BlockPos>> byBlock = new HashMap<>();
        double sumL = 0, sumL2 = 0, gx = 0, gy = 0, gz = 0, wx = 0, wy = 0, wz = 0, wsum = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int y = min.getY(); y <= max.getY(); y++)
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                    boolean seen = false;
                    for (Direction d : Direction.values()) {
                        BlockPos n = p.relative(d);
                        if (!Block.shouldRenderFace(level, p, state, level.getBlockState(n), d)) continue;
                        FaceColors.Face f = FaceColors.get(state, d, level, p);
                        if (f == null) continue;
                        seen = true;
                        float l = f.lab()[0], chroma = (float) Math.hypot(f.lab()[1], f.lab()[2]);
                        float w = 0.5f * (100 - l) / 100f + 0.3f * Math.min(1f, chroma / 60f) + 0.2f * Math.min(1f, f.busy() / 20f);
                        w = Math.max(0f, Math.min(1f, w));
                        pos.add(p.asLong());
                        dirs.add((byte) d.get3DDataValue());
                        weights.add(w);
                        labs.add(f.lab());
                        rgbCount.merge(f.rgb(), 1, Integer::sum);
                        sumL += l;
                        sumL2 += l * l;
                        double cx = x + 0.5 + d.getStepX() * 0.5, cy = y + 0.5 + d.getStepY() * 0.5, cz = z + 0.5 + d.getStepZ() * 0.5;
                        gx += cx;
                        gy += cy;
                        gz += cz;
                        wx += cx * w;
                        wy += cy * w;
                        wz += cz * w;
                        wsum += w;
                    }
                    if (seen) byBlock.computeIfAbsent(state.getBlock(), k -> new ArrayList<>()).add(p.immutable());
                }

        int n = pos.size();
        long[] facePos = new long[n];
        byte[] faceDir = new byte[n];
        float[] faceWeight = new float[n];
        for (int i = 0; i < n; i++) {
            facePos[i] = pos.get(i);
            faceDir[i] = dirs.get(i);
            faceWeight[i] = weights.get(i);
        }
        int[] scores = new int[5];
        String[] verdicts = new String[5];
        Object[][] args = new Object[5][];
        if (n == 0) {
            for (int i = 0; i < 5; i++) {
                verdicts[i] = "empty";
                args[i] = new Object[0];
            }
            return new Analysis(level.dimension(), min, max, facePos, faceDir, faceWeight, List.of(), List.of(), Map.of(), 0, 0, scores, verdicts, args);
        }

        // Contrast
        float meanL = (float) (sumL / n);
        float spread = (float) Math.sqrt(Math.max(0, sumL2 / n - meanL * meanL));
        scores[Analysis.CONTRAST] = Math.round(100 * (float) Math.exp(-Math.pow((spread - TARGET_SPREAD) / 11.0, 2)));
        verdicts[Analysis.CONTRAST] = spread < TARGET_SPREAD - 8 ? "flat" : spread > TARGET_SPREAD + 10 ? "noisy" : "good";
        args[Analysis.CONTRAST] = new Object[]{Math.round(spread)};

        // Balance
        gx /= n;
        gy /= n;
        gz /= n;
        double hx = Math.max(0.5, (max.getX() - min.getX() + 1) / 2.0), hy = Math.max(0.5, (max.getY() - min.getY() + 1) / 2.0), hz = Math.max(0.5, (max.getZ() - min.getZ() + 1) / 2.0);
        double dx = 0, dy = 0, dz = 0;
        if (wsum > 0) {
            dx = (wx / wsum - gx) / hx;
            dy = (wy / wsum - gy) / hy;
            dz = (wz / wsum - gz) / hz;
        }
        double side = Math.hypot(dx, dz);
        double sidePen = Math.min(1, side / 0.35);
        double vPen = dy > -0.08 ? Math.min(1, (dy + 0.08) / 0.3) : Math.min(1, (-0.08 - dy) / 0.5);
        scores[Analysis.BALANCE] = (int) Math.round(100 * (1 - 0.5 * sidePen - 0.5 * vPen));
        if (dy > 0.05 && vPen >= sidePen) {
            verdicts[Analysis.BALANCE] = "top_heavy";
            args[Analysis.BALANCE] = new Object[0];
        } else if (side > 0.1) {
            verdicts[Analysis.BALANCE] = "leans";
            args[Analysis.BALANCE] = new Object[]{Direction.getApproximateNearest(dx, 0, dz).getName()};
        } else {
            verdicts[Analysis.BALANCE] = "balanced";
            args[Analysis.BALANCE] = new Object[0];
        }

        // Palette
        List<Analysis.Cluster> clusters = cluster(rgbCount, n);
        float err = 0;
        int significant = 0;
        for (int i = 0; i < Math.max(IDEAL.length, clusters.size()); i++) {
            float share = i < clusters.size() ? clusters.get(i).share() : 0;
            float ideal = i < IDEAL.length ? IDEAL[i] : 0;
            err += Math.abs(share - ideal);
            if (share >= 0.03f) significant++;
        }
        scores[Analysis.PALETTE] = Math.round(100 * Math.max(0, 1 - err / 1.2f));
        verdicts[Analysis.PALETTE] = significant <= 1 ? "monotone" : significant > 5 ? "busy" : "groups";
        args[Analysis.PALETTE] = new Object[]{significant};

        // Similarity
        int blocks = 0;
        for (List<BlockPos> l : byBlock.values()) blocks += l.size();
        List<BlockPos> orphans = new ArrayList<>();
        if (blocks >= 20) {
            for (var e : byBlock.entrySet()) {
                List<BlockPos> list = e.getValue();
                if (list.size() > 2) continue;
                for (BlockPos bp : list) {
                    boolean neighbour = false;
                    for (BlockPos q : BlockPos.betweenClosed(bp.offset(-1, -1, -1), bp.offset(1, 1, 1))) {
                        if (!q.equals(bp) && level.getBlockState(q).is(e.getKey())) {
                            neighbour = true;
                            break;
                        }
                    }
                    if (!neighbour) orphans.add(bp);
                }
            }
        }
        scores[Analysis.SIMILARITY] = Math.round(100 * Math.max(0, 1 - orphans.size() * 20f / Math.max(1, blocks)));
        verdicts[Analysis.SIMILARITY] = orphans.isEmpty() ? "coherent" : "orphans";
        args[Analysis.SIMILARITY] = new Object[]{orphans.size()};

        scores[Analysis.OVERALL] = Math.round((scores[0] + scores[1] + scores[2] + scores[3]) / 4f);
        int o = scores[Analysis.OVERALL];
        verdicts[Analysis.OVERALL] = o >= 80 ? "great" : o >= 60 ? "solid" : o >= 40 ? "rough" : "messy";
        args[Analysis.OVERALL] = new Object[0];

        Map<Block, Integer> counts = new HashMap<>();
        byBlock.forEach((k, v) -> counts.put(k, v.size()));
        return new Analysis(level.dimension(), min, max, facePos, faceDir, faceWeight, orphans, clusters, counts, meanL, spread, scores, verdicts, args);
    }

    /** Groups colours (rgb to count) closer than {@link #CLUSTER_DE}, biggest share first. */
    public static List<Analysis.Cluster> cluster(Map<Integer, Integer> rgbCount, int n) {
        List<Map.Entry<Integer, Integer>> colours = new ArrayList<>(rgbCount.entrySet());
        colours.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed());
        List<double[]> sums = new ArrayList<>(); // L, a, b, count
        for (var e : colours) {
            float[] lab = ColorMath.lab(e.getKey());
            int count = e.getValue();
            double[] best = null;
            float bestD = CLUSTER_DE;
            for (double[] c : sums) {
                float[] centre = {(float) (c[0] / c[3]), (float) (c[1] / c[3]), (float) (c[2] / c[3])};
                float d = ColorMath.distance(centre, lab);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best == null) sums.add(new double[]{lab[0] * count, lab[1] * count, lab[2] * count, count});
            else {
                best[0] += lab[0] * count;
                best[1] += lab[1] * count;
                best[2] += lab[2] * count;
                best[3] += count;
            }
        }
        List<Analysis.Cluster> clusters = new ArrayList<>();
        for (double[] c : sums) {
            float[] lab = {(float) (c[0] / c[3]), (float) (c[1] / c[3]), (float) (c[2] / c[3])};
            clusters.add(new Analysis.Cluster(labToRgb(lab), lab, (float) (c[3] / n)));
        }
        clusters.sort(Comparator.comparingDouble(Analysis.Cluster::share).reversed());
        return clusters;
    }

    /** CIELAB back to sRGB, clamped. */
    public static int labToRgb(float[] lab) {
        double fy = (lab[0] + 16) / 116.0, fx = fy + lab[1] / 500.0, fz = fy - lab[2] / 200.0;
        double x = 0.95047 * finv(fx), y = finv(fy), z = 1.08883 * finv(fz);
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        return gamma(r) << 16 | gamma(g) << 8 | gamma(b);
    }

    private static double finv(double t) {
        return t * t * t > 216 / 24389.0 ? t * t * t : (116 * t - 16) / (24389 / 27.0);
    }

    private static int gamma(double c) {
        c = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
    }
}
