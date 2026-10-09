package org.enthusia.events.domain;

/** Pure limits for independently rolled loot. Zero preserves legacy unlimited behavior. */
public record LootLimits(int maxArmor, int maxWeapons, int maxTotal) {
    public LootLimits {
        if (maxArmor < 0 || maxWeapons < 0 || maxTotal < 0)
            throw new IllegalArgumentException("Loot limits cannot be negative");
    }
    public boolean permits(int armor, int weapons, int total, boolean isArmor, boolean isWeapon) {
        return (maxTotal == 0 || total < maxTotal)
                && (!isArmor || maxArmor == 0 || armor < maxArmor)
                && (!isWeapon || maxWeapons == 0 || weapons < maxWeapons);
    }
    public static void validateEntry(int min, int max, double chance) {
        if (min < 1 || max < min || max > 64 || !Double.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("Amount must be 1–64 (min <= max); chance must be 0–1");
    }
}
