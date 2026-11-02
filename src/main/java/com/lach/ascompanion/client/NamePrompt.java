package com.lach.ascompanion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Asks for a name, then hands it on. Escape or Cancel goes back to the screen that opened it. */
public class NamePrompt extends Screen {
    private static final int W = 180, H = 64;
    private final Screen parent;
    private final String initial;
    private final Consumer<String> onSave;
    private EditBox box;
    private Button save;

    public NamePrompt(Screen parent, Component title, String initial, Consumer<String> onSave) {
        super(title);
        this.parent = parent;
        this.initial = initial;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        box = addRenderableWidget(new EditBox(font, x + 8, y + 20, W - 16, 16, title));
        box.setMaxLength(48);
        box.setValue(initial);
        box.setResponder(v -> save.active = !v.isBlank());
        save = addRenderableWidget(Button.builder(Component.translatable("gui.appliedswatches_companion.save"), b -> submit())
                .bounds(x + 8, y + 42, (W - 20) / 2, 16).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + 12 + (W - 20) / 2, y + 42, (W - 20) / 2, 16).build());
        setInitialFocus(box);
    }

    private void submit() {
        String name = box.getValue().trim();
        if (!name.isEmpty()) onSave.accept(name);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        super.renderBackground(g, mx, my, partial);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF15171A);
        g.fill(x, y, x + W, y + H, 0xFF3C424B);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawString(font, title, (width - W) / 2 + 8, (height - H) / 2 + 7, 0xFFE4E8EE, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
