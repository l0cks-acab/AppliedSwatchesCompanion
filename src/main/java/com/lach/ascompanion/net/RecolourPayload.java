package com.lach.ascompanion.net;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/** Swap every {@code from} block (and its stairs, slabs and walls) in the lens region for {@code to}, or undo the last swap. */
public record RecolourPayload(Item from, Item to, boolean undo) implements CustomPacketPayload {
    public static final Type<RecolourPayload> TYPE = new Type<>(AppliedSwatchesCompanion.id("recolour"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecolourPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), RecolourPayload::from,
            ByteBufCodecs.registry(Registries.ITEM), RecolourPayload::to,
            ByteBufCodecs.BOOL, RecolourPayload::undo,
            RecolourPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
