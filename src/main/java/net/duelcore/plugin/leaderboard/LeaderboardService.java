package net.duelcore.plugin.leaderboard;

import net.duelcore.plugin.DuelCorePlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LeaderboardService {

    private final DuelCorePlugin plugin;

    public LeaderboardService(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** 1-based overall rank by summed tier points-value across all kits. Returns -1 if not ranked. */
    public int getOverallRank(UUID uuid) {
        try (Connection conn = plugin.database().getConnection()) {
            Double myPoints = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT overall_points FROM dc_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return -1;
                    myPoints = rs.getDouble("overall_points");
                    if (myPoints <= 0) return -1;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS higher FROM dc_players WHERE overall_points > ?")) {
                ps.setDouble(1, myPoints);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt("higher") + 1;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to compute overall rank: " + e.getMessage());
            return -1;
        }
    }

    /** 1-based rank within a single kit's leaderboard, ordered by tier then points-value. */
    public int getKitRank(UUID uuid, String kitId) {
        try (Connection conn = plugin.database().getConnection()) {
            String myTier;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tier FROM dc_kit_stats WHERE uuid = ? AND kit_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return -1;
                    myTier = rs.getString("tier");
                }
            }
            double myValue = plugin.tiers().getPointsValue(myTier);
            if (myValue <= 0) return -1;

            // Count players in this kit whose tier's points-value is strictly higher.
            // Pulled into Java since points-value lives in config, not the DB.
            List<String> higherTiers = new ArrayList<>();
            for (String tierId : plugin.getConfig().getStringList("tiers.order")) {
                if (plugin.tiers().getPointsValue(tierId) > myValue) higherTiers.add(tierId);
            }
            if (higherTiers.isEmpty()) return 1;

            String placeholders = String.join(",", higherTiers.stream().map(t -> "?").toList());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS higher FROM dc_kit_stats WHERE kit_id = ? AND tier IN (" + placeholders + ")")) {
                ps.setString(1, kitId);
                for (int i = 0; i < higherTiers.size(); i++) {
                    ps.setString(i + 2, higherTiers.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt("higher") + 1;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to compute kit rank: " + e.getMessage());
            return -1;
        }
    }
}
