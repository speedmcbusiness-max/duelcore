package net.duelcore.plugin.config;

import net.duelcore.plugin.DuelCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Central place for reading config.yml and turning any string field into a
 * fully-colored Component. Every text field in config.yml is MiniMessage,
 * which natively supports hex colors ( <#rrggbb> ) and gradients.
 */
public class ConfigManager {

    private final DuelCorePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration config;

    public ConfigManager(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    /** Parses a MiniMessage string (hex codes, gradients, etc.) into a Component. */
    public Component color(String miniMessageText) {
        if (miniMessageText == null) return Component.empty();
        return miniMessage.deserialize(miniMessageText).decoration(TextDecoration.ITALIC, false);
    }

    /** Reads a config path as a colored Component, with a fallback if missing. */
    public Component colorFromPath(String path, String fallback) {
        String raw = config.getString(path, fallback);
        return color(raw);
    }

    public List<Component> colorList(String path) {
        return config.getStringList(path).stream().map(this::color).collect(Collectors.toList());
    }

    /** Legacy '&' or '%%' placeholder replacement helper, applied before MiniMessage parsing. */
    public Component colorWithPlaceholders(String rawTemplate, String... placeholderPairs) {
        String text = rawTemplate;
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            text = text.replace(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        return color(text);
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }
}
