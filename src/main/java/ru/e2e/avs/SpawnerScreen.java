package ru.e2e.avs;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

import java.util.Locale;

final class SpawnerScreen extends GuiContainer {
    private static final int[] COLORS = {0xff58d390, 0xff54c8ff, 0xffb585ff, 0xffff6376};
    private final SpawnerMenu menu;

    SpawnerScreen(SpawnerMenu menu) {
        super(menu);
        this.menu = menu;
        xSize = 178;
        ySize = 240;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff0c1523);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + xSize - 1, guiTop + ySize - 1, 0xff1c2a3b);
        drawRect(guiLeft + 8, guiTop + 41, guiLeft + xSize - 8, guiTop + 44, 0xff0a121c);
        int ticks = Math.max(1, menu.number(6));
        int fill = Math.min(xSize - 16, menu.number(8) * (xSize - 16) / ticks);
        drawRect(guiLeft + 8, guiTop + 41, guiLeft + 8 + fill, guiTop + 44, COLORS[menu.tile.tier()]);
        for (Slot slot : menu.inventorySlots) {
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            int color = slot.getSlotStackLimit() == 0 ? 0xff202b38 : 0xff33495a;
            drawRect(x - 1, y - 1, x + 17, y + 17, color);
            drawRect(x, y, x + 16, y + 16, 0xff0c1622);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int tint = COLORS[menu.tile.tier()];
        int status = Math.min(SpawnerTile.Status.values().length - 1, menu.display[10]);
        String state = SpawnerTile.Status.values()[status].name().toLowerCase(Locale.ROOT);
        line(I18n.format(menu.tile.getBlockType().getTranslationKey() + ".name"), 8, 5, tint);
        line(I18n.format("avs.power", menu.number(0), menu.number(2)), 8, 14, 0xffe4eef7);
        line(I18n.format("avs.rate", menu.number(4), menu.number(6)), 8, 23, 0xffe4eef7);
        line(I18n.format("avs.state." + state), 8, 32, tint);
        for (int i = 0; i < 3; i++) {
            int x = SpawnerMenu.UPGRADE_X[i];
            int slot = SpawnerTile.SOUL_SLOTS + i;
            line(I18n.format("avs.short." + Avs.UPGRADE_NAMES[i]), x, 64, 0xffa9c0d3);
            line("×" + menu.inventorySlots.get(slot).getSlotStackLimit(), x + 20, 82, tint);
        }
        line(I18n.format("avs.output"), 8, 91, 0xffa9c0d3);
        line(I18n.format("container.inventory"), 8, 155, 0xffa9c0d3);
    }

    private void line(String text, int x, int y, int color) {
        fontRenderer.drawString(text, x, y, color);
    }
}
