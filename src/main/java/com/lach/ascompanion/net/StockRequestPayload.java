package com.lach.ascompanion.net;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Asks the server what block items the held lens's linked terminal can reach. */
public record StockRequestPayload() implements CustomPacketPayload {
    public static final Type<StockRequestPayload> TYPE = new Type<>(AppliedSwatchesCompanion.id("stock_request"));
    public static final StreamCodec<ByteBuf, StockRequestPayload> STREAM_CODEC = StreamCodec.unit(new StockRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
