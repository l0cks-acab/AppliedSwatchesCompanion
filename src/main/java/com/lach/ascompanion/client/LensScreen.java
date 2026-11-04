package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.FileDialogs;
import com.lach.appliedswatches.client.Theme;
import com.lach.appliedswatches.client.ThemeManager;
import com.lach.ascompanion.AppliedSwatchesCompanion;
import com.lach.ascompanion.Module;
import com.lach.ascompanion.SwatchLensItem;
import com.lach.ascompanion.net.ModulePayload;
import com.lach.ascompanion.net.SendPayload;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
public class LensScreen extends PanelScreen {
    private static final int W = 300, BASE_H = 226, ROW = 22;
    private static final int GRID_X = 186, GRID_Y = 32, COLS = 6, ROWS = 5, CELL = 18;
    private static final int BAR_X = 72, BAR_W = 84;
    private static final int PAL_H = 12;
    private static final int PER_ITEM = 64;
    /** Module panel, to the right of the main panel like AE2's upgrade slots. */
    private static final int MOD_W = 24;

    private final Analysis analysis;
    private final StockPayload stock;
    /** Kept between scans. */
    private static Accents.Mode mode = Accents.Mode.HARMONY;
    private List<Accents.Accent> accents;
    private final Set<Item> picked = new LinkedHashSet<>();
    private List<Module> modules = List.of();
    private boolean sur, actionRow;
    private int palY, rowH;
    private Button terminalBtn, applicatorBtn;

