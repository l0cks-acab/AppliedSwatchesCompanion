package com.lach.ascompanion;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import com.lach.appliedswatches.SwatchTerminalBlockEntity;
import com.lach.ascompanion.net.RecolourPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recolour module: swaps one block type (with its stairs, slabs and walls) across the lens region. New blocks come
 * out of the linked ME network and the old ones go back in. The last swap per player can be undone once.
 */
public final class Recolour {
    private record Change(BlockPos pos, BlockState before, BlockState after) {
    }

    private record Undo(ResourceKey<Level> dimension, List<Change> changes) {
    }

    private static final Map<UUID, Undo> UNDO = new HashMap<>();

    private Recolour() {
    }

    public static void handle(ServerPlayer player, RecolourPayload p) {
        ItemStack lens = LensServer.heldLens(player);
        if (lens.isEmpty() || !SwatchLensItem.has(lens, Module.RECOLOUR)) return;
        LensData d = SwatchLensItem.data(lens);
        Optional<GlobalPos> link = d.link();
        SwatchTerminalBlockEntity be = link.map(l -> LensServer.terminal(player, l)).orElse(null);
        if (be == null) {
            fail(player, "msg.appliedswatches_companion.no_terminal");
            return;
        }
        IGrid grid = LensServer.grid(be);
        if (grid == null) {
            fail(player, "msg.appliedswatches_companion.offline");
            return;
        }
        ServerLevel level = player.serverLevel();
        if (p.undo()) {
            Undo u = UNDO.get(player.getUUID());
            if (u == null || u.dimension() != level.dimension()) {
                fail(player, "msg.appliedswatches_companion.recolour.no_undo");
                return;
            }
            int n = move(player, level, grid, be, u.changes(), false);
            if (n >= 0) {
                UNDO.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.recolour.undone", n).withStyle(ChatFormatting.GREEN), false);
            }
            return;
        }
        if (!d.hasRegion() || d.tooBig()) {
            fail(player, "msg.appliedswatches_companion.no_region");
            return;
        }
        if (!(p.from() instanceof BlockItem fi) || !(p.to() instanceof BlockItem ti)) return;
        Block from = Variants.base(fi.getBlock()), to = ti.getBlock();
        if (from == to || to instanceof EntityBlock || Variants.shape(to) != null) return;
        Map<Block, Block> map = new LinkedHashMap<>();
        map.put(from, to);
        for (Variants.Shape s : Variants.Shape.values()) {
            Block fv = Variants.variant(from, s), tv = Variants.variant(to, s);
            if (fv != null && tv != null) map.put(fv, tv);
        }
        List<Change> changes = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(d.a().get(), d.b().get())) {
            BlockState st = level.getBlockState(pos);
            Block target = map.get(st.getBlock());
            if (target == null || st.hasBlockEntity() || !level.mayInteract(player, pos)) continue;
            changes.add(new Change(pos.immutable(), st, copy(st, target)));
        }
        // Stairs, slabs and walls the network can't cover stay as they are.
        var storage = grid.getStorageService().getInventory();
        var src = IActionSource.ofPlayer(player, be);
        Map<Block, Long> need = new HashMap<>();
        for (Change c : changes) if (c.after().getBlock() != to) need.merge(c.after().getBlock(), (long) items(c.after()), Long::sum);
        List<Block> skip = new ArrayList<>();
        need.forEach((b, amt) -> {
            if (storage.extract(AEItemKey.of(b.asItem()), amt, Actionable.SIMULATE, src) < amt) skip.add(b);
        });
        int kept = 0;
        if (!skip.isEmpty()) {
            int before = changes.size();
            changes.removeIf(c -> skip.contains(c.after().getBlock()));
            kept = before - changes.size();
        }
        if (changes.isEmpty()) {
            fail(player, "msg.appliedswatches_companion.recolour.nothing");
            return;
        }
        int n = move(player, level, grid, be, changes, true);
        if (n >= 0) {
            UNDO.put(player.getUUID(), new Undo(level.dimension(), changes));
            player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.recolour.done", n,
                    from.getName(), to.getName()).withStyle(ChatFormatting.GREEN), false);
            if (kept > 0) player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.recolour.kept", kept).withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** Applies the changes forwards or backwards, paying from ME. Returns how many blocks changed, or -1 when nothing could. */
    private static int move(ServerPlayer player, ServerLevel level, IGrid grid, SwatchTerminalBlockEntity be, List<Change> changes, boolean forward) {
        List<Change> todo = new ArrayList<>();
        Map<Item, Long> need = new LinkedHashMap<>(), give = new LinkedHashMap<>();
        for (Change c : changes) {
            BlockState was = forward ? c.before() : c.after(), next = forward ? c.after() : c.before();
            if (level.getBlockState(c.pos()) != was) continue;
            todo.add(c);
            need.merge(next.getBlock().asItem(), (long) items(next), Long::sum);
            give.merge(was.getBlock().asItem(), (long) items(was), Long::sum);
        }
        if (todo.isEmpty()) {
            fail(player, "msg.appliedswatches_companion.recolour.nothing");
            return -1;
        }
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        var src = IActionSource.ofPlayer(player, be);
        for (var e : need.entrySet()) {
            long have = storage.extract(AEItemKey.of(e.getKey()), e.getValue(), Actionable.SIMULATE, src);
            if (have < e.getValue()) {
                player.displayClientMessage(Component.translatable("msg.appliedswatches_companion.recolour.short",
                        e.getValue(), e.getKey().getDescription(), have).withStyle(ChatFormatting.RED), false);
                return -1;
            }
        }
        Map<Item, Long> taken = new HashMap<>();
        for (var e : need.entrySet()) {
            long got = StorageHelper.poweredExtraction(energy, storage, AEItemKey.of(e.getKey()), e.getValue(), src);
            taken.put(e.getKey(), got);
            if (got < e.getValue()) {
                taken.forEach((item, amt) -> {
                    if (amt > 0) StorageHelper.poweredInsert(energy, storage, AEItemKey.of(item), amt, src);
                });
                fail(player, "msg.appliedswatches_companion.recolour.power");
                return -1;
            }
        }
        for (Change c : todo) level.setBlock(c.pos(), forward ? c.after() : c.before(), Block.UPDATE_ALL);
        for (var e : give.entrySet()) {
            if (e.getKey() == net.minecraft.world.item.Items.AIR) continue;
            long left = e.getValue() - StorageHelper.poweredInsert(energy, storage, AEItemKey.of(e.getKey()), e.getValue(), src);
            while (left > 0) {
                int n = (int) Math.min(left, e.getKey().getDefaultMaxStackSize());
                player.getInventory().placeItemBackInInventory(new ItemStack(e.getKey(), n));
                left -= n;
            }
        }
        return todo.size();
    }

    private static int items(BlockState s) {
        return s.hasProperty(SlabBlock.TYPE) && s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
    }

    /** The new block with every property the old one shares (facing, axis, half, waterlogged...). */
    static BlockState copy(BlockState from, Block to) {
        BlockState s = to.defaultBlockState();
        for (Property<?> p : from.getProperties()) if (s.hasProperty(p)) s = with(s, from, p);
        return s;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, BlockState from, Property<T> p) {
        return s.setValue(p, from.getValue(p));
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
    }
}
