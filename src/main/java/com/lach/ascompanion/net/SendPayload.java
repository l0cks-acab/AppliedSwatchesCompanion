package com.lach.ascompanion.net;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.List;

/** Picked accent blocks: pull them into the linked terminal's output, or load them into a Swatch Applicator. */
public record SendPayload(List<Item> items, boolean toApplicator, int perItem, String name) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 27;
    public static final Type<SendPayload> TYPE = new Type<>(AppliedSwatchesCompanion.id("send"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SendPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list(MAX_ITEMS)), SendPayload::items,
            ByteBufCodecs.BOOL, SendPayload::toApplicator,
            ByteBufCodecs.VAR_INT, SendPayload::perItem,
            ByteBufCodecs.stringUtf8(64), SendPayload::name,
            SendPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
