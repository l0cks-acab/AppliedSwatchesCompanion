package com.lach.ascompanion.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Focal Point module: finds plain stretches that could use an accent. A spot is a flat area, at least 3 x 3 faces,
 * of one block type whose visual weight is in the light part of the scan's range. Its middle face is where an accent
 * would draw the eye.
 */
public final class Focal {
    public static final int MAX_SPOTS = 12;
    /** Share of the scan's weight range, from the light end, that counts as calm. */
    private static final float CALM = 0.45f;

    public record Spot(AABB area, BlockPos centre, Direction dir, int faces) {
    }

    private record Plane(Direction dir, int at) {
    }

    private Focal() {
    }

    private static long key(int u, int v) {
        return (long) u << 32 | (v & 0xFFFFFFFFL);
    }

    public static List<Spot> find(Level level, Analysis a, float wMin, float wMax) {
        float limit = wMax - wMin < 0.05f ? Float.MAX_VALUE : wMin + CALM * (wMax - wMin);
        Map<Plane, Map<Long, Integer>> planes = new HashMap<>();
        for (int i = 0; i < a.faces(); i++) {
            if (a.faceWeight()[i] > limit) continue;
            BlockPos p = a.pos(i);
            Direction d = a.dir(i);
            int[] uvw = uvw(p, d);
            planes.computeIfAbsent(new Plane(d, uvw[2]), k -> new HashMap<>()).put(key(uvw[0], uvw[1]), i);
        }
        List<Spot> out = new ArrayList<>();
        for (var e : planes.entrySet()) {
            Map<Long, Integer> cells = e.getValue();
            if (cells.size() < 9) continue;
            Map<Long, Block> blocks = new HashMap<>();
            for (var c : cells.entrySet()) blocks.put(c.getKey(), level.getBlockState(a.pos(c.getValue())).getBlock());
            Set<Long> marked = new HashSet<>();
            for (long k : cells.keySet()) {
                int u = (int) (k >> 32), v = (int) k;
                Block b = blocks.get(k);
                boolean ok = true;
                for (int du = 0; du < 3 && ok; du++)
                    for (int dv = 0; dv < 3 && ok; dv++)
                        ok = blocks.get(key(u + du, v + dv)) == b;
                if (!ok) continue;
                for (int du = 0; du < 3; du++)
                    for (int dv = 0; dv < 3; dv++) marked.add(key(u + du, v + dv));
            }
            Set<Long> seen = new HashSet<>();
            for (long start : marked) {
                if (!seen.add(start)) continue;
                List<Long> comp = new ArrayList<>();
                ArrayDeque<Long> q = new ArrayDeque<>();
                q.add(start);
                while (!q.isEmpty()) {
                    long k = q.poll();
                    comp.add(k);
                    int u = (int) (k >> 32), v = (int) k;
                    for (long n : new long[]{key(u + 1, v), key(u - 1, v), key(u, v + 1), key(u, v - 1)})
                        if (marked.contains(n) && seen.add(n)) q.add(n);
                }
                if (comp.size() >= 9) out.add(spot(a, cells, comp, e.getKey().dir()));
            }
        }
        out.sort(Comparator.comparingInt(Spot::faces).reversed());
        return out.size() > MAX_SPOTS ? out.subList(0, MAX_SPOTS) : out;
    }

    private static Spot spot(Analysis a, Map<Long, Integer> cells, List<Long> comp, Direction d) {
        double cu = 0, cv = 0;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long k : comp) {
            cu += (int) (k >> 32);
            cv += (int) k;
            BlockPos p = a.pos(cells.get(k));
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
        }
        cu /= comp.size();
        cv /= comp.size();
        long best = comp.get(0);
        double bestD = Double.MAX_VALUE;
        for (long k : comp) {
            double du = (int) (k >> 32) - cu, dv = (int) k - cv, dist = du * du + dv * dv;
            if (dist < bestD) {
                bestD = dist;
                best = k;
            }
        }
        AABB box = new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
        double plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(d.getAxis()) : box.min(d.getAxis());
        box = switch (d.getAxis()) {
            case X -> new AABB(plane, box.minY, box.minZ, plane, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, plane, box.minZ, box.maxX, plane, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, plane, box.maxX, box.maxY, plane);
        };
        return new Spot(box, a.pos(cells.get(best)), d, comp.size());
    }

    /** In-plane coordinates u, v and the plane itself. */
    private static int[] uvw(BlockPos p, Direction d) {
        return switch (d.getAxis()) {
            case X -> new int[]{p.getZ(), p.getY(), p.getX()};
            case Y -> new int[]{p.getX(), p.getZ(), p.getY()};
            case Z -> new int[]{p.getX(), p.getY(), p.getZ()};
        };
    }
}