    public LensScreen(Analysis analysis, StockPayload stock) {
        super(Component.translatable(P + "title"), W, BASE_H);
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

    private List<Module> heldModules() {
        ItemStack lens = LensClient.heldLens();
        return lens.isEmpty() ? List.of() : SwatchLensItem.modules(lens);
    }

    @Override
    public void tick() {
        if (!heldModules().equals(modules)) rebuildWidgets();
    }

    @Override
    protected void init() {
        modules = heldModules();
        sur = modules.contains(Module.SURROUNDINGS);
        List<Module> actions = modules.stream().filter(m -> m != Module.SURROUNDINGS).toList();
        actionRow = !actions.isEmpty();
        rowH = sur ? 20 : ROW;
        palY = sur ? 158 : 152;
        super.init();
        left = (width - W - MOD_W - 3) / 2;
        int y = top + h - 26, bw = 68, gap = 4, x = left + 8;
        for (int i = 0; i < actions.size(); i++) {
            Module m = actions.get(i);
            Component label = Component.translatable(P + "module." + m.getSerializedName() + (m == Module.FOCAL && LensClient.focal() ? ".off" : ""));
            addRenderableWidget(Button.builder(label, b -> moduleAction(m)).bounds(x + i * (92 + gap), y - 20, 92, 16)
                    .tooltip(Tooltip.create(Component.translatable("tooltip.appliedswatches_companion.module." + m.getSerializedName()))).build());
        }
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

    private void moduleAction(Module m) {
        switch (m) {
            case RECOLOUR -> minecraft.setScreen(new RecolourScreen(this, analysis, stock));
            case GRADIENT -> minecraft.setScreen(new GradientScreen(this, analysis, stock));
            case FOCAL -> {
                int n = LensClient.setFocal(!LensClient.focal());
                if (LensClient.focal() && minecraft.player != null)
                    minecraft.player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.focal", n).withStyle(ChatFormatting.AQUA), true);
                onClose();
            }
            default -> {
            }
        }
    }

    private void updateButtons() {
        terminalBtn.active = stock.online() && !accents.isEmpty();
        applicatorBtn.active = stock.linked() && !accents.isEmpty();
    }

    private Component heatLabel() {
        return Component.translatable(LensClient.heatmap() ? P + "heatmap_off" : P + "heatmap_on");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        panel(g, modX(), top + 4, MOD_W, Module.values().length * 18 + 6);
    }

    private int modX() {
        return left + W + 3;
    }

    /** Module slot under the mouse, or null. */
    private Module moduleAt(double mx, double my) {
        int x = modX() + 3;
        if (mx < x || mx >= x + 18) return null;
        int i = (int) Math.floor((my - top - 7) / 18);
        return i >= 0 && i < Module.values().length ? Module.values()[i] : null;
    }

    private void renderModules(GuiGraphics g, int mouseX, int mouseY) {
        int x = modX() + 3;
        for (Module m : Module.values()) {
            int y = top + 7 + m.ordinal() * 18;
            g.fill(x, y, x + 17, y + 17, SLOT);
            g.renderItem(new ItemStack(AppliedSwatchesCompanion.MODULE_ITEMS.get(m).get()), x + 1, y + 1);
            if (!modules.contains(m)) fillTop(g, x + 1, y + 1, x + 16, y + 16, 0xC0262A30);
            if (mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17) fillTop(g, x + 1, y + 1, x + 16, y + 16, 0x40FFFFFF);
        }
    }

    /** Rows of scores: the four, surroundings when installed, then overall. */
    private record Row(String name, int score, Component verdict) {
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < Analysis.OVERALL; i++) {
            int s = analysis.scores()[i];
            sum += s;
            out.add(new Row(Analysis.NAMES[i], s, Component.translatable(P + "verdict." + Analysis.NAMES[i] + "." + analysis.verdicts()[i], analysis.verdictArgs()[i])));
        }
        int n = Analysis.OVERALL;
        if (sur) {
            Surroundings.Result r = LensClient.surroundings();
            if (r != null && r.score() >= 0) {
                sum += r.score();
                n++;
            }
            out.add(new Row("surroundings", r == null ? 0 : Math.max(0, r.score()), Component.translatable(P + "verdict.surroundings." + (r == null ? "none" : r.verdict()))));
        }
        int o = Math.round(sum / (float) n);
        String v = o >= 80 ? "great" : o >= 60 ? "solid" : o >= 40 ? "rough" : "messy";
        if (analysis.faces() == 0) v = "empty";
        out.add(new Row("overall", o, Component.translatable(P + "verdict.overall." + v)));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        BlockPos mn = analysis.min(), mx = analysis.max();
        g.drawString(font, title, left + 8, top + 7, TEXT, false);
        Component dims = Component.translatable(P + "dims", mx.getX() - mn.getX() + 1, mx.getY() - mn.getY() + 1, mx.getZ() - mn.getZ() + 1, analysis.faces());
        g.drawString(font, dims, left + W - 8 - font.width(dims), top + 7, DIM, false);

        // Scores
        List<Row> rows = rows();
        boolean hoverTerrain = false;
        for (int i = 0; i < rows.size(); i++) {
            int y = top + 24 + i * rowH;
            Row row = rows.get(i);
            int s = row.score();
            boolean overall = i == rows.size() - 1;
            g.drawString(font, Component.translatable(P + "score." + row.name()), left + 8, y, overall ? 0xFFFFFFFF : TEXT, overall);
            g.fill(left + BAR_X, y + 1, left + BAR_X + BAR_W, y + 7, SLOT);
            g.fill(left + BAR_X, y + 1, left + BAR_X + BAR_W * s / 100, y + 7, 0xFF000000 | scoreColour(s));
            g.drawString(font, String.valueOf(s), left + BAR_X + BAR_W + 4, y, TEXT, false);
            g.drawString(font, row.verdict(), left + 8, y + 10, DIM, false);
            if (row.name().equals("surroundings")) {
                Surroundings.Result r = LensClient.surroundings();
                if (r != null) {
                    int sx = left + 8 + font.width(row.verdict()) + 4;
                    for (Analysis.Cluster c : r.terrain()) {
                        g.fill(sx, y + 10, sx + 7, y + 17, 0xFF000000 | c.rgb());
                        sx += 8;
                    }
                }
                hoverTerrain = mouseX >= left + 8 && mouseX < left + GRID_X - 4 && mouseY >= y && mouseY < y + rowH;
            }
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
                if (a.amount() >= 0) count(g, font, x + 1, y + 1, shortCount(a.amount()));
                if (mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17) {
                    hover = a;
                    g.fill(x + 1, y + 1, x + 16, y + 16, 0x60FFFFFF);
                }
            }
        Component src = !stock.linked() ? Component.translatable(P + "unlinked") : !stock.online() ? Component.translatable(P + "offline")
                : Component.translatable(P + "from_me", stock.items().size());
        if (accents.isEmpty()) g.drawString(font, Component.translatable(P + "no_accents"), left + GRID_X, top + GRID_Y + 4, DIM, false);
        drawWrapped(g, src, left + GRID_X, top + GRID_Y + ROWS * CELL + 3, COLS * CELL, DIM);
        renderModules(g, mouseX, mouseY);
        Module hoverModule = moduleAt(mouseX, mouseY);

        // Palette
        g.drawString(font, Component.translatable(P + "palette"), left + 8, top + palY - 11, TEXT, false);
        int px = left + 8, pw = W - 16, cur = px;
        g.fill(px - 1, top + palY - 1, px + pw + 1, top + palY + PAL_H + 1, EDGE);
        Analysis.Cluster hoverCluster = null;
        List<Analysis.Cluster> cl = analysis.clusters();
        for (int i = 0; i < cl.size(); i++) {
            int end = i == cl.size() - 1 ? px + pw : Math.min(px + pw, cur + Math.max(1, Math.round(cl.get(i).share() * pw)));
            if (end <= cur) continue;
            g.fill(cur, top + palY, end, top + palY + PAL_H, 0xFF000000 | cl.get(i).rgb());
            if (mouseX >= cur && mouseX < end && mouseY >= top + palY && mouseY < top + palY + PAL_H) hoverCluster = cl.get(i);
            cur = end;
            if (cur >= px + pw) break;
        }
        // 60-30-10 guide ticks
        for (float f : new float[]{0.6f, 0.9f}) {
            int x = px + Math.round(f * pw);
            g.fill(x, top + palY + PAL_H + 1, x + 1, top + palY + PAL_H + 4, DIM);
        }
        if (!actionRow) g.drawString(font, Component.translatable(P + "hint", picked.size()), left + 8, top + palY + PAL_H + 8, DIM, false);

        if (hoverModule != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("item.appliedswatches_companion." + hoverModule.itemId()).withStyle(st -> st.withColor(hoverModule.colour)));
            tip.add(Component.translatable("tooltip.appliedswatches_companion.module." + hoverModule.getSerializedName()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable(P + (modules.contains(hoverModule) ? "module.remove" : "module.install")).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (hoverTerrain) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(P + "terrain", Surroundings.RING));
            Surroundings.Result r = LensClient.surroundings();
            if (r != null) for (Analysis.Cluster c : r.terrain())
                tip.add(Component.literal("\u25A0 ").withStyle(st -> st.withColor(c.rgb()))
                        .append(Component.literal(Theme.formatColor(c.rgb()) + "  " + Math.round(c.share() * 100) + "%").withStyle(ChatFormatting.GRAY)));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hover.item().getDescription());
            if (hover.amount() >= 0) tip.add(Component.translatable(P + "stored", hover.amount()).withStyle(ChatFormatting.GRAY));
            Accents.Kind kind = hover.target().kind();
            int srcRgb = hover.target().sourceRgb();
            int kindColour = kind == Accents.Kind.BRIDGE || com.lach.appliedswatches.client.ColorMath.lab(srcRgb)[0] < 35 ? 0xBBBBBB : srcRgb | 0x202020;
            tip.add(Component.translatable(P + "kind." + kind.name().toLowerCase(), Theme.formatColor(hover.target().sourceRgb()))
                    .withStyle(st -> st.withColor(kindColour)));
            tip.add(Component.translatable(P + "distance", Math.round(hover.dist())).withStyle(ChatFormatting.DARK_GRAY));
            if (actionRow) tip.add(Component.translatable(P + "hint", picked.size()).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (hoverCluster != null) {
            g.renderComponentTooltip(font, List.of(Component.literal(Theme.formatColor(hoverCluster.rgb())),
                    Component.translatable(P + "share", Math.round(hoverCluster.share() * 100)).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private static int scoreColour(int s) {
        return s >= 75 ? 0x6FCF5A : s >= 50 ? 0xE6C84A : 0xE0574A;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Module m = moduleAt(mx, my);
        if (m != null) {
            boolean installed = modules.contains(m);
            if (!installed && minecraft.player != null && !minecraft.player.getInventory().hasAnyMatching(s -> s.is(AppliedSwatchesCompanion.MODULE_ITEMS.get(m).get()))) {
                minecraft.player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.module.missing",
                        Component.translatable("item.appliedswatches_companion." + m.itemId())).withStyle(ChatFormatting.RED), true);
                return true;
            }
            PacketDistributor.sendToServer(new ModulePayload(m, !installed));
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }
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

    /** Asks for a name, then saves the build's main colours plus the accents as an AppliedSwatches theme. */
    private void saveTheme() {
        minecraft.setScreen(new NamePrompt(this, Component.translatable(P + "theme_name"), scanName(), this::saveTheme));
    }

    private void saveTheme(String name) {
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
        Theme theme = new Theme(name, colours.stream().mapToInt(Integer::intValue).toArray(), blocks, -1, false);
        Path file = ThemeManager.userDir().resolve(theme.fileName());
        for (int i = 2; Files.exists(file); i++) file = ThemeManager.userDir().resolve(theme.fileName().replace(".json", "_" + i + ".json"));
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
