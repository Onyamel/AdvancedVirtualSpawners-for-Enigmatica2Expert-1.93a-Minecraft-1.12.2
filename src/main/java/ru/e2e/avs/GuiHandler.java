package ru.e2e.avs;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {
    SpawnerTile spawner(int id, World world, int x, int y, int z) {
        if (id != 0) return null;
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return tile instanceof SpawnerTile ? (SpawnerTile) tile : null;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        SpawnerTile tile = spawner(id, world, x, y, z);
        return tile == null ? null : new SpawnerMenu(player, tile);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
