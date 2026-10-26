package org.enthusia.events.event;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.EventConfigService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventRegistry {

    private final EnthusiaEventsPlugin plugin;
    private final EventConfigService eventConfigService;
    private final Map<EventType, EventDefinition> definitions = new EnumMap<>(EventType.class);

    public EventRegistry(EnthusiaEventsPlugin plugin, EventConfigService eventConfigService) {
        this.plugin = plugin;
        this.eventConfigService = eventConfigService;
        reload();
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection defaults = plugin.getConfig().getConfigurationSection("events.defaults");
        boolean allowTeleport = defaults != null && defaults.getBoolean("allow-teleport", false);
        boolean allowPearl = defaults != null && defaults.getBoolean("allow-ender-pearl", false);
        boolean usesKits = defaults != null && defaults.getBoolean("uses-kits", false);
        for (String raw : plugin.getConfig().getStringList("events.enabled")) {
            EventType type = EventType.parse(raw);
            definitions.put(type, new EventDefinition(
                    type,
                    displayName(type),
                    iconFor(type),
                    descriptionFor(type),
                    allowTeleport,
                    allowPearlFor(type, allowPearl),
                    usesKits
            ));
        }
    }

    private boolean allowPearlFor(EventType type, boolean defaultValue) {
        if (type == EventType.KNOCKBACK_FFA || type == EventType.BEDWARS) {
            return true;
        }
        return defaultValue;
    }

    public List<EventDefinition> all() {
        return definitions.values().stream().filter(d -> d.type() != EventType.SPEEDBUILDERS).toList();
    }

    public List<EventDefinition> randomChoices(int amount) {
        List<EventDefinition> pool = new ArrayList<>(all());
        List<EventDefinition> out = new ArrayList<>();
        while (!pool.isEmpty() && out.size() < amount) {
            out.add(pool.remove(ThreadLocalRandom.current().nextInt(pool.size())));
        }
        return out;
    }

    public EventDefinition definition(EventType type) {
        return definitions.get(type);
    }

    private String displayName(EventType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String descriptionFor(EventType type) {
        return eventConfigService.description(type);
    }

    private Material iconFor(EventType type) {
        return eventConfigService.icon(type);
    }
}
