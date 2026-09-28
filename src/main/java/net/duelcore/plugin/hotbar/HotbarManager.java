package net.duelcore.plugin.hotbar;

import net.duelcore.plugin.DuelCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HotbarManager {

    public static final NamespacedKey HOTBAR_ID_KEY = new NamespacedKey("duelcore", "hotbar_item");

    private final DuelCorePlugin plugin;
    private final Map<String, ItemStack> builtItems = new HashMap<>();
    private final Map<String, Integer> slots = new HashMap<>();

    public HotbarManager(DuelCorePlugin plugin) {
        this.plugin = plugin;
        build();
    }

    public void build() {
        builtItems.clear();
        slots.clear();
        if (!plugin.getConfig().getBoolean("hotbar.enabled", true)) return;

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("hotbar.items");
        if (items == null) return;

        for (String id : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(id);
            if (section == null) continue;

            int slot = section.getInt("slot", 0);
            Material material = Material.matchMaterial(section.getString("material", "STONE"));
            if (material == null) material = Material.STONE;

            Component name = plugin.config().color(section.getString("name", id));
            List<Component> lore = section.getStringList("lore").stream()
                    .map(plugin.config()::color)
                    .toList();
            boolean glow = section.getBoolean("glow", false);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(HOTBAR_ID_KEY, PersistentDataType.STRING, id);
            if (glow) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            }
            item.setItemMeta(meta);

            builtItems.put(id, item);
            slots.put(id, slot);
        }
    }

    public void giveHotbar(Player player) {
        player.getInventory().clear();
        for (Map.Entry<String, ItemStack> entry : builtItems.entrySet()) {
            int slot = slots.getOrDefault(entry.getKey(), 0);
            player.getInventory().setItem(slot, entry.getValue().clone());
        }
    }

    /** Returns the hotbar item id (e.g. "queue", "duel") for a clicked item, or null. */
    public String getIdFor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(HOTBAR_ID_KEY, PersistentDataType.STRING);
    }
}
