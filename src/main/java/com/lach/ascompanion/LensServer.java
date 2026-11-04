package com.lach.ascompanion;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import com.lach.appliedswatches.AppliedSwatches;
import com.lach.appliedswatches.ApplicatorData;
import com.lach.appliedswatches.ApplicatorItem;
import com.lach.appliedswatches.SwatchTerminalBlockEntity;
import com.lach.ascompanion.net.ModulePayload;
import com.lach.ascompanion.net.SendPayload;
import com.lach.ascompanion.net.StockPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server side of the lens: reading the linked terminal's network and sending picks to it. */
public final class LensServer {
    private LensServer() {
    }

    static ItemStack heldLens(ServerPlayer player) {
        for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) if (s.is(AppliedSwatchesCompanion.LENS.get())) return s;
        return ItemStack.EMPTY;
    }

    static @Nullable SwatchTerminalBlockEntity terminal(ServerPlayer player, GlobalPos link) {
        ServerLevel level = player.server.getLevel(link.dimension());
        if (level == null || !level.isLoaded(link.pos())) return null;
        return level.getBlockEntity(link.pos()) instanceof SwatchTerminalBlockEntity be ? be : null;
    }

    static @Nullable IGrid grid(SwatchTerminalBlockEntity be) {
        return be.managedNode().isActive() ? be.managedNode().getGrid() : null;
    }

    public static void sendStock(ServerPlayer player) {
        ItemStack lens = heldLens(player);
        Optional<GlobalPos> link = lens.isEmpty() ? Optional.empty() : SwatchLensItem.data(lens).link();
        List<Item> items = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        SwatchTerminalBlockEntity be = link.map(l -> terminal(player, l)).orElse(null);
        IGrid grid = be == null ? null : grid(be);
        if (grid != null) {
            for (var e : grid.getStorageService().getCachedInventory()) {
                if (items.size() >= StockPayload.MAX) break;
                if (e.getKey() instanceof AEItemKey key && !key.hasComponents() && key.getItem() instanceof BlockItem && e.getLongValue() > 0) {
                    items.add(key.getItem());
                    amounts.add(e.getLongValue());
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new StockPayload(be != null, grid != null, items, amounts.stream().mapToLong(Long::longValue).toArray()));
    }

    public static void send(ServerPlayer player, SendPayload p) {
        ItemStack lens = heldLens(player);
        if (lens.isEmpty()) return;
        Optional<GlobalPos> link = SwatchLensItem.data(lens).link();
        SwatchTerminalBlockEntity be = link.map(l -> terminal(player, l)).orElse(null);
        if (be == null) {
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.no_terminal").withStyle(ChatFormatting.RED), false);
            return;
        }
        List<Item> blocks = p.items().stream().filter(i -> i instanceof BlockItem).distinct().toList();
        if (blocks.isEmpty()) return;
        if (p.toApplicator()) loadApplicator(player, link.get(), blocks, p.name());
        else export(player, be, blocks, Math.max(1, Math.min(64, p.perItem())));
    }

    /** Same as the terminal's Export: pulls up to {@code perItem} of each into the output slots. */
    private static void export(ServerPlayer player, SwatchTerminalBlockEntity be, List<Item> items, int perItem) {
        IGrid grid = grid(be);
        if (grid == null) {
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.offline").withStyle(ChatFormatting.RED), false);
            return;
        }
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        var src = IActionSource.ofPlayer(player, be);
        ItemStackHandler out = be.output();
        int moved = 0;
        for (Item item : items) {
            AEItemKey key = AEItemKey.of(item);
            ItemStack left = ItemHandlerHelper.insertItemStacked(out, key.toStack(perItem), true);
            int fits = perItem - left.getCount();
            if (fits <= 0) continue;
            long got = StorageHelper.poweredExtraction(energy, storage, key, fits, src);
            if (got <= 0) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(out, key.toStack((int) got), false);
            if (!rest.isEmpty()) StorageHelper.poweredInsert(energy, storage, key, rest.getCount(), src);
            moved++;
        }
        player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.exported", moved).withStyle(ChatFormatting.GREEN), false);
    }

    private static void loadApplicator(ServerPlayer player, GlobalPos link, List<Item> blocks, String name) {
        ItemStack target = ItemStack.EMPTY;
        for (ItemStack s : List.of(player.getMainHandItem(), player.getOffhandItem())) if (s.is(AppliedSwatches.APPLICATOR.get())) target = s;
        for (int i = 0; target.isEmpty() && i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(AppliedSwatches.APPLICATOR.get())) target = player.getInventory().getItem(i);
        if (target.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.no_applicator").withStyle(ChatFormatting.RED), false);
            return;
        }
        ApplicatorData old = ApplicatorItem.data(target);
        target.set(AppliedSwatches.APPLICATOR_DATA.get(), new ApplicatorData(blocks, 0, name, Optional.of(link), old.mode(), old.size()));
        player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.applicator_loaded", blocks.size(), name).withStyle(ChatFormatting.GREEN), false);
    }

    /** Moves a module between the player's inventory and the held lens. */
    public static void module(ServerPlayer player, ModulePayload p) {
        ItemStack lens = heldLens(player);
        if (lens.isEmpty()) return;
        List<Module> mods = new ArrayList<>(SwatchLensItem.modules(lens));
        Item item = AppliedSwatchesCompanion.MODULE_ITEMS.get(p.module()).get();
        if (p.install()) {
            if (mods.contains(p.module())) return;
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(item)) continue;
                s.shrink(1);
                mods.add(p.module());
                SwatchLensItem.setModules(lens, mods);
                return;
            }
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.module.missing", item.getDescription()).withStyle(ChatFormatting.RED), true);
        } else if (mods.remove(p.module())) {
            SwatchLensItem.setModules(lens, mods);
            player.getInventory().placeItemBackInInventory(new ItemStack(item));
        }
    }
}
