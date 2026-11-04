package com.lach.ascompanion;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A card that adds a feature to the Swatch Lens. Right-click it onto a lens, or install it from the Lens screen. */
public class LensModuleItem extends Item {
    public final Module module;

    public LensModuleItem(Module module, Properties props) {
        super(props);
        this.module = module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.appliedswatches_companion.module." + module.getSerializedName()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.appliedswatches_companion.module.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
