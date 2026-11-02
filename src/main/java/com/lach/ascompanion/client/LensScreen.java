package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.FileDialogs;
import com.lach.appliedswatches.client.Theme;
import com.lach.appliedswatches.client.ThemeManager;
import com.lach.ascompanion.net.SendPayload;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Scan results: scores, the build's palette and accent picks from the linked network. */
public class LensScreen extends Screen {
    private static final int W = 300, H = 226;
    private static final int PANEL = 0xFF3C424B, SLOT = 0xFF262A30, EDGE = 0xFF1B1E22, LIGHT = 0xFF5A626D, TEXT = 0xFFE6E6E6, DIM = 0xFF9AA2AD;
    private static final int GRID_X = 186, GRID_Y = 32, COLS = 6, ROWS = 5, CELL = 18;
    private static final int BAR_X = 72, BAR_W = 84;
    private static final int PAL_Y = 152, PAL_H = 12;
    private static final int PER_ITEM = 64;
    private static final String P = "gui.appliedswatches_companion.";

    private final Analysis analysis;
    private final StockPayload stock;
    /** Kept between scans. */
    private static Accents.Mode mode = Accents.Mode.HARMONY;
    private List<Accents.Accent> accents;
    private final Set<Item> picked = new LinkedHashSet<>();
    private int left, top;
    private Button terminalBtn, applicatorBtn;

    public LensScreen(Analysis analysis, StockPayload stock) {
        super(Component.translatable(P + "title"));
        this.analysis = analysis;
        this.stock = stock;
        refreshAccents();
    }

    private void refreshAccents() {
        accents = stock.online() ? Accents.suggest(analysis, mode, stock.items(), stock.amounts()) : Accents.suggest(analysis, mode, null, null);
        picked.clear();
    }

    private Component modeLabel() {
        return Component.translatable(P + "mode." + mode.name().toLowerCase());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int y = top + H - 26, bw = 68, gap = 4, x = left + 8;
        addRenderableWidget(Button.builder(heatLabel(), b -> {
            LensClient.setHeatmap(!LensClient.heatmap());
            b.setMessage(heatLabel());
            if (LensClient.heatmap()) onClose();
        }).bounds(x, y, bw, 18).build());
        terminalBtn = addRenderableWidget(Button.builder(Component.translatable(P + "to_terminal"), b -> send(false)).bounds(x + (bw + gap), y, bw, 18)
                .tooltip(Tooltip.create(Component.translatable(P + "to_terminal.tip", PER_ITEM))).build());
        applicatorBtn = addRenderableWidget(Button.builder(Component.translatable(P + "to_applicator"), b -> send(true)).bounds(x + 2 * (bw + gap), y, bw, 18)
                .tooltip(Tooltip.create(Component.translatable(P + "to_applicator.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable(P + "save_theme"), b -> saveTheme()).bounds(x + 3 * (bw + gap), y, bw, 18)
                .tooltip(Tooltip.create(Component.translatable(P + "save_theme.tip"))).build());
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            mode = mode == Accents.Mode.HARMONY ? Accents.Mode.CONTRAST : Accents.Mode.HARMONY;
            b.setMessage(modeLabel());
            refreshAccents();
            updateButtons();
        }).bounds(left + GRID_X + COLS * CELL - 58, top + 16, 58, 14).tooltip(Tooltip.create(Component.translatable(P + "mode.tip"))).build());
        updateButtons();
    }

    private void updateButtons() {
        terminalBtn.active = stock.online() && !accents.isEmpty();
        applicatorBtn.active = stock.linked() && !accents.isEmpty();
    }

