package ru.e2e.avs;

import crazypants.enderio.util.CapturedMob;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootEntryTable;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.KilledByPlayer;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.RandomChance;
import net.minecraft.world.storage.loot.conditions.RandomChanceWithLooting;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.LootingEnchantBonus;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraft.world.storage.loot.functions.SetMetadata;
import net.minecraft.world.storage.loot.functions.SetNBT;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class VirtualLoot {
    private static final Set<ResourceLocation> REPORTED = new HashSet<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static final Map<ResourceLocation, ItemStack> EXTRA_HEADS = new HashMap<>();
    private static final UUID LOOT_PLAYER_ID = UUID.fromString("a4198755-a7f1-479a-b3e4-e6c7758fb93d");

    static void loadHeadMappings() {
        for (String mapping : Settings.headMappings) {
            try {
                String[] pair = mapping.split("=", 2);
                String[] target = pair[1].trim().split("@", 2);
                ResourceLocation mob = new ResourceLocation(pair[0].trim());
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(target[0]));
                int meta = target.length == 2 ? Integer.parseInt(target[1]) : 0;
                if (!ForgeRegistries.ENTITIES.containsKey(mob) || item == null || item == Items.AIR
                        || meta < 0 || meta >= 32767) throw new IllegalArgumentException();
                EXTRA_HEADS.put(mob, new ItemStack(item, 1, meta));
            } catch (RuntimeException exception) {
                Avs.log.warn("Invalid head mapping: {}", mapping);
            }
        }
    }

    static void collect(WorldServer world, BlockPos pos, CapturedMob mob, int kills, int luck,
                        int beheading, List<ItemStack> output) {
        collect(world, pos, mob, kills, luck, beheading, output, new Budget());
    }

    static boolean collect(WorldServer world, BlockPos pos, CapturedMob mob, int kills, int luck,
                           int beheading, List<ItemStack> output, Budget budget) {
        if (kills <= 0) return true;
        if (!world.getGameRules().getBoolean("doMobLoot")) return true;
        ResourceLocation entityId = mob.getEntityName();
        try {
            // Forge 1.12 индексирует FakePlayer по GameProfile, а не по миру.
            UUID playerId = new UUID(LOOT_PLAYER_ID.getMostSignificantBits(),
                    LOOT_PLAYER_ID.getLeastSignificantBits() ^ world.provider.getDimension());
            FakePlayer player = FakePlayerFactory.get(world, new GameProfile(playerId, "[AVS]"));
            List<ItemStack> result = new BoundedDrops();
            ItemStack head = beheading == 0 ? ItemStack.EMPTY : head(entityId);
            for (int kill = 0; kill < kills; kill++) {
                EntityLiving entity = EnderIo.sample(mob, world, pos);
                if (entity == null) return false;
                if (!entity.canDropLoot()) continue;
                player.setPosition(entity.posX, entity.posY, entity.posZ);
                LootContext context = new LootContext.Builder(world).withLootedEntity(entity).withPlayer(player)
                        .withDamageSource(DamageSource.causePlayerDamage(player)).build();
                ResourceLocation id = entity.deathLootTable != null ? entity.deathLootTable : entity.getLootTable();
                LootTable table = id == null ? LootTable.EMPTY_LOOT_TABLE : world.getLootTableManager().getLootTableFromLocation(id);
                budget.reserve(table, context, 1);
                Random random = entity.deathLootTable != null && entity.deathLootTableSeed != 0
                        ? new Random(entity.deathLootTableSeed) : world.rand;
                List<ItemStack> rare = new BoundedDrops();
                context.addLootTable(table);
                try {
                    for (LootPool pool : table.pools) {
                        if (luck == 0 || !boost(pool, luck, context, random, result, rare)) {
                            if (luck > 0 && Settings.diagnostics && REPORTED.add(id)) Avs.log.info("Native loot for complex pool in {}", id);
                            List<ItemStack> nativeDrops = new BoundedDrops();
                            pool.generateLoot(nativeDrops, random, context);
                            for (ItemStack stack : nativeDrops) append(result, stack, 1);
                        }
                    }
                    result.addAll(rare);
                } finally {
                    context.removeLootTable(table);
                }
                if (!head.isEmpty()) {
                    int count = Settings.heads(beheading, world.rand);
                    if (count > 0) {
                        ItemStack drop = head.copy();
                        drop.setCount(count);
                        append(result, drop, 1);
                    }
                }
            }
            if ((long) output.size() + result.size() > BoundedDrops.LIMIT) throw new LootLimit();
            output.addAll(result);
            return true;
        } catch (RuntimeException exception) {
            if (FAILED.add(entityId)) Avs.log.warn("Cannot process soul {}", entityId, exception);
            return false;
        }
    }

    static boolean boost(LootPool pool, int luck, LootContext context, Random random,
                         List<ItemStack> output, List<ItemStack> rare) {
        if (pool.getClass() != LootPool.class || pool.getRolls().getMin() != pool.getRolls().getMax()
                || pool.getRolls().getMin() != 1
                || context.getLuck() != 0 || !knownConditions(pool.poolConditions)) return false;

        // В многовариантном пуле только детерминированный отбор даёт известные веса.
        boolean weighted = pool.lootEntries.size() > 1;
        List<LootEntry> eligible = new ArrayList<>();
        Map<LootEntry, RandomValueRange> ranges = new HashMap<>();
        Set<Item> items = new HashSet<>();
        double totalWeight = 0;
        for (LootEntry entry : pool.lootEntries) {
            if (entry.getClass() != LootEntryItem.class && entry.getClass() != LootEntryEmpty.class) return false;
            if (!knownConditions(java.util.Arrays.asList(entry.conditions))) return false;
            if (weighted) {
                for (LootCondition condition : entry.conditions) {
                    if (condition.getClass() != KilledByPlayer.class) return false;
                }
                if (probability(java.util.Arrays.asList(entry.conditions), context, random) == 0) continue;
            }
            int weight = entry.getEffectiveWeight(context.getLuck());
            if (weight > 0) {
                if (entry instanceof LootEntryItem) {
                    LootEntryItem item = (LootEntryItem) entry;
                    if (!items.add(item.item)) return false;
                    RandomValueRange range = countRange(item, context);
                    if (range == null) return false;
                    ranges.put(item, range);
                }
                eligible.add(entry);
                totalWeight += weight;
            }
        }
        double poolChance = probability(pool.poolConditions, context, random);
        if (poolChance <= 0) return true;

        // Сегменты вероятностей сохраняют взаимоисключение, пока сумма не превышает 1.
        double draw = random.nextDouble();
        double start = 0;
        for (LootEntry entry : eligible) {
            if (entry.getClass() == LootEntryEmpty.class) continue;
            double original = poolChance * entry.getEffectiveWeight(context.getLuck()) / totalWeight;
            if (!weighted) original *= probability(java.util.Arrays.asList(entry.conditions), context, random);
            RandomValueRange range = ranges.get(entry);
            int low = (int) Math.floor(range.getMin());
            int high = (int) Math.floor(range.getMax());
            if (high < 1) continue;
            if (low < 1) original *= high / (high - (double) low + 1);
            if (original <= 0) continue;
            double modified = Settings.chance(original, luck);
            if (selected(draw, start, modified)) {
                List<ItemStack> rolled = new ArrayList<>();
                LootEntry source = low >= 1 ? entry : withPositiveCount((LootEntryItem) entry, high);
                source.addLoot(rolled, random, context);
                for (ItemStack stack : rolled) {
                    if (original >= 1) append(output, stack, Settings.QUANTITY[Math.min(luck, 4)]);
                    else if (luck == 5 && !stack.isEmpty()) {
                        ItemStack single = ItemHandlerHelper.copyStackWithSize(stack, 1);
                        if (rare.stream().noneMatch(previous -> ItemStack.areItemStacksEqual(previous, single))) {
                            rare.add(single);
                        }
                    } else append(output, stack, 1);
                }
            }
            start += modified;
        }
        return true;
    }

    private static final RandomValueRange DEFAULT_COUNT = new RandomValueRange(1);
    private static final LootCondition[] NO_CONDITIONS = new LootCondition[0];

    private static RandomValueRange countRange(LootEntryItem entry, LootContext context) {
        RandomValueRange count = DEFAULT_COUNT;
        for (LootFunction function : entry.functions) {
            Class<?> type = function.getClass();
            if (type == LootingEnchantBonus.class && context.getLootingModifier() == 0) continue;
            if (function.getConditions().length != 0) return null;
            if (type == SetCount.class && count == DEFAULT_COUNT) {
                count = ((SetCount) function).countRange;
                if (count.getMax() > 4096 || count.getMin() < -4096 || count.getMin() > count.getMax()) return null;
            } else if (type == SetMetadata.class) {
                RandomValueRange metadata = ((SetMetadata) function).metaRange;
                if (metadata.getMin() != metadata.getMax()) return null;
            } else if (type != SetNBT.class) return null;
        }
        return count;
    }

    private static LootEntryItem withPositiveCount(LootEntryItem entry, int high) {
        LootFunction[] functions = entry.functions.clone();
        for (int i = 0; i < functions.length; i++) {
            if (functions[i].getClass() == SetCount.class) {
                functions[i] = new SetCount(NO_CONDITIONS, new RandomValueRange(1, high));
            }
        }
        return new LootEntryItem(entry.item, 1, 0, functions, NO_CONDITIONS, entry.getEntryName());
    }

    private static double probability(Iterable<LootCondition> conditions, LootContext context, Random random) {
        double chance = 1;
        for (LootCondition condition : conditions) {
            if (condition.getClass() == RandomChance.class) {
                chance *= clamp(((RandomChance) condition).chance);
            } else if (condition.getClass() == RandomChanceWithLooting.class) {
                RandomChanceWithLooting scaled = (RandomChanceWithLooting) condition;
                chance *= clamp(scaled.chance + context.getLootingModifier() * scaled.lootingMultiplier);
            } else if (!condition.testCondition(random, context)) return 0;
        }
        return chance;
    }

    private static boolean knownConditions(Iterable<LootCondition> conditions) {
        for (LootCondition condition : conditions) {
            Class<?> type = condition.getClass();
            if (type != RandomChance.class && type != RandomChanceWithLooting.class && type != KilledByPlayer.class) return false;
        }
        return true;
    }

    private static double clamp(double chance) {
        return Math.max(0, Math.min(1, chance));
    }

    static boolean selected(double draw, double start, double chance) {
        double offset = draw - (start - Math.floor(start));
        if (offset < 0) offset += 1;
        return chance >= 1 || offset < chance;
    }

    private static void append(List<ItemStack> output, ItemStack stack, int factor) {
        if (stack.isEmpty()) return;
        long left = (long) stack.getCount() * factor;
        int limit = Math.max(1, Math.min(64, stack.getMaxStackSize()));
        if (output.size() + (left + limit - 1) / limit > BoundedDrops.LIMIT) throw new LootLimit();
        while (left > 0) {
            int count = (int) Math.min(limit, left);
            output.add(ItemHandlerHelper.copyStackWithSize(stack, count));
            left -= count;
        }
    }

    static final class BoundedDrops extends ArrayList<ItemStack> {
        static final int LIMIT = 2048;

        @Override public boolean add(ItemStack stack) {
            if (size() >= LIMIT) throw new LootLimit();
            return super.add(stack);
        }

        @Override public boolean addAll(Collection<? extends ItemStack> stacks) {
            if ((long) size() + stacks.size() > LIMIT) throw new LootLimit();
            return super.addAll(stacks);
        }
    }

    static final class Budget {
        private int remaining = 32768;

        void reserve(LootTable table, LootContext context, int kills) {
            inspect(table, context, kills, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }

        private void inspect(LootTable table, LootContext context, long visits, Set<LootTable> path, int depth) {
            if (depth >= 12) throw new LootLimit();
            if (!path.add(table)) return;
            try {
                charge(visits);
                for (LootPool pool : table.pools) {
                    double maxRolls = upperBound(pool.getRolls())
                            + (context.getLuck() == 0 ? 0 : Math.abs(context.getLuck()) * upperBound(pool.getBonusRolls()));
                    if (!Double.isFinite(maxRolls) || maxRolls > 64) throw new LootLimit();
                    long rolls = visits * Math.max(1, (int) Math.ceil(maxRolls));
                    charge(rolls);
                    for (LootEntry entry : pool.lootEntries) {
                        charge(rolls);
                        if (entry.getClass() == LootEntryTable.class) {
                            LootTable nested = context.getLootTableManager().getLootTableFromLocation(((LootEntryTable) entry).table);
                            inspect(nested, context, rolls, path, depth + 1);
                        } else if (entry instanceof LootEntryItem) {
                            double count = 1;
                            for (LootFunction function : ((LootEntryItem) entry).functions) {
                                charge(rolls * (1L + function.getConditions().length));
                                if (function instanceof SetCount) {
                                    count = Math.max(count, upperBound(((SetCount) function).countRange));
                                } else if (function instanceof LootingEnchantBonus) {
                                    int looting = Math.max(0, context.getLootingModifier());
                                    if (looting > 0) count += Math.ceil(looting * upperBound(((LootingEnchantBonus) function).count));
                                }
                            }
                            if (count > BoundedDrops.LIMIT) throw new LootLimit();
                            charge(rolls * (long) count);
                        }
                    }
                }
            } finally {
                path.remove(table);
            }
        }

        private double upperBound(RandomValueRange range) {
            double bound = Math.max(range.getMin(), range.getMax());
            if (!Double.isFinite(bound)) throw new LootLimit();
            return Math.max(0, bound);
        }

        private void charge(long work) {
            if (work > remaining) throw new LootLimit();
            remaining -= (int) work;
        }
    }

    private static final class LootLimit extends RuntimeException {
        LootLimit() { super("Virtual loot budget exceeded"); }
    }

    private static ItemStack head(ResourceLocation entity) {
        ItemStack configured = EXTRA_HEADS.get(entity);
        if (configured != null) return configured;
        switch (entity.toString()) {
            case "minecraft:skeleton": return new ItemStack(Items.SKULL, 1, 0);
            case "minecraft:wither_skeleton": return new ItemStack(Items.SKULL, 1, 1);
            case "minecraft:zombie": return new ItemStack(Items.SKULL, 1, 2);
            case "minecraft:creeper": return new ItemStack(Items.SKULL, 1, 4);
            case "minecraft:ender_dragon": return new ItemStack(Items.SKULL, 1, 5);
            case "minecraft:enderman":
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enderio", "block_enderman_skull"));
                return item == null ? ItemStack.EMPTY : new ItemStack(item);
            default: return ItemStack.EMPTY;
        }
    }
}
