package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.BlockColorCache;
import com.lach.appliedswatches.client.ColorMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.EntityBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Accent blocks for a scanned build: colours opposite the dominant palette colour on the hue wheel (complementary
 * and split-complementary), pushed away from the build's average lightness so they stand out.
 */
public final class Accents {
    public static final int MAX = 27;
    /** Accents further than this (CIE76) from every target aren't worth suggesting. */
    private static final float MAX_DIST = 55f;

    public enum Scheme { COMPLEMENTARY, SPLIT }

    public record Accent(Item item, long amount, int rgb, int target, Scheme scheme, float dist) {
    }

    /** One target colour and the scheme it came from. */
    public record Target(float[] lab, int rgb, Scheme scheme) {
    }

    private Accents() {
    }

    public static List<Target> targets(Analysis a) {
        if (a.clusters().isEmpty()) return List.of();
        float[] dom = a.clusters().get(0).lab();
        double hue = Math.atan2(dom[2], dom[1]);
        double chroma = Math.max(38, Math.hypot(dom[1], dom[2]));
        float l = a.meanL() > 55 ? a.meanL() - 30 : a.meanL() + 30;
        l = Math.max(18, Math.min(88, l));
        List<Target> out = new ArrayList<>();
        double[] offsets = {Math.PI, Math.toRadians(150), Math.toRadians(-150)};
        for (int i = 0; i < offsets.length; i++) {
            double h = hue + offsets[i];
            float[] lab = {l, (float) (chroma * Math.cos(h)), (float) (chroma * Math.sin(h))};
            out.add(new Target(lab, Analyzer.labToRgb(lab), i == 0 ? Scheme.COMPLEMENTARY : Scheme.SPLIT));
        }
        return out;
    }

    /** Ranked accents from {@code stock} (the linked network), or from every block in the game when it's null. */
    public static List<Accent> suggest(Analysis a, List<Item> stock, long[] amounts) {
        List<Target> targets = targets(a);
        if (targets.isEmpty()) return List.of();
        List<Accent> out = new ArrayList<>();
        boolean all = stock == null;
        List<Item> items = all ? BuiltInRegistries.ITEM.stream().filter(i -> i instanceof BlockItem).toList() : stock;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (!(item instanceof BlockItem bi) || a.used().contains(bi.getBlock())) continue;
            // Machines and other block entities make poor decoration; only skip them when browsing every block.
            if (all && bi.getBlock() instanceof EntityBlock) continue;
            BlockColorCache.Info info = BlockColorCache.get(item);
            if (info == null || !info.full()) continue;
            Target best = null;
            float bestD = Float.MAX_VALUE;
            for (Target t : targets) {
                float d = ColorMath.distance(t.lab(), info.lab());
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
            if (bestD > MAX_DIST) continue;
            out.add(new Accent(item, all ? -1 : amounts[i], info.rgb(), best.rgb(), best.scheme(), bestD));
        }
        out.sort(Comparator.comparingDouble(Accent::dist));
        return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
    }
}
