package ru.e2e.avs;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Settings {
    static final int[] SOULS = {1, 2, 4, 7};
    static final int[] SPEED = {3, 6, 9, 12};
    static final int[] SPEED_MULTIPLIER = {1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    static final int[] HEADS = {1, 2, 3, 4};
    static final int[] LUCK = {1, 2, 3, 5};
    static final int[] KILLS = {1, 2, 3, 4, 5, 6, 7};
    static final int[] QUANTITY = {1, 2, 3, 4, 5};
    static final double[] CHANCE = {1, 1.5, 2, 3, 4};
    static final double[] BEHEADING = {0, .25, .50, .75};
    static final double[] TIERS = {1, 1.5, 2.25, 3.375};
    static final int OUTPUT = 27;

    static int time = 140, baseEnergy, luckEnergy = 1000, capacity = 100_000_000, vialLimit = 64;
    static boolean additiveSpeed, diagnostics;
    static Set<String> allowed = new HashSet<>();
    static Set<String> excluded = new HashSet<>();
    static String[] headMappings = new String[0];

    static void load(File file) {
        Configuration cfg = new Configuration(file);
        time = cfg.getInt("baseTicks", "machine", time, 1, 72000, "Длительность базового цикла в тиках");
        additiveSpeed = cfg.getBoolean("additiveSpeed", "machine", false, "Скорость по формуле 1 + число улучшений");
        vialLimit = cfg.getInt("vialLimit", "machine", vialLimit, 1, 64, "Глобальный лимит стака Soul Vial");
        baseEnergy = cfg.getInt("baseEnergy", "energy", 0, 0, 1000000000, "0 = эталон Ender IO: зомби + базовый конденсатор");
        luckEnergy = cfg.getInt("luckV", "energy", luckEnergy, 1, 1000000, "Множитель Удачи V");
        capacity = cfg.getInt("capacity", "energy", capacity, 1, Integer.MAX_VALUE, "Вместимость FE. Минимум увеличивается до максимального расхода за тик");
        readInts(cfg, "soulSlots", SOULS, 1, 7);
        readInts(cfg, "speedSlots", SPEED, 0, 12);
        readInts(cfg, "speedMultiplier", SPEED_MULTIPLIER, 1, 64);
        readInts(cfg, "beheadingSlots", HEADS, 0, 4);
        readInts(cfg, "luckSlots", LUCK, 0, 5);
        readInts(cfg, "killsPerBand", KILLS, 0, 7);
        readInts(cfg, "commonQuantity", QUANTITY, 1, 32);
        readDoubles(cfg, "luckChance", CHANCE, 1, 100);
        readDoubles(cfg, "beheadingChance", BEHEADING, 0, 1);
        readDoubles(cfg, "tierEnergy", TIERS, .01, 1000);
        for (int tier = 0; tier < 3; tier++) LUCK[tier] = Math.min(LUCK[tier], 4);
        SPEED_MULTIPLIER[0] = 1;
        for (int level = 1; level < SPEED_MULTIPLIER.length; level++) {
            SPEED_MULTIPLIER[level] = Math.max(SPEED_MULTIPLIER[level - 1], SPEED_MULTIPLIER[level]);
        }
        allowed = new HashSet<>(Arrays.asList(cfg.getStringList("allow", "entities", new String[0], "Пустой список разрешает все подходящие типы")));
        excluded = new HashSet<>(Arrays.asList(cfg.getStringList("deny", "entities", new String[] {
                "minecraft:ender_dragon", "minecraft:wither"
        }, "Исключённые сущности")));
        headMappings = cfg.getStringList("headMappings", "entities", headMappings,
                "Дополнительные головы: mod:entity=mod:item@metadata");
        diagnostics = cfg.getBoolean("logFallbacks", "diagnostics", false, "Один раз сообщать о таблицах с неподдерживаемой схемой Удачи");
        cfg.save();
    }

    private static void readInts(Configuration cfg, String key, int[] target, int min, int max) {
        int[] values = cfg.get("tuning", key, target.clone(), "", min, max).getIntList();
        if (values.length == target.length) {
            for (int i = 0; i < values.length; i++) target[i] = Math.max(min, Math.min(max, values[i]));
        }
    }

    private static void readDoubles(Configuration cfg, String key, double[] target, double min, double max) {
        double[] values = cfg.get("tuning", key, target.clone(), "", min, max).getDoubleList();
        if (values.length == target.length) {
            for (int i = 0; i < values.length; i++) {
                if (Double.isFinite(values[i])) target[i] = Math.max(min, Math.min(max, values[i]));
            }
        }
    }

    static int kills(int count) {
        return count <= 0 ? 0 : KILLS[Math.min(6, count / 10)];
    }

    static int speed(int count) {
        return additiveSpeed ? 1 + count : SPEED_MULTIPLIER[count];
    }

    static int ticks(int speed) {
        return Math.max(1, (int) Math.round((double) time / speed));
    }

    static int cost(int tier, int speed, int luck) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE,
                Math.round(baseEnergy * TIERS[tier] * speed * (luck == 5 ? luckEnergy : 1))));
    }

    static int buffer(int tier) {
        return Math.max(capacity, cost(tier, speed(SPEED[tier]), LUCK[tier]));
    }

    static int heads(int level, java.util.Random random) {
        if (level == 4) return 2;
        return level > 0 && random.nextDouble() < BEHEADING[level] ? 1 : 0;
    }

    static double chance(double original, int luck) {
        if (original <= 0) return 0;
        return luck == 5 ? 1 : Math.min(1, original * CHANCE[luck]);
    }
}
