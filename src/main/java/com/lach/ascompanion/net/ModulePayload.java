package com.lach.ascompanion.net;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import com.lach.ascompanion.Module;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Install a module from the player's inventory into the held lens, or take one out. */
public record ModulePayload(Module module, boolean install) implements CustomPacketPayload {
    public static final Type<ModulePayload> TYPE = new Type<>(AppliedSwatchesCompanion.id("module"));
    public static final StreamCodec<ByteBuf, ModulePayload> STREAM_CODEC = StreamCodec.composite(
            Module.STREAM_CODEC, ModulePayload::module,
            ByteBufCodecs.BOOL, ModulePayload::install,
            ModulePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
