package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.ColorMath;
import net.minecraft.client.Minecraft;
import com.lach.appliedswatches.client.ModelQuads;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one face of a block looks like: its average colour (as AppliedSwatches works it out, but per face) and how
 * busy its texture is (spread of lightness across the pixels). Cleared on resource reload.
 */
public final class FaceColors {
    public record Face(int rgb, float[] lab, float busy) {
    }

    private record Key(BlockState state, Direction dir) {
    }

    /** Mean r, g, b, opaque pixel count and lightness spread of one sprite. */
    private record Sprite(int r, int g, int b, int n, float spread) {
    }

    private static final Map<Key, Face> FACES = new HashMap<>();
    private static final Map<Identifier, Sprite> SPRITES = new HashMap<>();
    private static final Face NONE = new Face(0, new float[3], -1);

    private FaceColors() {
    }

    public static void clear() {
        FACES.clear();
        SPRITES.clear();
    }

    /** Null when the face has nothing visible. Tint is taken at the first position the face is seen at. */
    public static @Nullable Face get(BlockState state, Direction dir, BlockAndTintGetter level, BlockPos pos) {
        Face f = FACES.computeIfAbsent(new Key(state, dir), k -> compute(state, dir, level, pos));
        return f == NONE ? null : f;
    }

    private static Face compute(BlockState state, Direction dir, BlockAndTintGetter level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        List<BakedQuad> quads = new ArrayList<>();
        try {
            List<BlockStateModelPart> parts = ModelQuads.parts(state);
            for (BlockStateModelPart part : parts) {
                quads.addAll(part.getQuads(dir));
                for (BakedQuad q : part.getQuads(null)) if (q.direction() == dir) quads.add(q);
            }
            if (quads.isEmpty()) for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(null));
        } catch (RuntimeException ignored) {
        }
        long r = 0, g = 0, b = 0, n = 0;
        double spread = 0;
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        List<Integer> tints = new ArrayList<>();
        for (BakedQuad q : quads) {
            sprites.add(q.materialInfo().sprite());
            tints.add(q.materialInfo().isTinted() ? q.materialInfo().tintIndex() : -1);
        }
        if (sprites.isEmpty()) {
            sprites.add(ModelQuads.particle(state));
            tints.add(-1);
        }
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprite(sprites.get(i));
            if (s.n == 0) continue;
            int cr = s.r, cg = s.g, cb = s.b;
            if (tints.get(i) >= 0) {
                int tint;
                try {
                    BlockTintSource src = mc.getBlockColors().getTintSource(state, tints.get(i));
                    tint = src == null ? -1 : src.colorInWorld(state, level, pos);
                } catch (RuntimeException e) {
                    tint = -1;
                }
                if (tint != -1) {
                    cr = cr * ((tint >> 16) & 0xFF) / 255;
                    cg = cg * ((tint >> 8) & 0xFF) / 255;
                    cb = cb * (tint & 0xFF) / 255;
                }
            }
            r += (long) cr * s.n;
            g += (long) cg * s.n;
            b += (long) cb * s.n;
            spread += (double) s.spread * s.n;
            n += s.n;
        }
        if (n == 0) return NONE;
        int rgb = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        return new Face(rgb, ColorMath.lab(rgb), (float) (spread / n));
    }

    private static Sprite sprite(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite.contents().name(), k -> {
            SpriteContents c = sprite.contents();
            var img = c.getOriginalImage();
            int w = Math.min(c.width(), img.getWidth()), h = Math.min(c.height(), img.getHeight());
            long r = 0, g = 0, b = 0;
            double sumL = 0, sumL2 = 0;
            int n = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int p = img.getPixel(x, y);
                    if ((p >>> 24) < 128) continue;
                    int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
                    r += pr;
                    g += pg;
                    b += pb;
                    float l = ColorMath.lab(pr << 16 | pg << 8 | pb)[0];
                    sumL += l;
                    sumL2 += l * l;
                    n++;
                }
            if (n == 0) return new Sprite(0, 0, 0, 0, 0);
            double mean = sumL / n;
            float spread = (float) Math.sqrt(Math.max(0, sumL2 / n - mean * mean));
            return new Sprite((int) (r / n), (int) (g / n), (int) (b / n), n, spread);
        });
    }
}