    private Component heatLabel() {
        return Component.translatable(LensClient.heatmap() ? P + "heatmap_off" : P + "heatmap_on");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, EDGE);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fill(left, top, left + W, top + 1, LIGHT);
        g.fill(left, top, left + 1, top + H, LIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        BlockPos mn = analysis.min(), mx = analysis.max();
        g.drawString(font, title, left + 8, top + 7, TEXT, false);
        Component dims = Component.translatable(P + "dims", mx.getX() - mn.getX() + 1, mx.getY() - mn.getY() + 1, mx.getZ() - mn.getZ() + 1, analysis.faces());
        g.drawString(font, dims, left + W - 8 - font.width(dims), top + 7, DIM, false);

        // Scores
        for (int i = 0; i < 5; i++) {
            int y = top + 24 + i * 22;
            int s = analysis.scores()[i];
            boolean overall = i == Analysis.OVERALL;
            g.drawString(font, Component.translatable(P + "score." + Analysis.NAMES[i]), left + 8, y, overall ? 0xFFFFFFFF : TEXT, overall);
            g.fill(left + BAR_X, y + 1, left + BAR_X + BAR_W, y + 7, SLOT);
            g.fill(left + BAR_X, y + 1, left + BAR_X + BAR_W * s / 100, y + 7, 0xFF000000 | scoreColour(s));
            g.drawString(font, String.valueOf(s), left + BAR_X + BAR_W + 4, y, TEXT, false);
            Component v = Component.translatable(P + "verdict." + Analysis.NAMES[i] + "." + analysis.verdicts()[i], analysis.verdictArgs()[i]);
            g.drawString(font, v, left + 8, y + 10, DIM, false);
        }

        // Accents
        g.drawString(font, Component.translatable(P + "accents"), left + GRID_X, top + 20, TEXT, false);
        Accents.Accent hover = null;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int x = left + GRID_X + c * CELL, y = top + GRID_Y + r * CELL, idx = r * COLS + c;
                g.fill(x, y, x + 17, y + 17, SLOT);
                if (idx >= accents.size()) continue;
                Accents.Accent a = accents.get(idx);
                if (picked.contains(a.item())) {
                    g.fill(x, y, x + 17, y + 17, 0xFF8FD16A);
                    g.fill(x + 1, y + 1, x + 16, y + 16, SLOT);
                }
                g.renderItem(new ItemStack(a.item()), x + 1, y + 1);
                if (a.amount() >= 0) g.renderItemDecorations(font, new ItemStack(a.item()), x + 1, y + 1, shortCount(a.amount()));
                if (mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17) {
                    hover = a;
                    g.fill(x + 1, y + 1, x + 16, y + 16, 0x60FFFFFF);
                }
            }
        Component src = !stock.linked() ? Component.translatable(P + "unlinked") : !stock.online() ? Component.translatable(P + "offline")
                : Component.translatable(P + "from_me", stock.items().size());
        if (accents.isEmpty()) g.drawString(font, Component.translatable(P + "no_accents"), left + GRID_X, top + GRID_Y + 4, DIM, false);
        drawWrapped(g, src, left + GRID_X, top + GRID_Y + ROWS * CELL + 3, COLS * CELL, DIM);

