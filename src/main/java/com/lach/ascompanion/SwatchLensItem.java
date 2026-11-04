package com.lach.ascompanion;

import com.lach.appliedswatches.SwatchTerminalBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Sneak + use on a Swatch Terminal links it. Sneak + use on a block sets corner A, use sets corner B.
 * Use in the air scans the region; sneak + use in the air clears the corners and the overlay.
 */
public class SwatchLensItem extends Item {
    public SwatchLensItem(Properties props) {
        super(props);
    }

    public static LensData data(ItemStack stack) {
        return stack.getOrDefault(AppliedSwatchesCompanion.LENS_DATA.get(), LensData.EMPTY);
    }

    public static List<Module> modules(ItemStack stack) {
        return stack.getOrDefault(AppliedSwatchesCompanion.MODULES.get(), List.of());
    }

    public static boolean has(ItemStack stack, Module m) {
        return modules(stack).contains(m);
    }

    /** Stored in panel order. */
    public static void setModules(ItemStack stack, List<Module> mods) {
        List<Module> sorted = new ArrayList<>(mods);
        sorted.sort(null);
        if (sorted.isEmpty()) stack.remove(AppliedSwatchesCompanion.MODULES.get());
        else stack.set(AppliedSwatchesCompanion.MODULES.get(), List.copyOf(sorted));
    }

    /** Right-click a module onto the lens in any inventory to install it. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack lens, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof LensModuleItem mi) || has(lens, mi.module)) return false;
        List<Module> mods = new ArrayList<>(modules(lens));
        mods.add(mi.module);
        setModules(lens, mods);
        other.shrink(1);
        player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8f, 1.2f);
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        LensData d = data(stack);
        if (player.isShiftKeyDown() && level.getBlockState(pos).getBlock() instanceof SwatchTerminalBlock) {
            stack.set(AppliedSwatchesCompanion.LENS_DATA.get(), d.withLink(GlobalPos.of(level.dimension(), pos.immutable())));
            player.sendOverlayMessage(Component.translatable("msg.appliedswatches_companion.linked", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN));
            return InteractionResult.CONSUME;
        }
        LensData next = player.isShiftKeyDown() ? d.withA(pos) : d.withB(pos);
        stack.set(AppliedSwatchesCompanion.LENS_DATA.get(), next);
        String key = player.isShiftKeyDown() ? "msg.appliedswatches_companion.corner_a" : "msg.appliedswatches_companion.corner_b";
        Component msg = Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
        int[] s = next.size();
        if (s != null) {
            msg = msg.copy().append(" ").append(Component.translatable("msg.appliedswatches_companion.size", s[0], s[1], s[2])
                    .withStyle(next.tooBig() ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        player.sendOverlayMessage(msg);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LensData d = data(stack);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) ClientBridge.clear();
            else {
                stack.set(AppliedSwatchesCompanion.LENS_DATA.get(), d.cleared());
                player.sendOverlayMessage(Component.translatable("msg.appliedswatches_companion.cleared"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!d.hasRegion()) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("msg.appliedswatches_companion.no_region").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (d.tooBig()) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("msg.appliedswatches_companion.too_big", LensData.MAX_SIZE).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) ClientBridge.startScan();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> tip, TooltipFlag flag) {
        LensData d = data(stack);
        d.link().ifPresentOrElse(
                l -> tip.accept(Component.translatable("tooltip.appliedswatches_companion.linked", l.pos().getX(), l.pos().getY(), l.pos().getZ()).withStyle(ChatFormatting.GREEN)),
                () -> tip.accept(Component.translatable("tooltip.appliedswatches_companion.unlinked").withStyle(ChatFormatting.GRAY)));
        for (Module m : modules(stack))
            tip.accept(Component.literal("+ ").append(Component.translatable("item.appliedswatches_companion." + m.itemId())).withStyle(st -> st.withColor(m.colour)));
        int[] s = d.size();
        if (s != null) tip.accept(Component.translatable("msg.appliedswatches_companion.size", s[0], s[1], s[2]).withStyle(ChatFormatting.GRAY));
        tip.accept(Component.translatable("tooltip.appliedswatches_companion.help1").withStyle(ChatFormatting.DARK_GRAY));
        tip.accept(Component.translatable("tooltip.appliedswatches_companion.help2").withStyle(ChatFormatting.DARK_GRAY));
        tip.accept(Component.translatable("tooltip.appliedswatches_companion.help3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
