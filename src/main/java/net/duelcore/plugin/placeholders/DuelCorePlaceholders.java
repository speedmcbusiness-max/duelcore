package net.duelcore.plugin.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers as "duelcore" so placeholders look like %duelcore_<x>%.
 *
 *   %duelcore_tier%                 highest tier the player holds in any kit
 *   %duelcore_tier_<kit>%           tier in one specific kit, e.g. %duelcore_tier_crystal%
 *   %duelcore_overall%              overall leaderboard rank (e.g. "3", or "Unranked")
 *   %duelcore_points%               total points (sum of every kit's tier points-value)
 *   %duelcore_point_<kit>%          points contributed by one specific kit
 *
 * These read straight from the database so they work for offline players too
 * (e.g. in join messages, or from console for a player who isn't online).
 */
public class DuelCorePlaceholders extends PlaceholderExpansion {

    private final DuelCorePlugin plugin;

    public DuelCorePlaceholders(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "duelcore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DuelCore";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "";
        UUID uuid = player.getUniqueId();

        if (params.equals("overall")) {
            int rank = plugin.leaderboard().getOverallRank(uuid);
            return rank > 0 ? String.valueOf(rank) : "Unranked";
        }

        if (params.equals("points")) {
            return formatPoints(getOverallPoints(uuid));
        }

        if (params.startsWith("point_")) {
            String kitId = params.substring("point_".length());
            return formatPoints(getKitPointsValue(uuid, kitId));
        }

        if (params.equals("tier")) {
            Map<String, String> tiers = getAllTiers(uuid);
            String highest = plugin.tiers().getHighestTier(tiers);
            return highest.equals("NONE") ? "Untested" : stripColor(plugin.tiers().getTierDisplay(highest));
        }

        if (params.startsWith("tier_")) {
            String kitId = params.substring("tier_".length());
            String tier = getAllTiers(uuid).get(kitId);
            if (tier == null) return "Untested";
            return stripColor(plugin.tiers().getTierDisplay(tier));
        }

        return null;
    }

    private String formatPoints(double points) {
        if (points == Math.floor(points)) return String.valueOf((long) points);
        return String.valueOf(points);
    }

    private double getOverallPoints(UUID uuid) {
        try (Connection conn = plugin.database().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT overall_points FROM dc_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("overall_points");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Placeholder DB error: " + e.getMessage());
        }
        return 0;
    }

    private double getKitPointsValue(UUID uuid, String kitId) {
        String tier = getAllTiers(uuid).get(kitId);
        if (tier == null) return 0;
        return plugin.tiers().getPointsValue(tier);
    }

    private Map<String, String> getAllTiers(UUID uuid) {
        Map<String, String> tiers = new LinkedHashMap<>();
        try (Connection conn = plugin.database().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT kit_id, tier FROM dc_kit_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tiers.put(rs.getString("kit_id"), rs.getString("tier"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Placeholder DB error: " + e.getMessage());
        }
        return tiers;
    }

    private String stripColor(String miniMessage) {
        return miniMessage.replaceAll("<[^>]*>", "");
    }
}
