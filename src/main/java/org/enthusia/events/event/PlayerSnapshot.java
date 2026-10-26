package org.enthusia.events.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;

public record PlayerSnapshot(
        Location location,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offhand,
        double maxHealth,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int totalExperience,
        int experienceLevel,
        float experienceProgress,
        GameMode gameMode,
        Collection<PotionEffect> potionEffects,
        boolean allowFlight,
        boolean flying,
        float walkSpeed,
        float flySpeed
) {

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
                player.getLocation().clone(),
                copyItems(player.getInventory().getContents()),
                copyItems(player.getInventory().getArmorContents()),
                copyItem(player.getInventory().getItemInOffHand()),
                captureMaxHealth(player),
                Math.min(player.getHealth(), player.getMaxHealth()),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getTotalExperience(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed()
        );
    }

    private static ItemStack copyItem(ItemStack item) { return item == null ? null : item.clone(); }
    private static ItemStack[] copyItems(ItemStack[] items) {
        ItemStack[] copied = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copied[i] = copyItem(items[i]);
        return copied;
    }

    private static double captureMaxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double value = attribute == null ? player.getMaxHealth() : attribute.getBaseValue();
        return value > 0.0D ? value : 20.0D;
    }
}
