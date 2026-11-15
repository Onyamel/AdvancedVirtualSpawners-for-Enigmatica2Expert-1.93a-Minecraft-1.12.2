package ru.e2e.avs;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import crazypants.enderio.util.CapturedMob;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IIngredientFactory;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class SoulVialIngredientFactory implements IIngredientFactory {
    @Override
    public Ingredient parse(JsonContext context, JsonObject json) {
        ResourceLocation entity = new ResourceLocation(JsonUtils.getString(json, "entity"));
        Item vial = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "item_soul_vial"));
        CapturedMob soul = CapturedMob.create(entity);
        if (vial == null || soul == null) {
            throw new JsonSyntaxException("Cannot resolve Ender IO soul vial for " + entity);
        }
        return new Ingredient(soul.toStack(vial, 1, 1)) {
            @Override
            public boolean apply(ItemStack stack) {
                if (stack == null || stack.isEmpty() || stack.getItem() != vial || stack.getMetadata() != 1) return false;
                try {
                    CapturedMob captured = CapturedMob.create(stack);
                    return captured != null && entity.equals(captured.getEntityName());
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            }

            @Override
            public boolean isSimple() {
                return false;
            }
        };
    }
}
