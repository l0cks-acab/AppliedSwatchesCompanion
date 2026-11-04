package com.lach.ascompanion;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.List;

/** Lens modules. Each one fits its own slot in the Lens screen's module panel. */
public enum Module implements StringRepresentable {
    RECOLOUR("recolour", 0xE0574A),
    GRADIENT("gradient", 0x6FA8FF),
    FOCAL("focal", 0x5BE0C8),
    SURROUNDINGS("surroundings", 0x6FCF5A);

    public static final Codec<Module> CODEC = StringRepresentable.fromEnum(Module::values);
    public static final Codec<List<Module>> LIST_CODEC = CODEC.listOf();
    public static final StreamCodec<ByteBuf, Module> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Module::ordinal);
    public static final StreamCodec<ByteBuf, List<Module>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list(values().length));

    private final String id;
    public final int colour;

    Module(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String itemId() {
        return id + "_module";
    }
}
