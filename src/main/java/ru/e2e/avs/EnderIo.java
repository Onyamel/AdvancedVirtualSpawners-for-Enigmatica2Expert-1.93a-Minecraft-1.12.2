package ru.e2e.avs;

import crazypants.enderio.base.capacitor.DefaultCapacitorData;
import crazypants.enderio.base.recipe.spawner.EntityDataRegistry;
import crazypants.enderio.machines.capacitor.CapacitorKey;
import crazypants.enderio.util.CapturedMob;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

final class EnderIo {
    private static Item vial;

    static void initialize() {
        vial = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "item_soul_vial"));
        if (vial == null) throw new IllegalStateException("Ender IO Soul Vial отсутствует в реестре");
        vial.setMaxStackSize(Settings.vialLimit);
        if (Settings.baseEnergy == 0) {
            ResourceLocation zombie = new ResourceLocation("minecraft", "zombie");
            double multiplier = EntityDataRegistry.getInstance().getCostMultiplierFor(zombie);
            Settings.baseEnergy = Math.max(1, (int) (CapacitorKey.SPAWNER_POWER_USE
                    .get(DefaultCapacitorData.BASIC_CAPACITOR) * multiplier));
        }
        Avs.log.info("Soul Vial: {} per stack. Powered Spawner baseline: {} RF/t", Settings.vialLimit, Settings.baseEnergy);
    }

    static CapturedMob soul(ItemStack stack) {
        if (!isVial(stack)) return null;
        try {
            CapturedMob mob = CapturedMob.create(stack);
            if (mob == null || mob.getEntityName() == null) return null;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) return null;
            // Ender IO выбирает entity раньше EntityTag, а идентификатор может читать из корня.
            String dataKey = tag.hasKey("entity") ? "entity" : "EntityTag";
            if (tag.hasKey(dataKey)) {
                if (!tag.hasKey(dataKey, Constants.NBT.TAG_COMPOUND)) return null;
                String nested = tag.getCompoundTag(dataKey).getString("id");
                if (nested.isEmpty() || !mob.getEntityName().equals(new ResourceLocation(nested))) return null;
            }
            String name = mob.getEntityName().toString();
            if (Settings.excluded.contains(name) || (!Settings.allowed.isEmpty() && !Settings.allowed.contains(name))) return null;
            Class<? extends Entity> type = mob.getEntityClass();
            return type != null && EntityLiving.class.isAssignableFrom(type) ? mob : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static boolean isVial(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == vial;
    }

    static EntityLiving sample(CapturedMob mob, WorldServer world, BlockPos pos) {
        Entity entity = mob.getEntity(world, true);
        if (!(entity instanceof EntityLiving) || !mob.getEntityName().equals(EntityList.getKey(entity))) return null;
        entity.setPosition(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        return (EntityLiving) entity;
    }
}
