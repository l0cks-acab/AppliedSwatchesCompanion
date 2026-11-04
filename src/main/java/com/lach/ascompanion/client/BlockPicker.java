package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.BlockColorCache;
import com.lach.ascompanion.Variants;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** A scrolling, searchable grid of full blocks, sorted round the colour wheel: greys first, then by hue. */
final class BlockPicker {
    static final int CELL = 18;
    final List<Item> all = new ArrayList<>();
    final Map<Item, Long> amounts = new HashMap<>();
    private List<Item> shown;
    final int cols, rows;
    int x, y;
    private int scroll;

    BlockPicker(StockPayload stock, int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        boolean me = stock.online();
        List<Item> items = me ? stock.items() : BuiltInRegistries.ITEM.stream().filter(i -> i instanceof BlockItem).toList();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof BlockItem bi) || bi.getBlock() instanceof EntityBlock || Variants.shape(bi.getBlock()) != null) continue;
            BlockColorCache.Info info = BlockColorCache.get(bi);
            if (info == null || !info.full()) continue;
            all.add(bi);
            if (me) amounts.put(bi, stock.amounts()[i]);
        }
        all.sort(Comparator.comparingDouble(BlockPicker::wheel));
        shown = all;
    }

    /** Neutrals by lightness, then 15 degree hue bands, each dark to light. */
    private static double wheel(Item item) {
        float[] lab = BlockColorCache.get(item).lab();
        double c = Math.hypot(lab[1], lab[2]);
        if (c < 12) return lab[0];
        double hue = (Math.toDegrees(Math.atan2(lab[2], lab[1])) + 360 + 330) % 360;
        return 1000 + Math.floor(hue / 15) * 1000 + lab[0];
    }

    void filter(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        shown = s.isEmpty() ? all : all.stream().filter(i -> i.getDescription().getString().toLowerCase(Locale.ROOT).contains(s)
                || BuiltInRegistries.ITEM.getKey(i).getPath().contains(s)).toList();
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + cols - 1) / cols - rows);
    }

    boolean scroll(double mx, double my, double dy) {
        if (!inside(mx, my)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
        return true;
    }

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + cols * CELL + 4 && my >= y && my < y + rows * CELL;
    }

    Item at(double mx, double my) {
        if (!inside(mx, my) || mx >= x + cols * CELL) return null;
        int idx = (scroll + (int) ((my - y) / CELL)) * cols + (int) ((mx - x) / CELL);
        return idx >= 0 && idx < shown.size() ? shown.get(idx) : null;
    }

    /** Draws the grid. {@code mark} gives an outline colour for marked items, or null. Returns the hovered item. */
    Item render(GuiGraphics g, Font font, int mouseX, int mouseY, Function<Item, Integer> mark) {
        Item hover = null;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int cx = x + c * CELL, cy = y + r * CELL, idx = (scroll + r) * cols + c;
                g.fill(cx, cy, cx + 17, cy + 17, PanelScreen.SLOT);
                if (idx >= shown.size()) continue;
                Item item = shown.get(idx);
                Integer m = mark.apply(item);
                if (m != null) {
                    g.fill(cx, cy, cx + 17, cy + 17, 0xFF000000 | m);
                    g.fill(cx + 1, cy + 1, cx + 16, cy + 16, PanelScreen.SLOT);
                }
                ItemStack st = new ItemStack(item);
                g.renderItem(st, cx + 1, cy + 1);
                Long amt = amounts.get(item);
                if (amt != null) PanelScreen.count(g, font, cx + 1, cy + 1, PanelScreen.shortCount(amt));
                if (mouseX >= cx && mouseX < cx + 17 && mouseY >= cy && mouseY < cy + 17) {
                    hover = item;
                    PanelScreen.fillTop(g, cx + 1, cy + 1, cx + 16, cy + 16, 0x60FFFFFF);
                }
            }
        int max = maxScroll(), bx = x + cols * CELL + 1, bh = rows * CELL - 1;
        g.fill(bx, y, bx + 3, y + bh, PanelScreen.SLOT);
        int th = max == 0 ? bh : Math.max(8, bh * rows / (rows + max));
        int ty = max == 0 ? 0 : (bh - th) * scroll / max;
        g.fill(bx, y + ty, bx + 3, y + ty + th, PanelScreen.LIGHT);
        return hover;
    }
}
