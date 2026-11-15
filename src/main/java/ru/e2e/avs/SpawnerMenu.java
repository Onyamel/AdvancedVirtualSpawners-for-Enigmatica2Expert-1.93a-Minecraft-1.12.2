package ru.e2e.avs;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Arrays;

final class SpawnerMenu extends Container {
    static final int MACHINE_SLOTS = SpawnerTile.INPUT_SLOTS + Settings.OUTPUT;
    static final int[] UPGRADE_X = {9, 64, 119};
    private static final int SLOT_LIMIT_OFFSET = 12;
    final SpawnerTile tile;
    final int[] display = new int[SLOT_LIMIT_OFFSET + SpawnerTile.INPUT_SLOTS];
    private final int[] last = new int[display.length];

    SpawnerMenu(EntityPlayer player, SpawnerTile tile) {
        this.tile = tile;
        Arrays.fill(last, -1);
        for (int i = 0; i < SpawnerTile.SOUL_SLOTS; i++) {
            addSlotToContainer(input(i, 9 + 18 * i, 48));
        }
        for (int i = 0; i < 3; i++) {
            addSlotToContainer(input(SpawnerTile.SOUL_SLOTS + i, UPGRADE_X[i], 74));
        }
        for (int i = 0; i < Settings.OUTPUT; i++) {
            addSlotToContainer(new SlotItemHandler(tile.output, i, 9 + 18 * (i % 9), 101 + 18 * (i / 9)) {
                @Override public boolean isItemValid(ItemStack stack) { return false; }
            });
        }
        for (int i = 0; i < 27; i++) addSlotToContainer(new Slot(player.inventory, i + 9, 9 + 18 * (i % 9), 165 + 18 * (i / 9)));
        for (int i = 0; i < 9; i++) addSlotToContainer(new Slot(player.inventory, i, 9 + 18 * i, 221));
    }

    private Slot input(int index, int x, int y) {
        return new SlotItemHandler(tile.inputs, index, x, y) {
            @Override public boolean isItemValid(ItemStack stack) { return tile.inputs.isItemValid(index, stack); }
            @Override public int getItemStackLimit(ItemStack stack) { return Math.min(getSlotStackLimit(), stack.getMaxStackSize()); }
            @Override public void onSlotChanged() { tile.inputChanged(index); }
        };
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        // Vanilla сначала сливает стаки, минуя isItemValid. Для входов используем обработчик Forge.
        if (index >= MACHINE_SLOTS) {
            ItemStack left = stack.copy();
            for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
                for (int i = 0; i < SpawnerTile.INPUT_SLOTS && !left.isEmpty(); i++) {
                    if (tile.inputs.getStackInSlot(i).isEmpty() == (pass == 1)) {
                        left = tile.inputs.insertItem(i, left, false);
                    }
                }
            }
            if (left.getCount() == original.getCount()) return ItemStack.EMPTY;
            slot.putStack(left);
        } else {
            ItemStack remaining = stack.copy();
            if (!mergeItemStack(remaining, MACHINE_SLOTS, inventorySlots.size(), true)) return ItemStack.EMPTY;
            slot.putStack(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
        return original;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int energy = tile.getEnergyStored(null);
        int capacity = tile.getMaxEnergyStored(null);
        int use = tile.consumption();
        int time = tile.cycleTicks();
        int progress = tile.progress();
        int[] values = {energy & 65535, energy >>> 16, capacity & 65535, capacity >>> 16, use & 65535, use >>> 16,
                time & 65535, time >>> 16, progress & 65535, progress >>> 16, tile.status.ordinal(), tile.tier()};
        for (int i = 0; i < values.length; i++) {
            sync(i, values[i]);
        }
        for (int slot = 0; slot < SpawnerTile.INPUT_SLOTS; slot++) sync(SLOT_LIMIT_OFFSET + slot, tile.inputs.getSlotLimit(slot));
    }

    private void sync(int index, int value) {
        if (last[index] == value) return;
        for (IContainerListener listener : listeners) listener.sendWindowProperty(this, index, value);
        last[index] = value;
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        for (int i = 0; i < last.length; i++) listener.sendWindowProperty(this, i, last[i]);
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id < 0 || id >= display.length) return;
        display[id] = value & 65535;
        if (id >= SLOT_LIMIT_OFFSET) tile.syncSlotLimit(id - SLOT_LIMIT_OFFSET, display[id]);
    }

    int number(int id) {
        return display[id] | display[id + 1] << 16;
    }
}
