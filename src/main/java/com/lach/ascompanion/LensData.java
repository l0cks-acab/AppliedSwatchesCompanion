package com.lach.ascompanion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/** The lens's two region corners and the Swatch Terminal it reads stock from. */
public record LensData(Optional<BlockPos> a, Optional<BlockPos> b, Optional<GlobalPos> link) {
    /** Longest side of a scan region, in blocks. */
    public static final int MAX_SIZE = 64;
    public static final LensData EMPTY = new LensData(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<LensData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.optionalFieldOf("a").forGetter(LensData::a),
            BlockPos.CODEC.optionalFieldOf("b").forGetter(LensData::b),
            GlobalPos.CODEC.optionalFieldOf("link").forGetter(LensData::link)
    ).apply(i, LensData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LensData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), LensData::a,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), LensData::b,
            GlobalPos.STREAM_CODEC.apply(ByteBufCodecs::optional), LensData::link,
            LensData::new);

    public LensData withA(BlockPos p) {
        return new LensData(Optional.of(p.immutable()), b, link);
    }

    public LensData withB(BlockPos p) {
        return new LensData(a, Optional.of(p.immutable()), link);
    }

    public LensData withLink(GlobalPos p) {
        return new LensData(a, b, Optional.of(p));
    }

    public LensData cleared() {
        return new LensData(Optional.empty(), Optional.empty(), link);
    }

    public boolean hasRegion() {
        return a.isPresent() && b.isPresent();
    }

    /** Size of the region on each axis, or null without both corners. */
    public int[] size() {
        if (!hasRegion()) return null;
        BlockPos p = a.get(), q = b.get();
        return new int[]{Math.abs(p.getX() - q.getX()) + 1, Math.abs(p.getY() - q.getY()) + 1, Math.abs(p.getZ() - q.getZ()) + 1};
    }

    public boolean tooBig() {
        int[] s = size();
        return s != null && (s[0] > MAX_SIZE || s[1] > MAX_SIZE || s[2] > MAX_SIZE);
    }
}
