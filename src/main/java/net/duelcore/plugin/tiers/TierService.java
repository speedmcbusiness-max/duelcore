package net.duelcore.plugin.tiers;

import net.duelcore.plugin.DuelCorePlugin;
import net.duelcore.plugin.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tiers are set manually by staff via /result — this class does not grind
 * tiers up from points anymore. It applies a tier to one kit, unlocks the
 * matching trim, recomputes the player's overall points (sum of every kit's
 * tier points-value), and fires the Discord announcement. Works whether the
 * target player is online or offline.
 */
public class TierService {

    private final DuelCorePlugin plugin;

    public TierService(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isValidTier(String tierId) {
        return plugin.getConfig().getStringList("tiers.order").contains(tierId);
    }

    public boolean isValidKit(String kitId) {
        return plugin.getConfig().getConfigurationSection("kits." + kitId) != null;
    }

    public double getPointsValue(String tierId) {
        return plugin.getConfig().getDouble("tiers.definitions." + tierId + ".points-value", 0);
    }

    public String getTierDisplay(String tierId) {
        return plugin.getConfig().getString("tiers.definitions." + tierId + ".display", tierId);
    }

    public String getKitDisplay(String kitId) {
        return plugin.getConfig().getString("kits." + kitId + ".display-name", kitId);
    }

    public Optional<String> getTrimMaterial(String tierId) {
        return Optional.ofNullable(plugin.getConfig().getString("tiers.definitions." + tierId + ".trim-material"));
    }

    public List<String> getTrimPatterns(String tierId) {
        return plugin.getConfig().getStringList("tiers.definitions." + tierId + ".trim-patterns");
    }

    /** Highest tier (first in tiers.order that the player holds in any kit). */
    public String getHighestTier(java.util.Map<String, String> allKitTiers) {
        List<String> order = plugin.getConfig().getStringList("tiers.order");
        for (String tierId : order) {
            if (allKitTiers.containsValue(tierId)) return tierId;
        }
        return "NONE";
    }

    public interface ResultCallback {
        void onResult(Result result);
    }

    public enum Result { SUCCESS, UNKNOWN_PLAYER, INVALID_KIT, INVALID_TIER, ERROR }

    /**
     * Sets a player's tier for one kit. This is the entry point for
     * /result <player> <gamemode> <tier>. Works for offline players too —
     * it goes straight to the database and only touches the live cache if
     * the player happens to be online.
     */
    public void setTier(String username, String kitId, String tierId, ResultCallback callback) {
        if (!isValidKit(kitId)) {
            callback.onResult(Result.INVALID_KIT);
            return;
        }
        if (!isValidTier(tierId)) {
            callback.onResult(Result.INVALID_TIER);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Result result = applyTier(username, kitId, tierId);
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(result));
        });
    }

    private Result applyTier(String username, String kitId, String tierId) {
        try (Connection conn = plugin.database().getConnection()) {
            UUID uuid;
            String storedUsername;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, username FROM dc_players WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Result.UNKNOWN_PLAYER;
                    uuid = UUID.fromString(rs.getString("uuid"));
                    storedUsername = rs.getString("username");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO dc_kit_stats (uuid, kit_id, tier, points) VALUES (?, ?, ?, 0) " +
                    "ON DUPLICATE KEY UPDATE tier = VALUES(tier)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitId);
                ps.setString(3, tierId);
                ps.executeUpdate();
            }

            double overall = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tier FROM dc_kit_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        overall += getPointsValue(rs.getString("tier"));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE dc_players SET overall_points = ? WHERE uuid = ?")) {
                ps.setDouble(1, overall);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }

            unlockTrim(conn, uuid, tierId);
            updateLiveCache(uuid, kitId, tierId, overall);
            announce(uuid, storedUsername, kitId, tierId);
            return Result.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set tier: " + e.getMessage());
            return Result.ERROR;
        }
    }

    private void unlockTrim(Connection conn, UUID uuid, String tierId) throws SQLException {
        Optional<String> material = getTrimMaterial(tierId);
        List<String> patterns = getTrimPatterns(tierId);
        long now = System.currentTimeMillis();

        if (material.isPresent()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO dc_trim_unlocks (uuid, trim_id, unlocked_at) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "material:" + material.get());
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        }
        for (String pattern : patterns) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO dc_trim_unlocks (uuid, trim_id, unlocked_at) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "pattern:" + pattern);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        }
    }

    private void updateLiveCache(UUID uuid, String kitId, String tierId, double overall) {
        PlayerProfile profile = plugin.players().getProfile(uuid);
        if (profile != null) {
            profile.setTier(kitId, tierId);
            profile.setOverallPoints(overall);
        }
    }

    private void announce(UUID uuid, String username, String kitId, String tierId) {
        if (plugin.discord() == null) return;
        String kitDisplay = stripColor(getKitDisplay(kitId));
        String tierDisplay = stripColor(getTierDisplay(tierId));
        String discordId = plugin.discord().verification().getDiscordId(uuid).orElse(null);
        plugin.discord().announceTierUp(username, kitDisplay, tierDisplay, discordId);

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(plugin.config().color(
                    "<#55ff55>Your <white>" + kitDisplay + "<#55ff55> tier was set to <white>" + tierDisplay));
        }
    }

    private String stripColor(String miniMessage) {
        return miniMessage.replaceAll("<[^>]*>", "");
    }
}
