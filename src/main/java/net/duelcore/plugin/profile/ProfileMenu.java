package net.duelcore.plugin.profile;

import net.duelcore.plugin.DuelCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A chest-GUI equivalent of the MCPvP-style stats screen: a head item with
 * the career overview (matches/rank/points/discord link) plus one icon per
 * kit showing its tier and points contribution.
 *
 * The screenshot you sent uses a full 3D "museum room" of floating item/text
 * displays around an armor stand rather than an inventory screen — that's a
 * separate, heavier subsystem (Display entities positioned in world space,
 * a dedicated instance/region per viewer, camera framing, etc). This GUI
 * gives players the same information right now; say the word and I'll build
 * the exact 3D version as its own batch on top of this.
 */
public class ProfileMenu {

    private final DuelCorePlugin plugin;

    public ProfileMenu(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, ProfileData data) {
        Inventory inv = Bukkit.createInventory(null, 54,
                plugin.config().color("<#ff5555><b>" + data.username() + "'s Stats"));

        inv.setItem(4, buildHeadItem(data));

        int slot = 19;
        for (String kitId : plugin.getConfig().getConfigurationSection("kits").getKeys(false)) {
            if (slot > 43) break; // ran out of room; kit list is paginated in a later batch if needed
            inv.setItem(slot, buildKitItem(kitId, data));
            slot++;
        }

        viewer.openInventory(inv);
    }

    private ItemStack buildHeadItem(ProfileData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(data.offlinePlayer());
        meta.displayName(plugin.config().color("<#ffaa00><b>" + data.username()));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.config().color("<gray>Overall Rank: <white>" +
                (data.overallRank() > 0 ? "#" + data.overallRank() : "Unranked")));
        lore.add(plugin.config().color("<gray>Overall Points: <white>" + formatPoints(data.overallPoints())));
        lore.add(Component.empty());
        lore.add(plugin.config().color(data.discordLinked()
                ? "<#5865F2>✔ Discord Linked"
                : "<gray>✘ Discord not linked <dark_gray>(/verify)"));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack buildKitItem(String kitId, ProfileData data) {
        ConfigurationSection kitCfg = plugin.getConfig().getConfigurationSection("kits." + kitId);
        Material icon = Material.matchMaterial(kitCfg.getString("icon", "STONE"));
        if (icon == null) icon = Material.STONE;

        String tier = data.kitTiers().getOrDefault(kitId, "NONE");
        double pointsValue = tier.equals("NONE") ? 0 : plugin.tiers().getPointsValue(tier);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.config().color(plugin.tiers().getKitDisplay(kitId)));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.config().color(tier.equals("NONE")
                ? "<gray>Tier: <dark_gray>Untested"
                : "<gray>Tier: " + plugin.tiers().getTierDisplay(tier)));
        lore.add(plugin.config().color("<gray>Points: <white>" + formatPoints(pointsValue)));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String formatPoints(double points) {
        return points == Math.floor(points) ? String.valueOf((long) points) : String.valueOf(points);
    }

    /** Immutable snapshot of everything the profile GUI needs, gathered off-thread. */
    public record ProfileData(
            UUID uuid,
            String username,
            OfflinePlayer offlinePlayer,
            double overallPoints,
            int overallRank,
            boolean discordLinked,
            Map<String, String> kitTiers
    ) {}
}