        // Palette
        g.drawString(font, Component.translatable(P + "palette"), left + 8, PAL_Y + top - 11, TEXT, false);
        int px = left + 8, pw = W - 16, cur = px;
        g.fill(px - 1, top + PAL_Y - 1, px + pw + 1, top + PAL_Y + PAL_H + 1, EDGE);
        Analysis.Cluster hoverCluster = null;
        List<Analysis.Cluster> cl = analysis.clusters();
        for (int i = 0; i < cl.size(); i++) {
            int end = i == cl.size() - 1 ? px + pw : Math.min(px + pw, cur + Math.max(1, Math.round(cl.get(i).share() * pw)));
            if (end <= cur) continue;
            g.fill(cur, top + PAL_Y, end, top + PAL_Y + PAL_H, 0xFF000000 | cl.get(i).rgb());
            if (mouseX >= cur && mouseX < end && mouseY >= top + PAL_Y && mouseY < top + PAL_Y + PAL_H) hoverCluster = cl.get(i);
            cur = end;
            if (cur >= px + pw) break;
        }
        // 60-30-10 guide ticks
        for (float f : new float[]{0.6f, 0.9f}) {
            int x = px + Math.round(f * pw);
            g.fill(x, top + PAL_Y + PAL_H + 1, x + 1, top + PAL_Y + PAL_H + 4, DIM);
        }
        Component hint = Component.translatable(P + "hint", picked.size());
        g.drawString(font, hint, left + 8, top + PAL_Y + PAL_H + 8, DIM, false);

        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hover.item().getDescription());
            if (hover.amount() >= 0) tip.add(Component.translatable(P + "stored", hover.amount()).withStyle(ChatFormatting.GRAY));
            Accents.Kind kind = hover.target().kind();
            int srcRgb = hover.target().sourceRgb();
            int kindColour = kind == Accents.Kind.BRIDGE || com.lach.appliedswatches.client.ColorMath.lab(srcRgb)[0] < 35 ? 0xBBBBBB : srcRgb | 0x202020;
            tip.add(Component.translatable(P + "kind." + kind.name().toLowerCase(), Theme.formatColor(hover.target().sourceRgb()))
                    .withStyle(st -> st.withColor(kindColour)));
            tip.add(Component.translatable(P + "distance", Math.round(hover.dist())).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (hoverCluster != null) {
            g.renderComponentTooltip(font, List.of(Component.literal(Theme.formatColor(hoverCluster.rgb())),
                    Component.translatable(P + "share", Math.round(hoverCluster.share() * 100)).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int w, int colour) {
        for (var line : font.split(text, w)) {
            g.drawString(font, line, x, y, colour, false);
            y += 9;
        }
    }

    private static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 1000) + "k";
        return (n / 1_000_000) + "m";
    }

    private static int scoreColour(int s) {
        return s >= 75 ? 0x6FCF5A : s >= 50 ? 0xE6C84A : 0xE0574A;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int c = (int) Math.floor((mx - left - GRID_X) / CELL), r = (int) Math.floor((my - top - GRID_Y) / CELL);
        if (c >= 0 && c < COLS && r >= 0 && r < ROWS) {
            int idx = r * COLS + c;
            if (idx < accents.size()) {
                Item item = accents.get(idx).item();
                if (!picked.remove(item) && picked.size() < SendPayload.MAX_ITEMS) picked.add(item);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Picked accents, or the best eight when nothing is picked. */
    private List<Item> chosen() {
        if (!picked.isEmpty()) return new ArrayList<>(picked);
        return accents.stream().limit(8).map(Accents.Accent::item).toList();
    }

    private static String scanName() {
        return "Lens " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void send(boolean applicator) {
        List<Item> items = chosen();
        if (items.isEmpty()) return;
        PacketDistributor.sendToServer(new SendPayload(items, applicator, PER_ITEM, scanName()));
        onClose();
    }

    /** Saves the build's main colours plus the accents as an AppliedSwatches theme. */
    private void saveTheme() {
        List<Integer> colours = new ArrayList<>();
        for (Analysis.Cluster c : analysis.clusters()) if (c.share() >= 0.03f && colours.size() < 5) colours.add(c.rgb());
        List<Item> items = chosen();
        List<ResourceLocation> blocks = new ArrayList<>();
        for (Item item : items) {
            blocks.add(BuiltInRegistries.ITEM.getKey(item));
            var info = com.lach.appliedswatches.client.BlockColorCache.get(item);
            if (info != null && colours.size() < Theme.MAX_COLORS && !colours.contains(info.rgb())) colours.add(info.rgb());
        }
        if (colours.isEmpty()) return;
        Theme theme = new Theme(scanName(), colours.stream().mapToInt(Integer::intValue).toArray(), blocks, -1, false);
        Path file = ThemeManager.userDir().resolve(theme.fileName());
        Minecraft mc = Minecraft.getInstance();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, FileDialogs.GSON.toJson(theme.toJson()));
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.theme_saved", theme.name()).withStyle(ChatFormatting.GREEN), false);
        } catch (IOException e) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.theme_failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
        }
        onClose();
    }
}
