package com.lach.ascompanion.client;

import com.lach.appliedswatches.client.BlockColorCache;
import com.lach.ascompanion.Variants;
import com.lach.ascompanion.net.RecolourPayload;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recolour module: pick a block from the build and a replacement from ME. The server does the swap. */
public class RecolourScreen extends PanelScreen {
    private static final int COLS = 15;
    private final LensScreen parent;
    private final StockPayload stock;
    /** Full blocks in the build (stairs, slabs and walls folded into their block), with visible counts. */
    private final List<Map.Entry<Item, Integer>> sources = new ArrayList<>();
    private final BlockPicker picker;
    private Item from, to;
    private EditBox search;
    private Button go;

    public RecolourScreen(LensScreen parent, Analysis analysis, StockPayload stock) {
        super(Component.translatable(P + "recolour"), 300, 226);
        this.parent = parent;
        this.stock = stock;
        Map<Item, Integer> m = new LinkedHashMap<>();
        analysis.counts().forEach((b, n) -> {
            Block base = Variants.base(b);
            Item item = base.asItem();
            if (item == Items.AIR || base instanceof EntityBlock) return;
            m.merge(item, n, Integer::sum);
        });
        sources.addAll(m.entrySet());
        sources.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        if (sources.size() > COLS * 2) sources.subList(COLS * 2, sources.size()).clear();
        picker = new BlockPicker(stock, COLS, 4);
    }

    @Override
    protected void init() {
        super.init();
        picker.x = left + 8;
        picker.y = top + 92;
        String q = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + w - 8 - 110, top + 78, 110, 12, Component.translatable(P + "search")));
        search.setHint(Component.translatable(P + "search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(q);
        search.setResponder(picker::filter);
        int y = top + h - 26, bw = 90;
        go = addRenderableWidget(Button.builder(Component.translatable(P + "recolour.go"), b -> {
            PacketDistributor.sendToServer(new RecolourPayload(from, to, false));
            LensClient.clear();
            onClose();
        }).bounds(left + 8, y, bw, 18).tooltip(Tooltip.create(Component.translatable(P + "recolour.go.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable(P + "recolour.undo"), b -> {
            PacketDistributor.sendToServer(new RecolourPayload(Items.AIR, Items.AIR, true));
            LensClient.clear();
            onClose();
        }).bounds(left + 8 + bw + 5, y, bw, 18).tooltip(Tooltip.create(Component.translatable(P + "recolour.undo.tip"))).build()).active = stock.online();
        addRenderableWidget(Button.builder(Component.translatable(P + "back"), b -> minecraft.setScreen(parent)).bounds(left + 8 + 2 * (bw + 5), y, bw, 18).build());
        update();
    }

    private void update() {
        go.active = stock.online() && from != null && to != null && from != to;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawString(font, title, left + 8, top + 7, TEXT, false);
        g.drawString(font, Component.translatable(P + "recolour.replace"), left + 8, top + 20, TEXT, false);
        Item hover = null;
        for (int i = 0; i < COLS * 2; i++) {
            int x = left + 8 + (i % COLS) * BlockPicker.CELL, y = top + 30 + (i / COLS) * BlockPicker.CELL;
            g.fill(x, y, x + 17, y + 17, SLOT);
            if (i >= sources.size()) continue;
            Item item = sources.get(i).getKey();
            if (item == from) {
                g.fill(x, y, x + 17, y + 17, 0xFFE0574A);
                g.fill(x + 1, y + 1, x + 16, y + 16, SLOT);
            }
            ItemStack st = new ItemStack(item);
            g.renderItem(st, x + 1, y + 1);
            count(g, font, x + 1, y + 1, shortCount(sources.get(i).getValue()));
            if (mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17) {
                hover = item;
                fillTop(g, x + 1, y + 1, x + 16, y + 16, 0x60FFFFFF);
            }
        }
        g.drawString(font, Component.translatable(P + "recolour.with"), left + 8, top + 80, TEXT, false);
        Item ph = picker.render(g, font, mouseX, mouseY, i -> i == to ? 0x8FD16A : null);
        if (ph != null) hover = ph;

        int y = top + 92 + 4 * BlockPicker.CELL + 6;
        if (!stock.online()) {
            drawWrapped(g, Component.translatable(P + "recolour.need_me"), left + 8, y, w - 16, 0xFFE0574A);
        } else if (from == null || to == null) {
            g.drawString(font, Component.translatable(P + "recolour.pick"), left + 8, y, DIM, false);
        } else {
            g.renderItem(new ItemStack(from), left + 8, y - 4);
            g.drawString(font, "→", left + 30, y, TEXT, false);
            g.renderItem(new ItemStack(to), left + 42, y - 4);
            Component c = Component.translatable(P + "recolour.summary", from.getDescription(), to.getDescription());
            g.drawString(font, font.plainSubstrByWidth(c.getString(), w - 80), left + 64, y, DIM, false);
        }
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hover.getDescription());
            Long amt = picker.amounts.get(hover);
            if (amt != null) tip.add(Component.translatable(P + "stored", amt).withStyle(ChatFormatting.GRAY));
            var info = BlockColorCache.get(hover);
            if (info != null) tip.add(Component.literal(com.lach.appliedswatches.client.Theme.formatColor(info.rgb())).withStyle(st -> st.withColor(info.rgb())));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int c = (int) Math.floor((mx - left - 8) / BlockPicker.CELL), r = (int) Math.floor((my - top - 30) / BlockPicker.CELL);
        if (c >= 0 && c < COLS && r >= 0 && r < 2 && r * COLS + c < sources.size()) {
            from = sources.get(r * COLS + c).getKey();
            update();
            return true;
        }
        Item p = picker.at(mx, my);
        if (p != null) {
            to = p;
            update();
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
