package org.enthusia.events.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.ExcessiveParameterList",
        "PMD.NPathComplexity"
})
public final class LootTableService {

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<EventType, Map<Integer, org.enthusia.events.domain.LootLimits>> cachedLimits = new EnumMap<>(EventType.class);
    private final Map<EventType, Map<Integer, List<LootEntry>>> entries = new EnumMap<>(EventType.class);

    public LootTableService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        File directory = new File(plugin.getDataFolder(), "loot");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create loot folder.");
        }
        this.file = new File(directory, "loot.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            writeDefaults();
        }
        entries.clear();
        cachedLimits.clear();
        for (EventType type : List.of(EventType.SKYWARS, EventType.BEDWARS)) {
            Map<Integer, org.enthusia.events.domain.LootLimits> tiers = new LinkedHashMap<>();
            for (int tier = 1; tier <= 3; tier++) tiers.put(tier, limits(type, tier));
            cachedLimits.put(type, tiers);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(yaml);
        ConfigurationSection root = yaml.getConfigurationSection("loot");
        if (root == null) {
            return;
        }
        for (String eventKey : root.getKeys(false)) {
            EventType type;
            try {
                type = EventType.parse(eventKey);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring unknown loot event type: " + eventKey);
                continue;
            }
            ConfigurationSection eventSection = root.getConfigurationSection(eventKey);
            if (eventSection == null) {
                continue;
            }
            Map<Integer, List<LootEntry>> tiers = entries.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
            for (String tierKey : eventSection.getKeys(false)) {
                int tier = parseTier(tierKey);
                ConfigurationSection tierSection = eventSection.getConfigurationSection(tierKey);
                if (tier <= 0 || tierSection == null) {
                    continue;
                }
                List<LootEntry> tierEntries = tiers.computeIfAbsent(tier, ignored -> new ArrayList<>());
                for (String id : tierSection.getKeys(false)) {
                    ConfigurationSection item = tierSection.getConfigurationSection(id);
                    if (item == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(item.getString("material", ""));
                    if (material == null || !material.isItem()) {
                        plugin.getLogger().warning("Invalid loot material at loot." + eventKey + "." + tierKey + "." + id);
                        continue;
                    }
                    try {
                        org.enthusia.events.domain.LootLimits.validateEntry(
                                item.getInt("min", item.getInt("amount", 1)), item.getInt("max", item.getInt("amount", 1)),
                                item.getDouble("chance", 1));
                    } catch (IllegalArgumentException invalid) {
                        plugin.getLogger().warning("Ignoring invalid loot entry: " + eventKey + "." + tierKey + "." + id);
                        continue;
                    }
                    tierEntries.add(new LootEntry(
                            material,
                            Math.max(1, item.getInt("min", item.getInt("amount", 1))),
                            Math.max(1, item.getInt("max", item.getInt("amount", 1))),
                            Math.max(0.0D, Math.min(1.0D, item.getDouble("chance", 1.0D))),
                            enchantments(item.getConfigurationSection("enchantments")),
                            effects(item.getConfigurationSection("effects")),
                            item.getString("display-name", "")
                    ));
                }
            }
        }
    }

    public record EntryView(String id, Material material, int min, int max, double chance) {}
    private String tierPath(EventType type, int tier) {
        if ((type != EventType.SKYWARS && type != EventType.BEDWARS) || tier < 1 || tier > 3)
            throw new IllegalArgumentException("Use SKYWARS or BEDWARS, tier 1-3");
        return "loot." + type.name() + ".tier" + tier;
    }
    public List<EntryView> inspect(EventType type, int tier) {
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection(tierPath(type, tier));
        if (section == null) return List.of();
        List<EntryView> result = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("material", ""));
            if (material != null) result.add(new EntryView(id, material,
                    item.getInt("min", item.getInt("amount", 1)), item.getInt("max", item.getInt("amount", 1)),
                    item.getDouble("chance", 1)));
        }
        return List.copyOf(result);
    }
    public org.enthusia.events.domain.LootLimits limits(EventType type, int tier) {
        var yaml = YamlConfiguration.loadConfiguration(file);
        String path = "limits." + type + ".tier" + tier;
        return new org.enthusia.events.domain.LootLimits(Math.max(0, yaml.getInt(path + ".armor", 0)),
                Math.max(0, yaml.getInt(path + ".weapons", 0)), Math.max(0, yaml.getInt(path + ".total", 0)));
    }
    public void edit(EventType type, int tier, String id, int min, int max, double chance) throws IOException {
        org.enthusia.events.domain.LootLimits.validateEntry(min, max, chance);
        if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid item id");
        var yaml = YamlConfiguration.loadConfiguration(file);
        String path = tierPath(type, tier) + "." + id;
        if (!yaml.isConfigurationSection(path)) throw new IllegalArgumentException("Unknown entry: " + id);
        yaml.set(path + ".min", min); yaml.set(path + ".max", max); yaml.set(path + ".chance", chance);
        saveAtomically(yaml); reload();
    }
    public void setLimits(EventType type, int tier, org.enthusia.events.domain.LootLimits limits) throws IOException {
        tierPath(type, tier);
        var yaml = YamlConfiguration.loadConfiguration(file);
        String path = "limits." + type + ".tier" + tier;
        yaml.set(path + ".armor", limits.maxArmor()); yaml.set(path + ".weapons", limits.maxWeapons());
        yaml.set(path + ".total", limits.maxTotal());
        saveAtomically(yaml); reload();
    }
    private void saveAtomically(YamlConfiguration yaml) throws IOException {
        var target = file.toPath();
        var temp = java.nio.file.Files.createTempFile(target.getParent(), "loot-", ".tmp");
        try {
            java.nio.file.Files.writeString(temp, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { java.nio.file.Files.deleteIfExists(temp); }
    }
    private boolean armor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
    private boolean weapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT || material == Material.MACE;
    }
    public List<ItemStack> roll(EventType type, int tier) {
        List<LootEntry> tierEntries = entries.getOrDefault(type, Map.of()).get(tier);
        if (tierEntries == null || tierEntries.isEmpty()) {
            tierEntries = entries.getOrDefault(type, Map.of()).get(1);
            tier = 1;
        }
        if (tierEntries == null || tierEntries.isEmpty()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>();
        var limits = cachedLimits.getOrDefault(type, Map.of()).getOrDefault(tier,
                new org.enthusia.events.domain.LootLimits(0, 0, 0));
        int armorCount = 0, weaponCount = 0;
        var shuffled = new ArrayList<>(tierEntries);
        java.util.Collections.shuffle(shuffled);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (LootEntry entry : shuffled) {
            if (random.nextDouble() >= entry.chance()) {
                continue;
            }
            boolean isArmor = armor(entry.material()), isWeapon = weapon(entry.material());
            if (!limits.permits(armorCount, weaponCount, items.size(), isArmor, isWeapon)) continue;
            if (isArmor) armorCount++;
            if (isWeapon) weaponCount++;
            int amount = entry.min() == entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
            items.add(entry.createItem(amount));
        }
        return items;
    }

    public void resetToDefaults() {
        if (file.exists()) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak-" + System.currentTimeMillis());
            if (!file.renameTo(backup)) {
                plugin.getLogger().warning("Could not back up loot/loot.yml before reset.");
                return;
            }
        }
        writeDefaults();
        reload();
    }

    private void writeDefaults() {
        YamlConfiguration yaml = defaultYaml();
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create loot/loot.yml: " + e.getMessage());
        }
    }

    private void mergeDefaults(YamlConfiguration yaml) {
        YamlConfiguration defaults = defaultYaml();
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !yaml.contains(key)) {
                yaml.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (yaml.getInt("config-version", 1) < defaults.getInt("config-version")) {
            yaml.set("config-version", defaults.getInt("config-version"));
            changed = true;
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to update loot/loot.yml defaults: " + e.getMessage());
            }
        }
    }

    private YamlConfiguration defaultYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 3);
        for (int tier = 1; tier <= 3; tier++) {
            yaml.set("limits.SKYWARS.tier" + tier + ".armor", 0);
            yaml.set("limits.SKYWARS.tier" + tier + ".weapons", 0);
            yaml.set("limits.SKYWARS.tier" + tier + ".total", 0);
        }

        set(yaml, "loot.SKYWARS.tier1.oak_planks", Material.OAK_PLANKS, 12, 28, 0.95D);
        set(yaml, "loot.SKYWARS.tier1.cobblestone", Material.COBBLESTONE, 12, 32, 0.90D);
        set(yaml, "loot.SKYWARS.tier1.spruce_planks", Material.SPRUCE_PLANKS, 8, 20, 0.45D);
        set(yaml, "loot.SKYWARS.tier1.steak", Material.COOKED_BEEF, 3, 7, 0.80D);
        set(yaml, "loot.SKYWARS.tier1.cooked_cod", Material.COOKED_COD, 2, 6, 0.40D);
        set(yaml, "loot.SKYWARS.tier1.cooked_salmon", Material.COOKED_SALMON, 2, 5, 0.30D);
        set(yaml, "loot.SKYWARS.tier1.bread", Material.BREAD, 2, 6, 0.50D);
        set(yaml, "loot.SKYWARS.tier1.wood_sword", Material.WOODEN_SWORD, 1, 1, 0.45D);
        set(yaml, "loot.SKYWARS.tier1.stone_sword", Material.STONE_SWORD, 1, 1, 0.50D);
        set(yaml, "loot.SKYWARS.tier1.stone_axe", Material.STONE_AXE, 1, 1, 0.38D);
        set(yaml, "loot.SKYWARS.tier1.stone_pickaxe", Material.STONE_PICKAXE, 1, 1, 0.55D);
        set(yaml, "loot.SKYWARS.tier1.iron_pickaxe", Material.IRON_PICKAXE, 1, 1, 0.08D);
        set(yaml, "loot.SKYWARS.tier1.bow", Material.BOW, 1, 1, 0.24D);
        set(yaml, "loot.SKYWARS.tier1.arrows", Material.ARROW, 4, 12, 0.40D);
        set(yaml, "loot.SKYWARS.tier1.leather_helmet", Material.LEATHER_HELMET, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier1.leather_boots", Material.LEATHER_BOOTS, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier1.chain_helmet", Material.CHAINMAIL_HELMET, 1, 1, 0.28D);
        set(yaml, "loot.SKYWARS.tier1.chain_chest", Material.CHAINMAIL_CHESTPLATE, 1, 1, 0.25D);
        set(yaml, "loot.SKYWARS.tier1.chain_leggings", Material.CHAINMAIL_LEGGINGS, 1, 1, 0.25D);
        set(yaml, "loot.SKYWARS.tier1.chain_boots", Material.CHAINMAIL_BOOTS, 1, 1, 0.28D);
        set(yaml, "loot.SKYWARS.tier1.iron_helmet", Material.IRON_HELMET, 1, 1, 0.08D);
        set(yaml, "loot.SKYWARS.tier1.iron_boots", Material.IRON_BOOTS, 1, 1, 0.08D);
        set(yaml, "loot.SKYWARS.tier1.coal", Material.COAL, 2, 8, 0.35D);
        set(yaml, "loot.SKYWARS.tier1.iron_ingots", Material.IRON_INGOT, 1, 3, 0.28D);
        set(yaml, "loot.SKYWARS.tier1.gold_ingots", Material.GOLD_INGOT, 1, 3, 0.18D);
        set(yaml, "loot.SKYWARS.tier1.diamond", Material.DIAMOND, 1, 1, 0.04D);

        set(yaml, "loot.SKYWARS.tier2.oak_planks", Material.OAK_PLANKS, 16, 36, 0.95D);
        set(yaml, "loot.SKYWARS.tier2.cobblestone", Material.COBBLESTONE, 16, 40, 0.90D);
        set(yaml, "loot.SKYWARS.tier2.steak", Material.COOKED_BEEF, 4, 9, 0.78D);
        set(yaml, "loot.SKYWARS.tier2.cooked_cod", Material.COOKED_COD, 3, 7, 0.38D);
        set(yaml, "loot.SKYWARS.tier2.iron_sword", Material.IRON_SWORD, 1, 1, 0.55D);
        set(yaml, "loot.SKYWARS.tier2.iron_axe", Material.IRON_AXE, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier2.iron_pickaxe", Material.IRON_PICKAXE, 1, 1, 0.45D);
        set(yaml, "loot.SKYWARS.tier2.bow", Material.BOW, 1, 1, 0.50D);
        set(yaml, "loot.SKYWARS.tier2.arrows", Material.ARROW, 8, 20, 0.72D);
        set(yaml, "loot.SKYWARS.tier2.spectral_arrows", Material.SPECTRAL_ARROW, 3, 8, 0.22D);
        setEffect(yaml, "loot.SKYWARS.tier2.poison_arrows", Material.TIPPED_ARROW, 2, 5, 0.16D, "poison", 5, 0, "&aPoison Arrow");
        set(yaml, "loot.SKYWARS.tier2.iron_helmet", Material.IRON_HELMET, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier2.iron_chest", Material.IRON_CHESTPLATE, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier2.iron_leggings", Material.IRON_LEGGINGS, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier2.iron_boots", Material.IRON_BOOTS, 1, 1, 0.35D);
        set(yaml, "loot.SKYWARS.tier2.diamond_helmet", Material.DIAMOND_HELMET, 1, 1, 0.10D);
        set(yaml, "loot.SKYWARS.tier2.diamond_boots", Material.DIAMOND_BOOTS, 1, 1, 0.10D);
        set(yaml, "loot.SKYWARS.tier2.diamonds", Material.DIAMOND, 1, 3, 0.32D);
        set(yaml, "loot.SKYWARS.tier2.iron_ingots", Material.IRON_INGOT, 2, 6, 0.50D);
        set(yaml, "loot.SKYWARS.tier2.gold_ingots", Material.GOLD_INGOT, 2, 6, 0.42D);
        set(yaml, "loot.SKYWARS.tier2.lapis", Material.LAPIS_LAZULI, 4, 12, 0.45D);
        set(yaml, "loot.SKYWARS.tier2.experience_bottles", Material.EXPERIENCE_BOTTLE, 3, 8, 0.45D);
        set(yaml, "loot.SKYWARS.tier2.golden_apple", Material.GOLDEN_APPLE, 1, 1, 0.18D);
        setEnchant(yaml, "loot.SKYWARS.tier2.sharp_iron_sword", Material.IRON_SWORD, 1, 1, 0.10D, "sharpness", 1);
        setEnchant(yaml, "loot.SKYWARS.tier2.efficiency_iron_pickaxe", Material.IRON_PICKAXE, 1, 1, 0.16D, "efficiency", 2);
        setEnchant(yaml, "loot.SKYWARS.tier2.protection_iron_chest", Material.IRON_CHESTPLATE, 1, 1, 0.09D, "protection", 1);

        set(yaml, "loot.SKYWARS.tier3.oak_planks", Material.OAK_PLANKS, 20, 48, 0.95D);
        set(yaml, "loot.SKYWARS.tier3.cobblestone", Material.COBBLESTONE, 20, 48, 0.90D);
        set(yaml, "loot.SKYWARS.tier3.steak", Material.COOKED_BEEF, 5, 12, 0.75D);
        set(yaml, "loot.SKYWARS.tier3.cooked_salmon", Material.COOKED_SALMON, 3, 8, 0.35D);
        set(yaml, "loot.SKYWARS.tier3.diamond_sword", Material.DIAMOND_SWORD, 1, 1, 0.55D);
        set(yaml, "loot.SKYWARS.tier3.diamond_axe", Material.DIAMOND_AXE, 1, 1, 0.36D);
        set(yaml, "loot.SKYWARS.tier3.diamond_pickaxe", Material.DIAMOND_PICKAXE, 1, 1, 0.42D);
        set(yaml, "loot.SKYWARS.tier3.bow", Material.BOW, 1, 1, 0.55D);
        setEnchant(yaml, "loot.SKYWARS.tier3.power_bow", Material.BOW, 1, 1, 0.18D, "power", 1);
        set(yaml, "loot.SKYWARS.tier3.arrows", Material.ARROW, 12, 28, 0.78D);
        set(yaml, "loot.SKYWARS.tier3.spectral_arrows", Material.SPECTRAL_ARROW, 4, 12, 0.28D);
        setEffect(yaml, "loot.SKYWARS.tier3.poison_arrows", Material.TIPPED_ARROW, 3, 7, 0.20D, "poison", 5, 0, "&aPoison Arrow");
        set(yaml, "loot.SKYWARS.tier3.diamond_helmet", Material.DIAMOND_HELMET, 1, 1, 0.30D);
        set(yaml, "loot.SKYWARS.tier3.diamond_chest", Material.DIAMOND_CHESTPLATE, 1, 1, 0.28D);
        set(yaml, "loot.SKYWARS.tier3.diamond_leggings", Material.DIAMOND_LEGGINGS, 1, 1, 0.28D);
        set(yaml, "loot.SKYWARS.tier3.diamond_boots", Material.DIAMOND_BOOTS, 1, 1, 0.30D);
        set(yaml, "loot.SKYWARS.tier3.netherite_sword", Material.NETHERITE_SWORD, 1, 1, 0.035D);
        set(yaml, "loot.SKYWARS.tier3.netherite_pickaxe", Material.NETHERITE_PICKAXE, 1, 1, 0.025D);
        set(yaml, "loot.SKYWARS.tier3.netherite_helmet", Material.NETHERITE_HELMET, 1, 1, 0.018D);
        set(yaml, "loot.SKYWARS.tier3.netherite_boots", Material.NETHERITE_BOOTS, 1, 1, 0.018D);
        set(yaml, "loot.SKYWARS.tier3.diamonds", Material.DIAMOND, 2, 6, 0.52D);
        set(yaml, "loot.SKYWARS.tier3.iron_ingots", Material.IRON_INGOT, 4, 10, 0.55D);
        set(yaml, "loot.SKYWARS.tier3.gold_ingots", Material.GOLD_INGOT, 4, 10, 0.48D);
        set(yaml, "loot.SKYWARS.tier3.lapis", Material.LAPIS_LAZULI, 8, 18, 0.58D);
        set(yaml, "loot.SKYWARS.tier3.experience_bottles", Material.EXPERIENCE_BOTTLE, 6, 14, 0.62D);
        set(yaml, "loot.SKYWARS.tier3.golden_apples", Material.GOLDEN_APPLE, 1, 2, 0.34D);
        set(yaml, "loot.SKYWARS.tier3.ender_pearl", Material.ENDER_PEARL, 1, 1, 0.16D);
        setEffect(yaml, "loot.SKYWARS.tier3.healing_potion", Material.POTION, 1, 1, 0.22D, "instant_health", 1, 0, "&dHealing Potion");
        setEffect(yaml, "loot.SKYWARS.tier3.regeneration_potion", Material.POTION, 1, 1, 0.18D, "regeneration", 8, 0, "&dRegeneration Potion");
        setEnchant(yaml, "loot.SKYWARS.tier3.sharpness_book", Material.ENCHANTED_BOOK, 1, 1, 0.18D, "sharpness", 1);
        setEnchant(yaml, "loot.SKYWARS.tier3.protection_book", Material.ENCHANTED_BOOK, 1, 1, 0.18D, "protection", 1);
        setEnchant(yaml, "loot.SKYWARS.tier3.power_book", Material.ENCHANTED_BOOK, 1, 1, 0.15D, "power", 1);

        set(yaml, "loot.BEDWARS.tier1.wool", Material.WHITE_WOOL, 16, 32, 1.0D);
        set(yaml, "loot.BEDWARS.tier1.iron", Material.IRON_INGOT, 8, 20, 1.0D);
        set(yaml, "loot.BEDWARS.tier1.gold", Material.GOLD_INGOT, 2, 6, 0.80D);
        return yaml;
    }

    private void set(YamlConfiguration yaml, String path, Material material, int min, int max, double chance) {
        yaml.set(path + ".material", material.name());
        yaml.set(path + ".min", min);
        yaml.set(path + ".max", max);
        yaml.set(path + ".chance", chance);
    }

    private void setEnchant(YamlConfiguration yaml, String path, Material material, int min, int max, double chance,
                            String enchantment, int level) {
        set(yaml, path, material, min, max, chance);
        yaml.set(path + ".enchantments." + enchantment, level);
    }

    private void setEffect(YamlConfiguration yaml, String path, Material material, int min, int max, double chance,
                           String effect, int durationSeconds, int amplifier, String displayName) {
        set(yaml, path, material, min, max, chance);
        yaml.set(path + ".effects." + effect + ".duration-seconds", durationSeconds);
        yaml.set(path + ".effects." + effect + ".amplifier", amplifier);
        yaml.set(path + ".display-name", displayName);
    }

    private Map<Enchantment, Integer> enchantments(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (enchantment == null) {
                plugin.getLogger().warning("Ignoring unknown loot enchantment: " + key);
                continue;
            }
            result.put(enchantment, Math.max(1, section.getInt(key, 1)));
        }
        return result;
    }

    private List<LootEffect> effects(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<LootEffect> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Ignoring unknown loot potion effect: " + key);
                continue;
            }
            ConfigurationSection effect = section.getConfigurationSection(key);
            int seconds = effect == null ? 5 : Math.max(1, effect.getInt("duration-seconds", 5));
            int amplifier = effect == null ? 0 : Math.max(0, effect.getInt("amplifier", 0));
            result.add(new LootEffect(type, seconds * 20, amplifier));
        }
        return result;
    }

    private int parseTier(String value) {
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return -1;
        }
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private record LootEntry(Material material, int min, int max, double chance,
                             Map<Enchantment, Integer> enchantments, List<LootEffect> effects,
                             String displayName) {

        private ItemStack createItem(int amount) {
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
            if (displayName != null && !displayName.isBlank()) {
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
            }
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (meta instanceof EnchantmentStorageMeta storageMeta) {
                    storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                } else {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }
            if (meta instanceof PotionMeta potionMeta) {
                for (LootEffect effect : effects) {
                    potionMeta.addCustomEffect(new PotionEffect(effect.type(), effect.durationTicks(), effect.amplifier()), true);
                }
            }
            item.setItemMeta(meta);
            return item;
        }
    }

    private record LootEffect(PotionEffectType type, int durationTicks, int amplifier) {
    }
}
