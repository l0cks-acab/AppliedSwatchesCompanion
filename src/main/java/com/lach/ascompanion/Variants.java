package com.lach.ascompanion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stairs, slabs and walls that belong to a full block. Vanilla's block families first, then the usual naming
 * ({@code stone_bricks} -> {@code stone_brick_stairs}) so modded blocks work too.
 */
public final class Variants {
    public enum Shape { STAIRS, SLAB, WALL }

    private static Map<Block, Block> baseOf;
    private static Map<Block, Map<Shape, Block>> byBase;

    private Variants() {
    }

    private static synchronized void init() {
        if (baseOf != null) return;
        baseOf = new HashMap<>();
        byBase = new HashMap<>();
        try {
            BlockFamilies.getAllFamilies().forEach(f -> {
                Map<Shape, Block> m = new HashMap<>();
                put(m, Shape.STAIRS, f.get(BlockFamily.Variant.STAIRS));
                put(m, Shape.SLAB, f.get(BlockFamily.Variant.SLAB));
                put(m, Shape.WALL, f.get(BlockFamily.Variant.WALL));
                if (m.isEmpty()) return;
                byBase.put(f.getBaseBlock(), m);
                m.values().forEach(v -> baseOf.put(v, f.getBaseBlock()));
            });
        } catch (Throwable ignored) {
            // Data classes missing: names only.
        }
    }

    private static void put(Map<Shape, Block> m, Shape s, @Nullable Block b) {
        if (b != null) m.put(s, b);
    }

    public static @Nullable Shape shape(Block b) {
        return b instanceof StairBlock ? Shape.STAIRS : b instanceof SlabBlock ? Shape.SLAB : b instanceof WallBlock ? Shape.WALL : null;
    }

    /** The full block a stair, slab or wall belongs to, or the block itself. */
    public static Block base(Block b) {
        init();
        Shape s = shape(b);
        if (s == null) return b;
        Block known = baseOf.get(b);
        if (known != null) return known;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        String suffix = "_" + s.name().toLowerCase();
        if (!id.getPath().endsWith(suffix)) return b;
        String stem = id.getPath().substring(0, id.getPath().length() - suffix.length());
        for (String p : List.of(stem + "s", stem, stem + "_planks", stem + "_block")) {
            Block c = lookup(id.getNamespace(), p);
            if (c != null && shape(c) == null) return c;
        }
        return b;
    }

    /** The base block's variant of this shape, or null when there isn't one. */
    public static @Nullable Block variant(Block base, Shape s) {
        init();
        Map<Shape, Block> m = byBase.get(base);
        if (m != null && m.containsKey(s)) return m.get(s);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(base);
        String p = id.getPath(), suffix = "_" + s.name().toLowerCase();
        for (String stem : List.of(p, p.endsWith("s") ? p.substring(0, p.length() - 1) : p,
                p.endsWith("_planks") ? p.substring(0, p.length() - 7) : p, p.endsWith("_block") ? p.substring(0, p.length() - 6) : p)) {
            Block c = lookup(id.getNamespace(), stem + suffix);
            if (c != null && shape(c) == s) return c;
        }
        return null;
    }

    private static @Nullable Block lookup(String ns, String path) {
        ResourceLocation rl = ResourceLocation.tryBuild(ns, path);
        if (rl == null) return null;
        Block b = BuiltInRegistries.BLOCK.get(rl);
        return b == Blocks.AIR ? null : b;
    }
}
