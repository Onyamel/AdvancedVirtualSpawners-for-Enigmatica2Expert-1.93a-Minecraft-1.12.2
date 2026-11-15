package ru.e2e.avs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class RuntimeClient {
    private boolean started;
    private int ticks;
    private int visualTicks;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft client = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END) return;
        if (!started && client.world == null && ++ticks >= 100) {
            started = true;
            WorldSettings settings = new WorldSettings(12345, GameType.CREATIVE, false, false, WorldType.FLAT);
            client.launchIntegratedServer("avs-runtime", "AVS runtime checks", settings);
        }
        if (!RuntimeChecks.verified || client.player == null || client.getIntegratedServer() == null) return;
        visualTicks++;
        if (visualTicks == 40 || visualTicks == 100) {
            boolean open = visualTicks == 100;
            client.getIntegratedServer().addScheduledTask(() -> {
                EntityPlayerMP player = client.getIntegratedServer().getPlayerList().getPlayerByUUID(client.player.getUniqueID());
                BlockPos pos = player.getServerWorld().getSpawnPoint().add(24, 5, 24);
                if (open) {
                    BlockPos gui = pos.east(3);
                    player.openGui(Avs.instance, 0, player.world, gui.getX(), gui.getY(), gui.getZ());
                } else {
                    player.capabilities.isFlying = true;
                    player.sendPlayerAbilities();
                    player.connection.setPlayerLocation(pos.getX() + 1.8, pos.getY() + 1.2, pos.getZ() + 4.6, 180, 31);
                }
            });
        }
        if (visualTicks == 85) client.gameSettings.hideGUI = true;
        if (visualTicks == 90) screenshot(client, "avs-models.png");
        if (visualTicks == 91) client.gameSettings.hideGUI = false;
        if (visualTicks == 130 && client.currentScreen instanceof SpawnerScreen) {
            SpawnerScreen screen = (SpawnerScreen) client.currentScreen;
            screenshot(client, "avs-gui.png");
            if (I18n.hasKey("tile.advancedvirtualspawners.spawner_4.name") && I18n.hasKey("avs.state.energy")
                    && I18n.hasKey("avs.state.loot_error")
                    && screen.inventorySlots.getSlot(6).getSlotStackLimit() == 64
                    && screen.inventorySlots.getSlot(7).getSlotStackLimit() == 12
                    && screen.inventorySlots.getSlot(8).getSlotStackLimit() == 4
                    && screen.inventorySlots.getSlot(9).getSlotStackLimit() == 5) {
                Avs.log.info("AVS_GUI_PASS");
            } else Avs.log.error("AVS_GUI_FAIL: translations or server slot limits unavailable");
        }
    }

    private void screenshot(Minecraft client, String name) {
        ScreenShotHelper.saveScreenshot(client.gameDir, name, client.displayWidth, client.displayHeight, client.getFramebuffer());
    }
}
