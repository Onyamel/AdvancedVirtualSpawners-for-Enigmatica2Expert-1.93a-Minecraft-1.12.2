package ru.e2e.avs;

import java.util.Random;

public final class Regression {
    public static void main(String[] args) {
        int[] counts = {0, 1, 9, 10, 19, 20, 29, 30, 39, 40, 49, 50, 59, 60, 64};
        int[] kills = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7};
        for (int i = 0; i < counts.length; i++) check(Settings.kills(counts[i]) == kills[i], "vials " + counts[i]);
        int[] total = {5, 10, 15, 21};
        for (int i = 0; i < total.length; i++) {
            check(Settings.SPEED[i] + Settings.HEADS[i] + Settings.LUCK[i] == total[i], "upgrade capacity " + i);
        }
        int[] ticks = {140, 140, 70, 47, 35, 28, 23, 20, 18, 16, 14, 13, 12};
        for (int i = 0; i < ticks.length; i++) check(Settings.ticks(Settings.speed(i)) == ticks[i], "speed " + i);
        Settings.additiveSpeed = true;
        check(Settings.speed(1) == 2, "alternative speed");
        Settings.additiveSpeed = false;
        Settings.baseEnergy = 1280;
        check(Settings.cost(0, Settings.speed(0), 0) == 1280, "base energy");
        check(Settings.cost(0, Settings.speed(1), 0) == 1280, "speed I energy");
        check(Settings.cost(1, Settings.speed(5), 0) == 9600, "speed V tier II energy");
        check(Settings.cost(3, Settings.speed(12), 5) == 51_840_000, "maximum RF/t");
        Settings.baseEnergy = Integer.MAX_VALUE;
        check(Settings.cost(3, 13, 5) == Integer.MAX_VALUE, "overflow");
        for (int level = 0; level <= 4; level++) {
            Random random = new Random(58366L);
            int observed = 0;
            for (int i = 0; i < 100_000; i++) observed += Settings.heads(level, random);
            double expected = level == 4 ? 2 : level * .25;
            check(Math.abs(observed / 100_000.0 - expected) < .005, "heads " + level);
        }
        double[] multipliers = {1, 1.5, 2, 3, 4};
        for (double chance : new double[]{.01, .10, .20, .25, .40, .50, .90}) {
            for (int level = 0; level < multipliers.length; level++) {
                check(Settings.chance(chance, level) == Math.min(1, chance * multipliers[level]), "luck " + level);
            }
            check(Settings.chance(chance, 5) == 1 && Settings.chance(0, 5) == 0, "luck V");
        }
        System.out.println("AVS: regression checks passed");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
