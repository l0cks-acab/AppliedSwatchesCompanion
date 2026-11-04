package com.lach.ascompanion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared look for the lens screens: an AE2-style grey panel. */
abstract class PanelScreen extends Screen {
    static final int PANEL = 0xFF3C424B, SLOT = 0xFF262A30, EDGE = 0xFF1B1E22, LIGHT = 0xFF5A626D, TEXT = 0xFFE6E6E6, DIM = 0xFF9AA2AD;
    static final String P = "gui.appliedswatches_companion.";
    protected int w, h, left, top;

    protected PanelScreen(Component title, int w, int h) {
        super(title);
        this.w = w;
        this.h = h;
    }

    @Override
    protected void init() {
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, LIGHT);
        g.fill(x, y, x + 1, y + h, LIGHT);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.renderBackground(g, mouseX, mouseY, partial);
        panel(g, left, top, w, h);
    }

    void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int colour) {
        for (var line : font.split(text, width)) {
            g.drawString(font, line, x, y, colour, false);
            y += 9;
        }
    }

    static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 1000) + "k";
        return (n / 1_000_000) + "m";
    }

    /** AE2-style small stack count in the slot's bottom right. */
    static void count(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, String text) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        float sc = 0.666f;
        g.pose().scale(sc, sc, 1);
        g.drawString(font, text, Math.round((x + 16 - font.width(text) * sc) / sc), Math.round((y + 16 - 7 * sc) / sc), 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    /** Draws over items, which render above plain fills. */
    static void fillTop(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x0, y0, x1, y1, colour);
        g.pose().popPose();
    }
}
