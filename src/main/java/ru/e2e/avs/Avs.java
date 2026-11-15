package ru.e2e.avs;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

@Mod(modid = Avs.ID, name = "Advanced Virtual Spawners", version = "1.1.0", acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:enderio@[5.3.72,5.3.73);required-after:draconicevolution;required-after:redstoneflux")
@Mod.EventBusSubscriber
public final class Avs {
    public static final String ID = "advancedvirtualspawners";
    static final SpawnerBlock[] BLOCKS = new SpawnerBlock[4];
    static final Item[] UPGRADES = new Item[3];
    static final String[] UPGRADE_NAMES = {"speed", "beheading", "luck"};
    static Logger log;

    @Mod.Instance
    public static Avs instance;

    @SidedProxy(clientSide = "ru.e2e.avs.ClientProxy", serverSide = "ru.e2e.avs.GuiHandler")
    public static GuiHandler gui;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        Settings.load(event.getSuggestedConfigurationFile());
        GameRegistry.registerTileEntity(SpawnerTile.class, id("spawner"));
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, gui);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        EnderIo.initialize();
        VirtualLoot.loadHeadMappings();
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        for (int tier = 0; tier < BLOCKS.length; tier++) {
            SpawnerBlock block = new SpawnerBlock(tier);
            block.setRegistryName(id("spawner_" + (tier + 1))).setTranslationKey(ID + ".spawner_" + (tier + 1));
            block.setCreativeTab(CreativeTabs.REDSTONE);
            BLOCKS[tier] = block;
            event.getRegistry().register(block);
        }
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        for (int i = 0; i < UPGRADES.length; i++) {
            String name = UPGRADE_NAMES[i];
            Item item = new Item().setRegistryName(id(name)).setTranslationKey(ID + "." + name).setCreativeTab(CreativeTabs.REDSTONE);
            UPGRADES[i] = item;
            event.getRegistry().register(item);
        }
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(ID, path);
    }
}
