package ru.e2e.avs;

import crazypants.enderio.util.CapturedMob;
import net.minecraft.command.CommandClone;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import static ru.e2e.avs.Regression.check;

public final class AuditRegression {
    static void verify(WorldServer world, Item vial) throws Exception {
        BlockPos pos = world.getSpawnPoint().add(80, 12, 80);
        world.setBlockToAir(pos);
        world.setBlockState(pos, Avs.BLOCKS[0].getDefaultState());
        SpawnerTile tile = (SpawnerTile) world.getTileEntity(pos);
        ResourceLocation type = new ResourceLocation("minecraft:zombie");
        EntityLiving entity = (EntityLiving) EntityList.createEntityByIDFromName(type, world);
        ItemStack soul = CapturedMob.create(entity).toStack(vial, 1, 1);
        check(EnderIo.soul(soul) != null, "ordinary captured soul with full entity NBT");
        ItemStack mismatched = soul.copy();
        check(mismatched.getTagCompound().hasKey("entity", 10), "captured soul has entity payload");
        mismatched.getTagCompound().getCompoundTag("entity").setString("id", "minecraft:skeleton");
        check(EnderIo.soul(mismatched) == null, "inconsistent soul rejected before construction");
        tile.inputs.insertItem(0, soul, false);
        tile.inputs.insertItem(SpawnerTile.SOUL_SLOTS, new ItemStack(Avs.UPGRADES[0]), false);

        int cost = tile.consumption();
        tile.receiveEnergy(EnumFacing.UP, cost, false);
        tile.update();
        check(tile.progress() == 1, "audit fixture reached one paid tick");
        FakePlayer player = FakePlayerFactory.getMinecraft(world);
        player.setPosition(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        ItemStack previous = player.inventory.getItemStack();
        try {
            player.inventory.setItemStack(new ItemStack(Items.FLINT));
            new SpawnerMenu(player, tile).slotClick(SpawnerTile.SOUL_SLOTS, 0, ClickType.PICKUP, player);
            check(tile.progress() == 1 && player.inventory.getItemStack().getItem() == Items.FLINT,
                    "rejected click preserves paid progress");
            player.inventory.setItemStack(ItemStack.EMPTY);
            new SpawnerMenu(player, tile).slotClick(SpawnerTile.SOUL_SLOTS, 0, ClickType.PICKUP, player);
            check(tile.progress() == 0 && player.inventory.getItemStack().getItem() == Avs.UPGRADES[0],
                    "real removal resets the cycle");
        } finally {
            player.inventory.setItemStack(previous);
        }

        String oldRule = world.getGameRules().getString("doMobLoot");
        try {
            world.getGameRules().setOrCreateGameRule("doMobLoot", "false");
            tile.receiveEnergy(EnumFacing.UP, cost, false);
            tile.update();
            check(tile.getEnergyStored(null) == cost && tile.progress() == 0
                    && tile.status == SpawnerTile.Status.NO_MOB_LOOT, "disabled mob loot pauses energy and progress");
        } finally {
            world.getGameRules().setOrCreateGameRule("doMobLoot", oldRule);
        }

        for (int i = 0; i < Settings.OUTPUT; i++) tile.output.setStackInSlot(i, new ItemStack(Items.FLINT, 64));
        tile.output.setStackInSlot(0, new ItemStack(Items.DIAMOND, 63));
        NBTTagCompound state = tile.writeToNBT(new NBTTagCompound());
        NBTTagList queue = new NBTTagList();
        queue.appendTag(new ItemStack(Items.GOLD_INGOT).writeToNBT(new NBTTagCompound()));
        queue.appendTag(new ItemStack(Items.DIAMOND).writeToNBT(new NBTTagCompound()));
        state.setTag("Pending", queue);
        tile.readFromNBT(state);
        tile.update();
        check(tile.output.getStackInSlot(0).getCount() == 64
                && tile.writeToNBT(new NBTTagCompound()).getTagList("Pending", 10).tagCount() == 1,
                "compatible loot behind a blocked item reaches the output");

        LootContext context = new LootContext.Builder(world).build();
        LootCondition unknown = (rng, ctx) -> { throw new AssertionError("unknown condition evaluated by boost"); };
        LootPool custom = pool(2, new LootCondition[]{unknown});
        check(!VirtualLoot.boost(custom, 4, context, new Random(1), new ArrayList<>(), new ArrayList<>()),
                "unknown condition defers to native generation before evaluation");
        try {
            new VirtualLoot.Budget().reserve(new LootTable(new LootPool[]{pool(4096, new LootCondition[0])}), context, 1);
            throw new AssertionError("oversized loot pool accepted");
        } catch (RuntimeException expected) {
            check("Virtual loot budget exceeded".equals(expected.getMessage()), "oversized pool rejected by budget");
        }

        for (int i = 0; i < Settings.OUTPUT; i++) tile.output.setStackInSlot(i, ItemStack.EMPTY);
        tile.output.setStackInSlot(0, new ItemStack(Items.FLINT, 5));
        BlockPos destination = pos.east(4);
        world.setBlockToAir(destination);
        AxisAlignedBB local = new AxisAlignedBB(pos).grow(1);
        int initial = itemCount(world, local, Items.FLINT);
        String start = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String end = destination.getX() + " " + destination.getY() + " " + destination.getZ();
        new CommandClone().execute(world.getMinecraftServer(), player, (start + " " + start + " " + end + " replace move").split(" "));
        SpawnerTile moved = (SpawnerTile) world.getTileEntity(destination);
        check(world.getTileEntity(pos) == null && moved != null
                && moved.inputs.getStackInSlot(0).getItem() == vial && moved.output.getStackInSlot(0).getCount() == 5
                && itemCount(world, local, Items.FLINT) == initial, "command move preserves inventory without dropping a copy");

        AxisAlignedBB recoveryArea = new AxisAlignedBB(destination).grow(1);
        int floor = itemCount(world, recoveryArea, Items.FLINT);
        DropCancellation cancel = new DropCancellation(world, recoveryArea);
        MinecraftForge.EVENT_BUS.register(cancel);
        try {
            world.setBlockToAir(destination);
            check(cancel.cancelled >= 2 && itemCount(world, recoveryArea, Items.FLINT) == floor,
                    "drop cancellation fixture rejected item entities");
            String key = Avs.ID + "_drops";
            DropRecovery data = (DropRecovery) world.getPerWorldStorage().getOrLoadData(DropRecovery.class, key);
            NBTTagCompound snapshot = data.writeToNBT(new NBTTagCompound());
            data.readFromNBT(snapshot);
            check(data.writeToNBT(new NBTTagCompound()).equals(snapshot), "deferred world drops survive NBT round trip");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        DropRecovery.release(world, destination, Collections.emptyList());
        DropRecovery.release(world, destination, Collections.emptyList());
        check(itemCount(world, recoveryArea, Items.FLINT) == floor + 5, "deferred drops recover exactly once");
        Avs.log.info("AVS_AUDIT_FIXES_PASS");
    }

    private static LootPool pool(int count, LootCondition[] conditions) {
        LootCondition[] none = {};
        LootEntry entry = new LootEntryItem(Items.FLINT, 1, 0,
                new LootFunction[]{new SetCount(none, new RandomValueRange(count))}, none, "audit");
        return new LootPool(new LootEntry[]{entry}, conditions, new RandomValueRange(1), new RandomValueRange(0), "audit");
    }

    private static int itemCount(WorldServer world, AxisAlignedBB area, Item item) {
        return world.getEntitiesWithinAABB(EntityItem.class, area).stream().map(EntityItem::getItem)
                .filter(stack -> stack.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    public static final class DropCancellation {
        private final WorldServer world;
        private final AxisAlignedBB area;
        int cancelled;

        DropCancellation(WorldServer world, AxisAlignedBB area) {
            this.world = world;
            this.area = area;
        }

        @SubscribeEvent
        public void join(EntityJoinWorldEvent event) {
            if (event.getWorld() != world || !(event.getEntity() instanceof EntityItem)
                    || !area.contains(event.getEntity().getPositionVector())) return;
            event.setCanceled(true);
            cancelled++;
        }
    }
}
