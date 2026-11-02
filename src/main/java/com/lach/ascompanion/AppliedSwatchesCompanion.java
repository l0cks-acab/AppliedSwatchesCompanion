package com.lach.ascompanion;

import com.lach.ascompanion.net.SendPayload;
import com.lach.ascompanion.net.StockPayload;
import com.lach.ascompanion.net.StockRequestPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AppliedSwatchesCompanion.MODID)
public class AppliedSwatchesCompanion {
    public static final String MODID = "appliedswatches_companion";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LensData>> LENS_DATA = COMPONENTS.registerComponentType("lens",
            b -> b.persistent(LensData.CODEC).networkSynchronized(LensData.STREAM_CODEC));
    public static final DeferredItem<SwatchLensItem> LENS = ITEMS.registerItem("swatch_lens", SwatchLensItem::new, p -> p.stacksTo(1));

    public AppliedSwatchesCompanion(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(AppliedSwatchesCompanion::registerPayloads);
        modBus.addListener(AppliedSwatchesCompanion::creativeTabs);
        LOGGER.info("{} loaded", MODID);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(LENS.get());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(StockPayload.TYPE, StockPayload.STREAM_CODEC, (p, ctx) -> ClientBridge.stock(p));
        r.playToServer(StockRequestPayload.TYPE, StockRequestPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) LensServer.sendStock(sp);
        });
        r.playToServer(SendPayload.TYPE, SendPayload.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) LensServer.send(sp, p);
        });
    }
}
