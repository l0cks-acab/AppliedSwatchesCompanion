package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.BlockColorCache;
import com.lach.appliedswatches.client.Theme;
import com.lach.ascompanion.net.SendPayload;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gradient module: pick two blocks and a step count, and the steps between are filled with the closest blocks to
 * evenly spaced CIELAB colours between them.
 */
public class GradientScreen extends PanelScreen {
    private static final int COLS = 15, MIN_STEPS = 3, MAX_STEPS = 9, PER_ITEM = 64;
    private static int steps = 5;
    private final LensScreen parent;
    private final StockPayload stock;
    private final BlockPicker picker;
    private Item start, end;
    private List<Item> ramp = List.of();
    private EditBox search;
    private Button terminalBtn, applicatorBtn;

    public GradientScreen(LensScreen parent, Analysis analysis, StockPayload stock) {
        super(Component.translatable(P + "gradient"), 300, 226);
        this.parent = parent;
        this.stock = stock;
        picker = new BlockPicker(stock, COLS, 5);
        // Start from the darkest and lightest main colours of the build.
        Analysis.Cluster dark = null, light = null;
        for (Analysis.Cluster c : analysis.clusters()) {
            if (c.share() < 0.03f) continue;
            if (dark == null || c.lab()[0] < dark.lab()[0]) dark = c;
            if (light == null || c.lab()[0] > light.lab()[0]) light = c;
        }
        if (dark != null && dark != light) {
            start = nearest(dark.lab(), Set.of());
            end = nearest(light.lab(), start == null ? Set.of() : Set.of(start));
        }
        rebuild();
    }

    private Item nearest(float[] lab, Set<Item> skip) {
        Item best = null;
        float bestD = Float.MAX_VALUE;
        for (Item i : picker.all) {
            if (skip.contains(i)) continue;
            float d = Accents.cie94(lab, BlockColorCache.get(i).lab());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private void rebuild() {
        if (start == null || end == null || start == end) {
            ramp = List.of();
            return;
        }
        float[] a = BlockColorCache.get(start).lab(), b = BlockColorCache.get(end).lab();
        List<Item> out = new ArrayList<>();
        Set<Item> used = new HashSet<>(List.of(start, end));
        out.add(start);
        for (int k = 1; k < steps - 1; k++) {
            float t = k / (float) (steps - 1);
            float[] lab = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
            Item n = nearest(lab, used);
            if (n == null) break;
            used.add(n);
            out.add(n);
        }
        out.add(end);
        ramp = out;
    }

    @Override
    protected void init() {
        super.init();
        picker.x = left + 8;
        picker.y = top + 34;
        String q = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + w - 8 - 110, top + 19, 110, 12, Component.translatable(P + "search")));
        search.setHint(Component.translatable(P + "search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(q);
        search.setResponder(picker::filter);
        int sy = top + 34 + 5 * BlockPicker.CELL + 5;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setSteps(steps - 1)).bounds(left + 48, sy, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setSteps(steps + 1)).bounds(left + 80, sy, 14, 14).build());
        int y = top + h - 26, bw = 90;
        terminalBtn = addRenderableWidget(Button.builder(Component.translatable(P + "to_terminal"), b -> send(false)).bounds(left + 8, y, bw, 18)
                .tooltip(Tooltip.create(Component.translatable(P + "to_terminal.tip", PER_ITEM))).build());
        applicatorBtn = addRenderableWidget(Button.builder(Component.translatable(P + "to_applicator"), b -> send(true)).bounds(left + 8 + bw + 5, y, bw, 18)
                .tooltip(Tooltip.create(Component.translatable(P + "gradient.applicator.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable(P + "back"), b -> minecraft.setScreen(parent)).bounds(left + 8 + 2 * (bw + 5), y, bw, 18).build());
        update();
    }

    private void setSteps(int n) {
        steps = Math.max(MIN_STEPS, Math.min(MAX_STEPS, n));
        rebuild();
        update();
    }

    private void update() {
        terminalBtn.active = stock.online() && !ramp.isEmpty();
        applicatorBtn.active = stock.linked() && !ramp.isEmpty();
    }

    private void send(boolean applicator) {
        if (ramp.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new SendPayload(ramp, applicator, PER_ITEM, "Gradient " + ramp.size()));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.text(font, title, left + 8, top + 7, TEXT, false);
        g.text(font, Component.translatable(P + "gradient.hint"), left + 8, top + 22, DIM, false);
        Item hover = picker.render(g, font, mouseX, mouseY, i -> i == start ? Integer.valueOf(0x6FA8FF) : i == end ? Integer.valueOf(0xF0C850) : null);
        int sy = top + 34 + 5 * BlockPicker.CELL + 5;
        g.text(font, Component.translatable(P + "gradient.steps"), left + 8, sy + 3, TEXT, false);
        String n = String.valueOf(steps);
        g.text(font, n, left + 71 - font.width(n) / 2, sy + 3, TEXT, false);

        // The ramp: blocks with their colour underneath.
        int rx = left + 104, ry = sy - 2;
        for (int i = 0; i < steps; i++) {
            int x = rx + i * 20;
            g.fill(x, ry, x + 17, ry + 17, SLOT);
            if (i >= ramp.size()) continue;
            Item item = ramp.get(i);
            g.item(new ItemStack(item), x + 1, ry + 1);
            g.fill(x, ry + 19, x + 17, ry + 23, 0xFF000000 | BlockColorCache.get(item).rgb());
            if (mouseX >= x && mouseX < x + 17 && mouseY >= ry && mouseY < ry + 17) hover = item;
        }
        if (ramp.isEmpty()) g.text(font, Component.translatable(P + "gradient.pick"), rx, ry + 4, DIM, false);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hover.getName(hover.getDefaultInstance()));
            Long amt = picker.amounts.get(hover);
            if (amt != null) tip.add(Component.translatable(P + "stored", amt).withStyle(ChatFormatting.GRAY));
            int rgb = BlockColorCache.get(hover).rgb();
            tip.add(Component.literal(Theme.formatColor(rgb)).withStyle(st -> st.withColor(rgb)));
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        Item p = picker.at(mx, my);
        if (p != null) {
            if (button == 1) end = p;
            else start = p;
            rebuild();
            update();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return picker.scroll(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
