package ru.e2e.avs;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mod.EventBusSubscriber(modid = Avs.ID)
public final class DropRecovery extends WorldSavedData {
    private static final String KEY = Avs.ID + "_drops";
    private static final int ATTEMPTS = 64;
    private final Deque<Drop> waiting = new ArrayDeque<>();

    public DropRecovery(String name) { super(name); }

    static void release(WorldServer world, BlockPos pos, List<ItemStack> stacks) {
        DropRecovery data = get(world, true);
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) data.waiting.addLast(new Drop(pos, stack.copy()));
        }
        data.markDirty();
        data.flush(world);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
                || event.world.getTotalWorldTime() % 20 != 0) return;
        WorldServer world = (WorldServer) event.world;
        DropRecovery data = get(world, false);
        if (data != null) data.flush(world);
    }

    private static DropRecovery get(WorldServer world, boolean create) {
        MapStorage storage = world.getPerWorldStorage();
        DropRecovery data = (DropRecovery) storage.getOrLoadData(DropRecovery.class, KEY);
        if (data == null && create) {
            data = new DropRecovery(KEY);
            storage.setData(KEY, data);
        }
        return data;
    }

    private boolean flushing;

    private void flush(WorldServer world) {
        if (flushing) return;
        flushing = true;
        try {
            int entries = Math.min(ATTEMPTS, waiting.size());
            int attempts = ATTEMPTS;
            while (entries-- > 0 && attempts > 0) {
                Drop drop = waiting.removeFirst();
                try {
                    if (!world.isBlockLoaded(drop.pos)) continue;
                    int limit = Math.max(1, Math.min(64, drop.stack.getMaxStackSize()));
                    while (!drop.stack.isEmpty() && attempts-- > 0) {
                        int count = Math.min(limit, drop.stack.getCount());
                        ItemStack part = drop.stack.copy();
                        part.setCount(count);
                        EntityItem entity = new EntityItem(world, drop.pos.getX() + .5,
                                drop.pos.getY() + .5, drop.pos.getZ() + .5, part);
                        entity.setDefaultPickupDelay();
                        if (!world.spawnEntity(entity)) break;
                        drop.stack.shrink(count);
                        markDirty();
                    }
                } finally {
                    if (!drop.stack.isEmpty()) waiting.addLast(drop);
                }
            }
        } finally {
            flushing = false;
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        waiting.clear();
        NBTTagList entries = tag.getTagList("Drops", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(entry.getCompoundTag("Stack"));
            if (!stack.isEmpty()) waiting.addLast(new Drop(BlockPos.fromLong(entry.getLong("Pos")), stack));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (Drop drop : waiting) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Pos", drop.pos.toLong());
            entry.setTag("Stack", drop.stack.writeToNBT(new NBTTagCompound()));
            entries.appendTag(entry);
        }
        tag.setTag("Drops", entries);
        return tag;
    }

    private static final class Drop {
        final BlockPos pos;
        final ItemStack stack;

        Drop(BlockPos pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }
    }
}
