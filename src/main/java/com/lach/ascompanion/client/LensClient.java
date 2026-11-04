package com.lach.ascompanion.client;

import com.lach.ascompanion.AppliedSwatchesCompanion;
import com.lach.ascompanion.LensData;
import com.lach.ascompanion.Module;
import com.lach.ascompanion.SwatchLensItem;
import com.lach.ascompanion.net.StockPayload;
import com.lach.ascompanion.net.StockRequestPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

/** Scan flow, the region outline and the visual weight heatmap. */
@EventBusSubscriber(modid = AppliedSwatchesCompanion.MODID, value = Dist.CLIENT)
public final class LensClient {
    /** Most faces the heatmap draws, so huge scans stay smooth. */
    private static final int MAX_DRAWN = 60000;

    private static Analysis last;
    private static boolean heatmap;
    private static boolean waiting;
    /** Lightest and heaviest face of the last scan, so the heatmap uses its full colour range. */
    private static float wMin, wMax = 1;
    private static boolean focal;
    private static List<Focal.Spot> spots;
    private static Surroundings.Result surroundings;

    private LensClient() {
    }

    public static Analysis last() {
        return last;
    }

    public static boolean heatmap() {
        return heatmap;
    }

    public static void setHeatmap(boolean on) {
        heatmap = on;
    }

    public static void clear() {
        last = null;
        heatmap = false;
        focal = false;
        spots = null;
        surroundings = null;
    }

    public static boolean has(Module m) {
        ItemStack lens = heldLens();
        return !lens.isEmpty() && SwatchLensItem.has(lens, m);
    }

    public static boolean focal() {
        return focal;
    }

    /** Turns the focal point outlines on or off; returns how many spots there are. */
    public static int setFocal(boolean on) {
        focal = on;
        if (on && spots == null && last != null) spots = Focal.find(Minecraft.getInstance().level, last, wMin, wMax);
        return spots == null ? 0 : spots.size();
    }

    /** Terrain fit of the last scan, worked out the first time it's asked for. */
    public static Surroundings.Result surroundings() {
        if (surroundings == null && last != null && Minecraft.getInstance().level != null)
            surroundings = Surroundings.compute(Minecraft.getInstance().level, last);
        return surroundings;
    }

    /** Asks the server for the linked network's stock; the scan runs when the answer arrives. */
    public static void startScan() {
        waiting = true;
        PacketDistributor.sendToServer(new StockRequestPayload());
    }

    public static void onStock(StockPayload p) {
        if (!waiting) return;
        waiting = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack lens = heldLens();
        if (lens.isEmpty()) return;
        LensData d = SwatchLensItem.data(lens);
        if (!d.hasRegion() || d.tooBig()) return;
        long t = System.nanoTime();
        last = Analyzer.scan(mc.level, d.a().get(), d.b().get());
        spots = null;
        surroundings = null;
        wMin = 1;
        wMax = 0;
        for (float w : last.faceWeight()) {
            wMin = Math.min(wMin, w);
            wMax = Math.max(wMax, w);
        }
        if (focal) spots = Focal.find(mc.level, last, wMin, wMax);
        if (last.faces() == 0) {
            mc.player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        com.mojang.logging.LogUtils.getLogger().debug("Lens scan: {} faces in {} ms", last.faces(), (System.nanoTime() - t) / 1_000_000);
        mc.setScreen(new LensScreen(last, p));
    }

    static ItemStack heldLens() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        for (ItemStack s : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()})
            if (s.is(AppliedSwatchesCompanion.LENS.get())) return s;
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        waiting = false;
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack lens = heldLens();
        boolean here = last != null && last.dimension() == mc.level.dimension();
        boolean drawHeat = heatmap && here;
        boolean drawFocal = focal && here && spots != null;
        if (lens.isEmpty() && !drawHeat && !drawFocal) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        if (drawHeat) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f m = pose.last().pose();
            int n = Math.min(last.faces(), MAX_DRAWN);
            for (int i = 0; i < n; i++) face(quads, m, last.pos(i), last.dir(i), heat((last.faceWeight()[i] - wMin) / Math.max(0.05f, wMax - wMin)));
            buffers.endBatch(RenderType.debugQuads());
        }
        if (drawFocal) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f m = pose.last().pose();
            for (Focal.Spot s : spots) face(quads, m, s.centre(), s.dir(), 0x5BE0C8);
            buffers.endBatch(RenderType.debugQuads());
        }

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (drawHeat) for (BlockPos o : last.orphans())
            LevelRenderer.renderLineBox(pose, lines, new AABB(o).inflate(0.02), 1f, 0.85f, 0.2f, 1f);
        if (drawFocal) for (Focal.Spot s : spots)
            LevelRenderer.renderLineBox(pose, lines, s.area().inflate(0.02), 0.36f, 0.88f, 0.78f, 1f);
        if (!lens.isEmpty()) {
            LensData d = SwatchLensItem.data(lens);
            d.a().ifPresent(a -> LevelRenderer.renderLineBox(pose, lines, new AABB(a).inflate(0.01), 0.3f, 0.9f, 1f, 1f));
            d.b().ifPresent(b -> LevelRenderer.renderLineBox(pose, lines, new AABB(b).inflate(0.01), 1f, 0.45f, 0.9f, 1f));
            if (d.hasRegion()) {
                AABB box = new AABB(Vec3.atLowerCornerOf(d.a().get()), Vec3.atLowerCornerOf(d.b().get()));
                box = new AABB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1).inflate(0.03);
                if (d.tooBig()) LevelRenderer.renderLineBox(pose, lines, box, 1f, 0.2f, 0.2f, 1f);
                else LevelRenderer.renderLineBox(pose, lines, box, 0.72f, 0.5f, 1f, 1f);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** Light blue for light faces through to red for heavy ones. */
    static int heat(float w) {
        float[][] stops = {{0.25f, 0.55f, 1f}, {0.2f, 0.9f, 0.5f}, {1f, 0.9f, 0.2f}, {1f, 0.25f, 0.2f}};
        float t = Math.max(0, Math.min(0.999f, w)) * (stops.length - 1);
        int i = (int) t;
        float f = t - i;
        float r = stops[i][0] + (stops[i + 1][0] - stops[i][0]) * f;
        float g = stops[i][1] + (stops[i + 1][1] - stops[i][1]) * f;
        float b = stops[i][2] + (stops[i + 1][2] - stops[i][2]) * f;
        return (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }

    private static void face(VertexConsumer vc, Matrix4f m, BlockPos p, Direction d, int rgb) {
        float e = 0.003f;
        float x0 = p.getX() - e, y0 = p.getY() - e, z0 = p.getZ() - e, x1 = p.getX() + 1 + e, y1 = p.getY() + 1 + e, z1 = p.getZ() + 1 + e;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, a = 110;
        switch (d) {
            case DOWN -> quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
            case UP -> quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
            case NORTH -> quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
            case SOUTH -> quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
            case WEST -> quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
            case EAST -> quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }

    @SubscribeEvent
    static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) rm -> FaceColors.clear());
    }
}
