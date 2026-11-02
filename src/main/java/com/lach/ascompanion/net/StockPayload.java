package com.lach.ascompanion.net;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** Block items in the linked terminal's ME network, with amounts. */
public record StockPayload(boolean linked, boolean online, List<Item> items, long[] amounts) implements CustomPacketPayload {
    public static final int MAX = 4096;
    public static final Type<StockPayload> TYPE = new Type<>(AppliedSwatchesCompanion.id("stock"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);
    public static final StreamCodec<RegistryFriendlyByteBuf, StockPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.linked);
        buf.writeBoolean(p.online);
        buf.writeVarInt(p.items.size());
        for (int i = 0; i < p.items.size(); i++) {
            ITEM.encode(buf, p.items.get(i));
            buf.writeVarLong(p.amounts[i]);
        }
    }, buf -> {
        boolean linked = buf.readBoolean(), online = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), MAX);
        List<Item> items = new ArrayList<>(n);
        long[] amounts = new long[n];
        for (int i = 0; i < n; i++) {
            items.add(ITEM.decode(buf));
            amounts[i] = buf.readVarLong();
        }
        return new StockPayload(linked, online, items, amounts);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
