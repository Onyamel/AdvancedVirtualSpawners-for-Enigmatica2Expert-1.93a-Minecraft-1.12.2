package ru.e2e.avs;

import appeng.util.InventoryAdaptor;
import cofh.thermaldynamics.duct.item.DuctUnitItem;
import crazypants.enderio.base.capability.ItemTools;
import crazypants.enderio.util.CapturedMob;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.RandomChance;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static ru.e2e.avs.Regression.check;

@Mod(modid = "avsruntimecheck", name = "AVS Runtime Checks", version = "1.1.0",
        dependencies = "required-after:advancedvirtualspawners;required-after:appliedenergistics2;required-after:thermaldynamics")
public final class RuntimeChecks {
    static volatile boolean verified;

    private static boolean enabled() {
        return Boolean.getBoolean("avs.test") || new File(".avs-runtime").isFile();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient() && enabled()) {
            MinecraftForge.EVENT_BUS.register(new RuntimeClient());
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!enabled()) return;
        MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server.getWorld(0);
        try {
            verify(world);
            verified = true;
            Avs.log.info("AVS_RUNTIME_PASS");
        } catch (Throwable failure) {
            Avs.log.error("AVS_RUNTIME_FAIL", failure);
        } finally {
            if (!server.isSinglePlayer() && new File(".avs-auto-stop").isFile()) server.initiateShutdown();
        }
    }

    private static void verify(WorldServer world) throws Exception {
        checkLoot(world);
        Item vial = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "item_soul_vial"));
        checkTierOneRecipe(world, vial);
        ItemStack zombies = vial(vial, "minecraft:zombie", 64);
        ItemStack skeletons = vial(vial, "minecraft:skeleton", 64);
        check(new ItemStack(vial).getMaxStackSize() == 64 && zombies.getMaxStackSize() == 64, "vial limit");
        check(!ItemHandlerHelper.canItemStacksStack(zombies, skeletons), "different species");
        ItemStack variant = zombies.copy();
        variant.getTagCompound().setBoolean("avs_test_variant", true);
        check(!ItemHandlerHelper.canItemStacksStack(zombies, variant), "different NBT");
        InventoryBasic chest = new InventoryBasic("check", false, 27);
        check(ItemHandlerHelper.insertItemStacked(new InvWrapper(chest), zombies.copy(), false).isEmpty()
                && chest.getStackInSlot(0).getCount() == 64, "vanilla sized inventory accepts 64");

        for (Item item : Avs.UPGRADES) check(ForgeRegistries.RECIPES.containsKey(item.getRegistryName()), "recipe " + item.getRegistryName());
        BlockPos pos = world.getSpawnPoint().add(24, 5, 24);
        BlockPos saved = pos.east(448);
        world.getChunkProvider().provideChunk(saved.getX() >> 4, saved.getZ() >> 4);
        if (world.getTileEntity(saved) instanceof SpawnerTile && world.getTileEntity(saved.east()) instanceof SpawnerTile) {
            check(((SpawnerTile) world.getTileEntity(saved)).progress() == 1, "saved cycle after server restart");
            check(world.getTileEntity(saved.east()).writeToNBT(new NBTTagCompound()).getTagList("Pending", 10)
                    .tagCount() > 0, "saved pending loot after server restart");
            Avs.log.info("AVS_RESTART_PASS");
        }
        int[] soulSlots = {1, 2, 4, 7};
        int[][] limits = {{3, 1, 1}, {6, 2, 2}, {9, 3, 3}, {12, 4, 5}};
        for (int tier = 0; tier < 4; tier++) {
            check(ForgeRegistries.RECIPES.containsKey(Avs.BLOCKS[tier].getRegistryName()), "spawner recipe " + tier);
            BlockPos target = pos.east(tier);
            world.setBlockToAir(target);
            world.setBlockState(target, Avs.BLOCKS[tier].getDefaultState());
            SpawnerTile tile = (SpawnerTile) world.getTileEntity(target);
            IItemHandler port = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
            for (int i = 0; i < 7; i++) check(port.getSlotLimit(i) == (i < soulSlots[tier] ? 64 : 0), "soul slots");
            for (int i = 0; i < 3; i++) {
                int slot = SpawnerTile.SOUL_SLOTS + i;
                check(port.getSlotLimit(slot) == limits[tier][i], "upgrade limit");
                check(port.insertItem(slot, new ItemStack(Items.FLINT), false).getCount() == 1, "invalid upgrade");
                check(port.insertItem(slot, new ItemStack(Avs.UPGRADES[i], 64), false).getCount() == 64 - limits[tier][i], "upgrade overflow");
                check(port.extractItem(slot, 64, false).isEmpty(), "upgrade export");
            }
        }

        SpawnerTile machine = (SpawnerTile) world.getTileEntity(pos.east(3));
        IItemHandler io = machine.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        IEnergyStorage power = machine.getCapability(CapabilityEnergy.ENERGY, EnumFacing.WEST);
        String[] names = {"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:enderman",
                "minecraft:spider", "minecraft:wither_skeleton", "thermalfoundation:basalz"};
        for (String id : new String[]{names[6], "thermalfoundation:blizz", "thermalfoundation:blitz", "twilightforest:hedge_spider"}) {
            if (id.startsWith("twilightforest:") && !Loader.isModLoaded("twilightforest")) continue;
            List<ItemStack> modLoot = new ArrayList<>();
            CapturedMob sample = CapturedMob.create(new ResourceLocation(id));
            check(sample != null && EntityLiving.class.isAssignableFrom(sample.getEntityClass()), id + " registry");
            VirtualLoot.collect(world, pos, sample, 49, 5, 0, modLoot);
            check(!modLoot.isEmpty(), id + " loot");
        }
        for (int i = 0; i < names.length; i++) {
            check(io.insertItem(i, vial(vial, names[i], 64), false).isEmpty(), "insert " + names[i]);
            check(io.extractItem(i, 1, false).isEmpty(), "vial export");
        }
        int maxUse = machine.consumption();
        check(maxUse == Settings.cost(3, 12, 0) * 1000, "Luck V power");
        machine.update();
        check(machine.status == SpawnerTile.Status.ENERGY, "energy pause");
        energy(power, maxUse);
        machine.update();
        check(machine.progress() == 1 && power.getEnergyStored() == 0, "energy debit");
        NBTTagCompound mid = machine.writeToNBT(new NBTTagCompound());
        machine.readFromNBT(mid.copy());
        check(machine.progress() == 1 && io.getStackInSlot(0).getCount() == 64, "mid-operation NBT");
        world.setBlockToAir(saved);
        SpawnerTile unload = replica(world, machine, saved);
        Chunk chunk = world.getChunkProvider().provideChunk(saved.getX() >> 4, saved.getZ() >> 4);
        if (!Loader.isModLoaded("vintagefix")) {
            world.getChunkProvider().queueUnload(chunk);
            world.getChunkProvider().tick();
            check(world.getChunkProvider().getLoadedChunk(chunk.x, chunk.z) == null, "chunk unloaded");
            world.getChunkProvider().provideChunk(chunk.x, chunk.z);
            SpawnerTile loaded = (SpawnerTile) world.getTileEntity(saved);
            check(loaded != null && loaded != unload && loaded.progress() == 1
                    && loaded.inputs.getStackInSlot(0).getCount() == 64, "chunk reload preserved progress and vials");
        }

        int output = SpawnerTile.INPUT_SLOTS;
        for (int i = 0; i < Settings.OUTPUT; i++) machine.output.setStackInSlot(i, new ItemStack(Items.FLINT, 64));
        machine.output.setStackInSlot(0, ItemStack.EMPTY);
        int cycle = machine.cycleTicks();
        AxisAlignedBB area = new AxisAlignedBB(pos.add(-2, -2, -2), pos.add(6, 4, 4));
        int entitiesBefore = world.getEntitiesWithinAABB(EntityLiving.class, area).size();
        for (int i = 1; i < cycle; i++) {
            energy(power, maxUse);
            machine.update();
        }
        check(machine.status == SpawnerTile.Status.OUTPUT, "pending loot, blocked output");
        NBTTagCompound blocked = machine.writeToNBT(new NBTTagCompound());
        check(blocked.getTagList("Pending", 10).tagCount() > 0, "pending saved");
        world.setBlockToAir(saved.east());
        replica(world, machine, saved.east());
        machine.readFromNBT(blocked.copy());
        machine.update();
        check(machine.writeToNBT(new NBTTagCompound()).getTagList("Pending", 10)
                .equals(blocked.getTagList("Pending", 10)), "pending round trip and no reroll");

        List<ItemStack> delivered = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            drain(io, output, delivered);
            machine.update();
            if (machine.status != SpawnerTile.Status.OUTPUT) break;
        }
        drain(io, output, delivered);
        check(machine.status == SpawnerTile.Status.ENERGY, "resumed after clearing output");
        int zombieHeads = delivered.stream().filter(s -> s.getItem() == Items.SKULL && s.getMetadata() == 2).mapToInt(ItemStack::getCount).sum();
        check(zombieHeads == 14, "beheading IV for seven kills, received " + zombieHeads);
        check(world.getEntitiesWithinAABB(EntityLiving.class, area).size() == entitiesBefore, "no spawned mobs");
        List<ItemStack> drops = new ArrayList<>();
        CapturedMob zombie = CapturedMob.create(new ResourceLocation("minecraft:zombie"));
        EntityLiving sample = EnderIo.sample(zombie, world, pos);
        LootTable zombieTable = world.getLootTableManager().getLootTableFromLocation(sample.getLootTable());
        LootPool nativeRare = zombieTable.getPool("pool1");
        check(nativeRare != null, "zombie rare pool");
        Item[] candidates = {Items.IRON_INGOT, Items.CARROT, Items.POTATO};
        for (int i = 0; i < 10; i++) {
            drops.clear();
            VirtualLoot.collect(world, pos, zombie, 1, 5, 0, drops);
            for (Item item : candidates) {
                boolean present = nativeRare.lootEntries.stream().anyMatch(e -> e instanceof LootEntryItem
                        && ((LootEntryItem) e).item == item);
                int observed = drops.stream().filter(s -> s.getItem() == item).mapToInt(ItemStack::getCount).sum();
                check(observed == (present ? 1 : 0), "zombie rare drop " + item.getRegistryName());
            }
        }

        checkTransport(world, pos, machine, zombies);
        benchmark(machine);
        SpawnerTile[] devices = {machine, replica(world, machine, pos.south(2)), replica(world, machine, pos.south(4))};
        benchmark(devices);
        AxisAlignedBB dropsAtBlock = new AxisAlignedBB(machine.getPos()).grow(1);
        for (int batch = 0; batch < 16; batch++) DropRecovery.release(world, machine.getPos(), Collections.emptyList());
        int previouslyDropped = countItems(world, dropsAtBlock, zombies);
        vial.setMaxStackSize(1);
        try {
            world.setBlockToAir(machine.getPos());
            for (int batch = 0; batch < 16; batch++) DropRecovery.release(world, machine.getPos(), Collections.emptyList());
            check(countItems(world, dropsAtBlock, zombies) - previouslyDropped == 64,
                    "break preserved the entire original stack after lowering the vial limit");
        } finally {
            vial.setMaxStackSize(Settings.vialLimit);
        }
        check(machine.isInvalid(), "tile invalidated on break");
        replica(world, devices[1], pos.east(3));
        world.setBlockToAir(devices[1].getPos());
        world.setBlockToAir(devices[2].getPos());
        for (EntityItem fixtureDrop : world.getEntitiesWithinAABB(EntityItem.class, area.grow(8))) {
            fixtureDrop.setDead();
        }
        AuditRegression.verify(world, vial);
    }

    private static void checkTierOneRecipe(WorldServer world, Item vial) {
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(Avs.id("spawner_1"));
        check(recipe != null, "tier I recipe registered");
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        Item crystal = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "item_material"));
        Item spawner = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "block_powered_spawner"));
        for (int corner : new int[]{0, 2, 6, 8}) grid.setInventorySlotContents(corner, new ItemStack(crystal, 1, 18));
        ItemStack spider = vial(vial, "minecraft:spider", 1);
        spider.getTagCompound().setString("FixtureExtraData", "ignored by ingredient");
        grid.setInventorySlotContents(1, spider);
        grid.setInventorySlotContents(3, vial(vial, "minecraft:enderman", 1));
        grid.setInventorySlotContents(4, new ItemStack(spawner));
        grid.setInventorySlotContents(5, vial(vial, "minecraft:skeleton", 1));
        grid.setInventorySlotContents(7, vial(vial, "minecraft:zombie", 1));
        check(recipe.matches(grid, world), "custom tier I recipe accepts four souls and additional vial NBT");
        check(recipe.getCraftingResult(grid).getItem() == Item.getItemFromBlock(Avs.BLOCKS[0]), "tier I crafted");
        grid.setInventorySlotContents(1, new ItemStack(vial));
        check(!recipe.matches(grid, world), "empty vial rejected");
        grid.setInventorySlotContents(1, vial(vial, "minecraft:zombie", 1));
        check(!recipe.matches(grid, world), "duplicate soul rejected");
        grid.setInventorySlotContents(1, spider);
        grid.setInventorySlotContents(0, new ItemStack(crystal, 1, 17));
        check(!recipe.matches(grid, world), "wrong corner material rejected");
        Avs.log.info("AVS_TIER_ONE_RECIPE_PASS");
    }

    private static int countItems(WorldServer world, AxisAlignedBB region, ItemStack expected) {
        return world.getEntitiesWithinAABB(EntityItem.class, region).stream()
                .map(EntityItem::getItem)
                .filter(stack -> ItemHandlerHelper.canItemStacksStack(stack, expected))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static void drain(IItemHandler inventory, int outputStart, List<ItemStack> delivered) {
        for (int slot = 0; slot < Settings.OUTPUT; slot++) {
            ItemStack loot = inventory.extractItem(outputStart + slot, 64, false);
            if (!loot.isEmpty()) delivered.add(loot);
        }
    }

    private static void checkLoot(WorldServer world) {
        LootCondition[] none = new LootCondition[0];
        LootEntry guaranteed = new LootEntryItem(Items.FLINT, 1, 0,
                new LootFunction[]{new SetCount(none, new RandomValueRange(2))}, none, "flint");
        LootEntry countZero = new LootEntryItem(Items.CLAY_BALL, 1, 0,
                new LootFunction[]{new SetCount(none, new RandomValueRange(0, 1))}, none, "clay");
        LootContext context = new LootContext.Builder(world).build();
        Random random = new Random(19736);
        for (float chance : new float[]{.01f, .10f, .25f, .50f, .90f, 1f}) {
            LootPool pool = new LootPool(new LootEntry[]{guaranteed}, new LootCondition[]{new RandomChance(chance)},
                    new RandomValueRange(1), new RandomValueRange(0), "chance");
            List<ItemStack> regular = new ArrayList<>();
            List<ItemStack> rare = new ArrayList<>();
            int hits = 0;
            for (int repetition = 0; repetition < 10_000; repetition++) {
                regular.clear();
                check(VirtualLoot.boost(pool, 1, context, random, regular, rare), "standard loot pool");
                if (!regular.isEmpty()) {
                    hits++;
                    check(regular.get(0).getCount() == (chance == 1 ? 4 : 2), "quantity vs chance");
                }
            }
            check(Math.abs(hits / 10000.0 - Math.min(1, chance * 1.5)) < .02, "sampled chance " + chance);
            regular.clear();
            VirtualLoot.boost(pool, 5, context, random, regular, rare);
            List<ItemStack> result = chance == 1 ? regular : rare;
            check(result.size() == 1 && result.get(0).getCount() == (chance == 1 ? 10 : 1), "luck V quantity");
        }
        LootPool sometimesZero = new LootPool(new LootEntry[]{countZero}, none,
                new RandomValueRange(1), new RandomValueRange(0), "zero count");
        for (int i = 0; i < 50; i++) {
            List<ItemStack> rare = new ArrayList<>();
            VirtualLoot.boost(sometimesZero, 5, context, random, new ArrayList<>(), rare);
            check(rare.size() == 1 && rare.get(0).getCount() == 1, "zero-count range under luck V");
        }
    }

    private static void checkTransport(WorldServer world, BlockPos pos, SpawnerTile machine, ItemStack soul) {
        InventoryAdaptor ae = InventoryAdaptor.getAdaptor(machine, EnumFacing.NORTH);
        check(ae != null && ae.simulateRemove(64, soul, null).isEmpty(), "AE2 respects input lock");
        machine.output.setStackInSlot(0, new ItemStack(Items.FLINT, 64));
        check(ae.removeItems(64, new ItemStack(Items.FLINT), null).getCount() == 64, "AE2 adaptor extracts loot");
        SpawnerTile other = (SpawnerTile) world.getTileEntity(pos.east());
        IItemHandler itemPort = other.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST);
        check(DuctUnitItem.insertItemStackIntoInventory(itemPort, soul.copy(), 0, -1).isEmpty()
                && other.inputs.getStackInSlot(0).getCount() == 64, "Thermal Dynamics transfer inserts 64 vials");
        BlockPos chestPos = machine.getPos().north();
        world.setBlockToAir(chestPos);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        TileEntity chest = world.getTileEntity(chestPos);
        machine.output.setStackInSlot(0, new ItemStack(Items.FLINT, 64));
        ItemTools.move(ItemTools.NO_LIMIT, world, machine, EnumFacing.NORTH, chest, EnumFacing.SOUTH);
        IItemHandler chestInventory = chest.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.SOUTH);
        check(chestInventory.getStackInSlot(0).getCount() == 64 && machine.output.getStackInSlot(0).isEmpty(),
                "Ender IO transfer moves loot to chest");
        check(machine.receiveEnergy(EnumFacing.WEST, 1, true) == 1 && machine.getEnergyStored(EnumFacing.WEST) == 0,
                "Redstone Flux adapter simulation");
    }

    private static SpawnerTile replica(WorldServer world, SpawnerTile source, BlockPos copyPos) {
        NBTTagCompound copy = source.writeToNBT(new NBTTagCompound());
        copy.setInteger("x", copyPos.getX());
        copy.setInteger("y", copyPos.getY());
        copy.setInteger("z", copyPos.getZ());
        world.setBlockToAir(copyPos);
        world.setBlockState(copyPos, Avs.BLOCKS[3].getDefaultState());
        SpawnerTile replica = (SpawnerTile) world.getTileEntity(copyPos);
        replica.readFromNBT(copy);
        return replica;
    }

    private static void benchmark(SpawnerTile... devices) {
        long start = System.nanoTime();
        int cycleTicks = devices[0].cycleTicks();
        for (int i = 0; i < 25; i++) {
            for (int tick = 0; tick < cycleTicks; tick++) {
                for (SpawnerTile device : devices) {
                    device.receiveEnergy(EnumFacing.WEST, device.consumption(), false);
                    device.update();
                    for (int slot = 0; slot < Settings.OUTPUT; slot++) device.output.extractItem(slot, 64, false);
                }
            }
        }
        Avs.log.info("AVS benchmark: {} devices, 25 full tier IV cycles each in {} ms", devices.length,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    private static void energy(IEnergyStorage power, int amount) {
        check(power.receiveEnergy(amount, false) == amount, "energy insertion");
    }

    private static ItemStack vial(Item item, String id, int count) {
        ResourceLocation key = new ResourceLocation(id);
        check(EntityList.getClass(key) != null, "entity " + id);
        return CapturedMob.create(key).toStack(item, 1, count);
    }
}
