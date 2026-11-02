package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.BlockColorCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TntBlock;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accent blocks for a scanned build.
 * <p>
 * Harmony (default): colours that belong with the build's own palette. Each chromatic palette colour gets a
 * darker shade, a lighter tint and a muted tone at the same hue. Neutral greys bridge large lightness gaps between
 * palette colours, so black, white and red gets charcoal, greys and maroon.
 * <p>
 * Contrast: complementary and split-complementary to the main colour, for a deliberate pop.
 */
public final class Accents {
    public static final int MAX = 30;
    /** Below this CIELAB chroma a colour counts as neutral (black, white, grey). */
    private static final float NEUTRAL = 15f;
    /** CIE94 distance a candidate may be from its nearest target and still be suggested. */
    private static final float MAX_DIST = 22f;
    /** Candidates this close to a colour the build already uses add nothing. */
    private static final float SAME = 5f;

    public enum Mode { HARMONY, CONTRAST }

    public enum Kind { SHADE, TINT, MUTED, BRIDGE, COMPLEMENTARY, SPLIT }

    public record Accent(Item item, long amount, int rgb, Target target, float dist) {
    }

    /** A colour to look for, which palette colour it came from and how. */
    public record Target(float[] lab, int rgb, Kind kind, int sourceRgb) {
    }

    private Accents() {
    }

    public static List<Target> targets(Analysis a, Mode mode) {
        List<Analysis.Cluster> pal = a.clusters().stream().filter(c -> c.share() >= 0.03f).toList();
        if (pal.isEmpty() && !a.clusters().isEmpty()) pal = List.of(a.clusters().get(0));
        List<Target> out = new ArrayList<>();
        if (pal.isEmpty()) return out;
        if (mode == Mode.CONTRAST) {
            float[] dom = pal.get(0).lab();
            double hue = Math.atan2(dom[2], dom[1]);
            double chroma = Math.max(38, Math.hypot(dom[1], dom[2]));
            float l = clampL(a.meanL() > 55 ? a.meanL() - 30 : a.meanL() + 30);
            double[] offsets = {Math.PI, Math.toRadians(150), Math.toRadians(-150)};
            for (int i = 0; i < offsets.length; i++)
                out.add(target(l, chroma, hue + offsets[i], i == 0 ? Kind.COMPLEMENTARY : Kind.SPLIT, pal.get(0).rgb()));
            return out;
        }
        for (Analysis.Cluster c : pal) {
            float[] lab = c.lab();
            double chroma = Math.hypot(lab[1], lab[2]), hue = Math.atan2(lab[2], lab[1]);
            if (chroma < NEUTRAL) continue;
            out.add(target(clampL(lab[0] - 18), chroma * 0.85, hue, Kind.SHADE, c.rgb()));
            out.add(target(clampL(lab[0] + 16), chroma * 0.8, hue, Kind.TINT, c.rgb()));
            out.add(target(lab[0], chroma * 0.45, hue, Kind.MUTED, c.rgb()));
        }
        // Greys in the lightness gaps between palette colours (and next to a lone colour).
        List<Float> ls = new ArrayList<>();
        for (Analysis.Cluster c : pal) ls.add(c.lab()[0]);
        ls.sort(Float::compare);
        for (int i = 0; i + 1 < ls.size(); i++) {
            float gap = ls.get(i + 1) - ls.get(i);
            if (gap < 22) continue;
            int steps = gap >= 50 ? 2 : 1;
            for (int s = 1; s <= steps; s++) {
                float l = ls.get(i) + gap * s / (steps + 1);
                out.add(target(l, 2, 0, Kind.BRIDGE, 0x808080));
            }
        }
        if (ls.size() == 1) {
            float l = ls.get(0);
            out.add(target(clampL(l > 50 ? l - 25 : l + 25), 2, 0, Kind.BRIDGE, 0x808080));
        }
        return out;
    }

    private static float clampL(float l) {
        return Math.max(10, Math.min(92, l));
    }

    private static Target target(float l, double chroma, double hue, Kind kind, int source) {
        float[] lab = {l, (float) (chroma * Math.cos(hue)), (float) (chroma * Math.sin(hue))};
        return new Target(lab, Analyzer.labToRgb(lab), kind, source);
    }

    /** CIE94 (graphic arts) distance from target {@code r} to sample {@code s}: hue errors count more than lightness. */
    static float cie94(float[] r, float[] s) {
        double c1 = Math.hypot(r[1], r[2]), c2 = Math.hypot(s[1], s[2]);
        double dl = r[0] - s[0], dc = c1 - c2;
        double da = r[1] - s[1], db = r[2] - s[2];
        double dh2 = Math.max(0, da * da + db * db - dc * dc);
        double sc = 1 + 0.045 * c1, sh = 1 + 0.015 * c1;
        return (float) Math.sqrt(dl * dl + (dc / sc) * (dc / sc) + dh2 / (sh * sh));
    }

    /**
     * Ranked accents from {@code stock} (the linked network), or from every block in the game when it's null.
     * Targets take turns so every palette colour gets a few matches near the top.
     */
    public static List<Accent> suggest(Analysis a, Mode mode, List<Item> stock, long[] amounts) {
        List<Target> targets = targets(a, mode);
        if (targets.isEmpty()) return List.of();
        boolean all = stock == null;
        List<Item> items = all ? BuiltInRegistries.ITEM.stream().filter(i -> i instanceof BlockItem).toList() : stock;
        List<List<Accent>> perTarget = new ArrayList<>();
        for (int t = 0; t < targets.size(); t++) perTarget.add(new ArrayList<>());
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (!(item instanceof BlockItem bi) || a.used().contains(bi.getBlock())) continue;
            if (all && bi.getBlock() instanceof EntityBlock) continue;
            if (bi.getBlock() instanceof TntBlock || bi.getBlock().defaultBlockState().is(Tags.Blocks.ORES)) continue;
            BlockColorCache.Info info = BlockColorCache.get(item);
            if (info == null || !info.full()) continue;
            boolean same = false;
            for (Analysis.Cluster c : a.clusters())
                if (c.share() >= 0.03f && cie94(c.lab(), info.lab()) < SAME) same = true;
            if (same) continue;
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int t = 0; t < targets.size(); t++) {
                float d = cie94(targets.get(t).lab(), info.lab());
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
            float limit = mode == Mode.CONTRAST ? MAX_DIST * 2 : MAX_DIST;
            if (bestD > limit) continue;
            perTarget.get(best).add(new Accent(item, all ? -1 : amounts[i], info.rgb(), targets.get(best), bestD));
        }
        for (List<Accent> l : perTarget) l.sort(Comparator.comparingDouble(Accent::dist));
        List<Accent> out = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (int round = 0; out.size() < MAX; round++) {
            boolean any = false;
            for (List<Accent> l : perTarget) {
                if (round < l.size() && out.size() < MAX && seen.add(l.get(round).item())) {
                    out.add(l.get(round));
                    any = true;
                }
            }
            if (!any) break;
        }
        return out;
    }
}
