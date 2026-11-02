package com.lach.ascompanion;

import com.lach.appliedswatches.SwatchTerminalBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        LensData d = data(stack);
        if (player.isShiftKeyDown() && level.getBlockState(pos).getBlock() instanceof SwatchTerminalBlock) {
            stack.set(AppliedSwatchesCompanion.LENS_DATA.get(), d.withLink(GlobalPos.of(level.dimension(), pos.immutable())));
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.linked", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN), true);
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
        player.displayClientMessage(msg, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        LensData d = data(stack);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) ClientBridge.clear();
            else {
                stack.set(AppliedSwatchesCompanion.LENS_DATA.get(), d.cleared());
                player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!d.hasRegion()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.no_region").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (d.tooBig()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.too_big", LensData.MAX_SIZE).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) ClientBridge.startScan();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        LensData d = data(stack);
        d.link().ifPresentOrElse(
                l -> tip.add(Component.translatable("tooltip.appliedswatches_companion.linked", l.pos().getX(), l.pos().getY(), l.pos().getZ()).withStyle(ChatFormatting.GREEN)),
                () -> tip.add(Component.translatable("tooltip.appliedswatches_companion.unlinked").withStyle(ChatFormatting.GRAY)));
        int[] s = d.size();
        if (s != null) tip.add(Component.translatable("msg.appliedswatches_companion.size", s[0], s[1], s[2]).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.appliedswatches_companion.help1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("tooltip.appliedswatches_companion.help2").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("tooltip.appliedswatches_companion.help3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
