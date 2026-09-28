package net.duelcore.plugin.player;

import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final DuelCorePlugin plugin;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerProfile getProfile(UUID uuid) {
        return cache.get(uuid);
    }

    /** Loads (or creates) a player's profile asynchronously, then caches it. */
    public void loadAsync(UUID uuid, String username, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = loadOrCreate(uuid, username);
            cache.put(uuid, profile);
            if (onDone != null) {
                Bukkit.getScheduler().runTask(plugin, onDone);
            }
        });
    }

    public void unload(UUID uuid) {
        PlayerProfile profile = cache.remove(uuid);
        if (profile != null) {
            saveAsync(profile);
        }
    }

    private PlayerProfile loadOrCreate(UUID uuid, String username) {
        try (Connection conn = plugin.database().getConnection()) {
            PlayerProfile profile;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM dc_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        profile = new PlayerProfile(uuid, username);
                        profile.setCurrency(rs.getLong("currency"));
                        profile.setOverallPoints(rs.getDouble("overall_points"));
                        profile.setCountryCode(rs.getString("country_code"));
                        profile.setClientBrand(rs.getString("client_brand"));
                    } else {
                        profile = new PlayerProfile(uuid, username);
                        long startingBalance = plugin.getConfig().getLong("currency.starting-balance", 0);
                        profile.setCurrency(startingBalance);
                        try (PreparedStatement insert = conn.prepareStatement(
                                "INSERT INTO dc_players (uuid, username, currency, last_seen) VALUES (?, ?, ?, ?)")) {
                            insert.setString(1, uuid.toString());
                            insert.setString(2, username);
                            insert.setLong(3, startingBalance);
                            insert.setLong(4, System.currentTimeMillis());
                            insert.executeUpdate();
                        }
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT kit_id, tier, points FROM dc_kit_stats WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        profile.setTier(rs.getString("kit_id"), rs.getString("tier"));
                        profile.setKitPoints(rs.getString("kit_id"), rs.getDouble("points"));
                    }
                }
            }
            return profile;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player " + uuid + ": " + e.getMessage());
            return new PlayerProfile(uuid, username);
        }
    }

    public void saveAsync(PlayerProfile profile) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> save(profile));
    }

    private void save(PlayerProfile profile) {
        try (Connection conn = plugin.database().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE dc_players SET username = ?, currency = ?, overall_points = ?, last_seen = ? WHERE uuid = ?")) {
                ps.setString(1, profile.getUsername());
                ps.setLong(2, profile.getCurrency());
                ps.setDouble(3, profile.getOverallPoints());
                ps.setLong(4, System.currentTimeMillis());
                ps.setString(5, profile.getUuid().toString());
                ps.executeUpdate();
            }
            for (Map.Entry<String, String> entry : profile.getAllTiers().entrySet()) {
                String kitId = entry.getKey();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dc_kit_stats (uuid, kit_id, tier, points) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE tier = VALUES(tier), points = VALUES(points)")) {
                    ps.setString(1, profile.getUuid().toString());
                    ps.setString(2, kitId);
                    ps.setString(3, entry.getValue());
                    ps.setDouble(4, profile.getKitPoints(kitId));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player " + profile.getUuid() + ": " + e.getMessage());
        }
    }
}
