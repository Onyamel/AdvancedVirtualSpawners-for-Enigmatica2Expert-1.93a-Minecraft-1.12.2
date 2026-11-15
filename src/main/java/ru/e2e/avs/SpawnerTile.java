package ru.e2e.avs;

import cofh.redstoneflux.api.IEnergyReceiver;
import crazypants.enderio.util.CapturedMob;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public final class SpawnerTile extends TileEntity implements ITickable, IEnergyReceiver, ISidedInventory {
    static final int SOUL_SLOTS = 7;
    static final int INPUT_SLOTS = SOUL_SLOTS + 3;
    private int tier;
    private int energy;
    private int progress;
    private int retryTicks;
    private boolean releasingContents;
    private final List<ItemStack> pending = new ArrayList<>();
    private int pendingCursor;
    private final CapturedMob[] souls = new CapturedMob[SOUL_SLOTS];
    private final ItemStack[] lastInputs = new ItemStack[INPUT_SLOTS];
    private final int[] remoteSlotLimits = new int[INPUT_SLOTS];
    private boolean needsSoulScan = true;
    Status status = Status.EMPTY;

    enum Status { EMPTY, INVALID, ENERGY, RUNNING, OUTPUT, NO_MOB_LOOT, LOOT_ERROR }

    final ItemStackHandler inputs = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            if (world != null && world.isRemote) return remoteSlotLimits[slot];
            if (slot < SOUL_SLOTS) return slot < Settings.SOULS[tier()] ? Settings.vialLimit : 0;
            switch (slot - SOUL_SLOTS) {
                case 0: return Settings.SPEED[tier()];
                case 1: return Settings.HEADS[tier()];
                default: return Settings.LUCK[tier()];
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && getSlotLimit(slot) > 0 && (slot < SOUL_SLOTS
                    ? (world != null && world.isRemote ? EnderIo.isVial(stack) : EnderIo.soul(stack) != null)
                    : stack.getItem() == Avs.UPGRADES[slot - SOUL_SLOTS]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            inputChanged(slot);
        }
    };

    void syncSlotLimit(int slot, int limit) {
        remoteSlotLimits[slot] = Math.min(64, limit);
    }

    void inputChanged(int slot) {
        if (world != null && !world.isRemote) {
            ItemStack current = inputs.getStackInSlot(slot);
            ItemStack previous = lastInputs[slot];
            if (ItemStack.areItemStacksEqual(current, previous == null ? ItemStack.EMPTY : previous)) return;
            lastInputs[slot] = current.copy();
            if (slot < SOUL_SLOTS) needsSoulScan = true;
            progress = 0;
            retryTicks = 0;
            markDirty();
        }
    }

    final ItemStackHandler output = new ItemStackHandler(Settings.OUTPUT) {
        @Override
        protected void onContentsChanged(int slot) {
            if (world != null && !world.isRemote) markDirty();
        }
    };

    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return INPUT_SLOTS + Settings.OUTPUT; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < INPUT_SLOTS ? inputs.getStackInSlot(slot) : output.getStackInSlot(slot - INPUT_SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return !isInvalid() && !releasingContents && slot < INPUT_SLOTS ? inputs.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return isInvalid() || releasingContents || slot < INPUT_SLOTS
                    ? ItemStack.EMPTY : output.extractItem(slot - INPUT_SLOTS, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < INPUT_SLOTS ? inputs.getSlotLimit(slot) : output.getSlotLimit(slot - INPUT_SLOTS);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < INPUT_SLOTS && inputs.isItemValid(slot, stack);
        }
    };

    private final IEnergyStorage port = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) { return SpawnerTile.this.receiveEnergy(null, amount, simulate); }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return Settings.buffer(tier()); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public SpawnerTile() {}

    SpawnerTile(int tier) { this.tier = tier; }

    int tier() {
        if (world != null && getBlockType() instanceof SpawnerBlock) tier = ((SpawnerBlock) getBlockType()).tier;
        return tier;
    }

    private int upgrade(int type) {
        int slot = SOUL_SLOTS + type;
        ItemStack stack = inputs.getStackInSlot(slot);
        return stack.isEmpty() || stack.getItem() != Avs.UPGRADES[type] ? 0 : Math.min(stack.getCount(), inputs.getSlotLimit(slot));
    }

    int cycleTicks() { return Settings.ticks(Settings.speed(upgrade(0))); }
    int consumption() { return Settings.cost(tier(), Settings.speed(upgrade(0)), upgrade(2)); }
    int progress() { return progress; }

    @Override
    public void update() {
        if (!(world instanceof WorldServer) || isInvalid() || releasingContents) return;
        if (!pending.isEmpty()) {
            flushPending();
            if (!pending.isEmpty()) {
                updateStatus(Status.OUTPUT);
                return;
            }
        }

        int slots = Settings.SOULS[tier()];
        if (needsSoulScan) {
            for (int slot = 0; slot < slots; slot++) souls[slot] = EnderIo.soul(inputs.getStackInSlot(slot).copy());
            needsSoulScan = false;
        }
        boolean configured = false;
        boolean active = false;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inputs.getStackInSlot(slot);
            configured |= !stack.isEmpty();
            active |= souls[slot] != null && kills(stack) > 0;
        }
        if (!active) {
            updateStatus(configured ? Status.INVALID : Status.EMPTY);
            return;
        }
        if (!world.getGameRules().getBoolean("doMobLoot")) {
            updateStatus(Status.NO_MOB_LOOT);
            return;
        }
        if (retryTicks > 0) {
            retryTicks--;
            updateStatus(Status.LOOT_ERROR);
            return;
        }
        if (outputFull()) {
            updateStatus(Status.OUTPUT);
            return;
        }

        int cost = consumption();
        if (energy < cost) {
            updateStatus(Status.ENERGY);
            return;
        }
        if (progress + 1 >= cycleTicks()) {
            int luck = upgrade(2);
            int heads = upgrade(1);
            List<ItemStack> rolled = new VirtualLoot.BoundedDrops();
            VirtualLoot.Budget budget = new VirtualLoot.Budget();
            for (int slot = 0; slot < slots; slot++) {
                if (souls[slot] != null && !VirtualLoot.collect((WorldServer) world, pos, souls[slot],
                        kills(inputs.getStackInSlot(slot)), luck, heads, rolled, budget)) {
                    retryTicks = 100;
                    updateStatus(Status.LOOT_ERROR);
                    return;
                }
            }
            energy -= cost;
            progress = 0;
            pending.addAll(rolled);
            flushPending();
            updateStatus(pending.isEmpty() ? Status.RUNNING : Status.OUTPUT);
        } else {
            energy -= cost;
            progress++;
            updateStatus(Status.RUNNING);
        }
        markDirty();
    }

    private int kills(ItemStack stack) {
        return Settings.kills(Math.min(Settings.vialLimit, stack.getCount()));
    }

    private boolean outputFull() {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), output.getSlotLimit(slot))) return false;
        }
        return true;
    }

    private void flushPending() {
        if (pending.isEmpty() || outputFull()) return;
        int checks = Math.min(64, pending.size());
        for (int i = 0; i < checks && !pending.isEmpty(); i++) {
            if (pendingCursor >= pending.size()) pendingCursor = 0;
            ItemStack before = pending.get(pendingCursor);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, before.copy(), false);
            if (remainder.isEmpty()) pending.remove(pendingCursor);
            else {
                if (remainder.getCount() != before.getCount()) pending.set(pendingCursor, remainder);
                pendingCursor++;
            }
        }
    }

    private void updateStatus(Status value) {
        if (status != value) {
            status = value;
            markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Tier", tier());
        tag.setInteger("Energy", energy);
        tag.setInteger("Progress", progress);
        tag.setInteger("Status", status.ordinal());
        tag.setTag("Inputs", inputs.serializeNBT());
        tag.setTag("Output", output.serializeNBT());
        NBTTagList waiting = new NBTTagList();
        for (ItemStack stack : pending) waiting.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("Pending", waiting);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tier = Math.max(0, Math.min(3, tag.getInteger("Tier")));
        energy = Math.max(0, Math.min(Settings.buffer(tier), tag.getInteger("Energy")));
        readInventory(inputs, tag.getCompoundTag("Inputs"));
        for (int slot = 0; slot < INPUT_SLOTS; slot++) lastInputs[slot] = inputs.getStackInSlot(slot).copy();
        readInventory(output, tag.getCompoundTag("Output"));
        progress = Math.max(0, Math.min(Settings.time - 1, tag.getInteger("Progress")));
        int savedStatus = tag.getInteger("Status");
        status = savedStatus >= 0 && savedStatus < Status.values().length ? Status.values()[savedStatus] : Status.EMPTY;
        pending.clear();
        pendingCursor = 0;
        NBTTagList waiting = tag.getTagList("Pending", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < waiting.tagCount(); i++) {
            ItemStack stack = new ItemStack(waiting.getCompoundTagAt(i));
            if (!stack.isEmpty()) pending.add(stack);
        }
        needsSoulScan = true;
    }

    private static void readInventory(ItemStackHandler handler, NBTTagCompound saved) {
        // Фиксированный размер не берётся из входного NBT. Слоты, отключённые конфигом, остаются доступны для выгрузки.
        saved.setInteger("Size", handler.getSlots());
        handler.deserializeNBT(saved);
    }

    void dropContents() {
        if (releasingContents || !(world instanceof WorldServer)) return;
        releasingContents = true;
        List<ItemStack> removed = new ArrayList<>();
        for (int slot = 0; slot < inputs.getSlots(); slot++) removed.add(inputs.getStackInSlot(slot));
        for (int slot = 0; slot < output.getSlots(); slot++) removed.add(output.getStackInSlot(slot));
        removed.addAll(pending);
        clear();
        DropRecovery.release((WorldServer) world, pos, removed);
    }

    // Forge 1.12 очищает исходный IInventory после снимка NBT при командном переносе.
    // Старому интерфейсу доступны только выходные слоты.
    @Override public int getSizeInventory() { return Settings.OUTPUT; }
    @Override public boolean isEmpty() {
        for (int i = 0; i < getSizeInventory(); i++) if (!getStackInSlot(i).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStackInSlot(int index) { return output.getStackInSlot(index); }
    @Override public ItemStack decrStackSize(int index, int count) {
        return isInvalid() || releasingContents ? ItemStack.EMPTY : output.extractItem(index, count, false);
    }
    @Override public ItemStack removeStackFromSlot(int index) {
        if (isInvalid() || releasingContents) return ItemStack.EMPTY;
        ItemStack stack = output.getStackInSlot(index);
        output.setStackInSlot(index, ItemStack.EMPTY);
        return stack;
    }
    @Override public void setInventorySlotContents(int index, ItemStack stack) {
        if (stack.isEmpty()) output.setStackInSlot(index, ItemStack.EMPTY);
    }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUsableByPlayer(EntityPlayer player) {
        return !isInvalid() && world.getTileEntity(pos) == this && player.getDistanceSqToCenter(pos) <= 64;
    }
    @Override public void openInventory(EntityPlayer player) {}
    @Override public void closeInventory(EntityPlayer player) {}
    @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return false; }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount() { return 0; }
    @Override public String getName() { return "tile." + Avs.ID + ".spawner_" + (tier() + 1) + ".name"; }
    @Override public boolean hasCustomName() { return false; }
    @Override public ITextComponent getDisplayName() { return new TextComponentTranslation(getName()); }
    @Override public int[] getSlotsForFace(EnumFacing side) {
        int[] slots = new int[getSizeInventory()];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }
    @Override public boolean canInsertItem(int index, ItemStack stack, EnumFacing side) { return false; }
    @Override public boolean canExtractItem(int index, ItemStack stack, EnumFacing side) {
        return !isInvalid() && !releasingContents;
    }
    @Override public void clear() {
        for (int i = 0; i < inputs.getSlots(); i++) inputs.setStackInSlot(i, ItemStack.EMPTY);
        for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, ItemStack.EMPTY);
        pending.clear();
        pendingCursor = 0;
        progress = 0;
        retryTicks = 0;
        markDirty();
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        Block old = oldState.getBlock();
        return old != newState.getBlock();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing side) {
        return capability == CapabilityEnergy.ENERGY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(port);
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automation);
        return super.getCapability(capability, side);
    }

    @Override
    public int receiveEnergy(EnumFacing side, int amount, boolean simulate) {
        if (isInvalid() || releasingContents || world != null && world.isRemote && !simulate) return 0;
        int moved = Math.max(0, Math.min(Settings.buffer(tier()) - energy, amount));
        if (!simulate && moved > 0) {
            energy += moved;
            markDirty();
        }
        return moved;
    }

    @Override public int getEnergyStored(EnumFacing side) { return energy; }
    @Override public int getMaxEnergyStored(EnumFacing side) { return Settings.buffer(tier()); }
    @Override public boolean canConnectEnergy(EnumFacing side) { return true; }
}
