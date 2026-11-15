package ru.e2e.avs;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Avs.ID, value = Side.CLIENT)
public final class ClientProxy extends GuiHandler {
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        SpawnerTile tile = spawner(id, world, x, y, z);
        return tile == null ? null : new SpawnerScreen(new SpawnerMenu(player, tile));
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Block block : Avs.BLOCKS) register(Item.getItemFromBlock(block));
        for (Item item : Avs.UPGRADES) register(item);
    }

    private static void register(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
