package net.duelcore.plugin.commands;

import net.duelcore.plugin.DuelCorePlugin;
import net.duelcore.plugin.profile.ProfileMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Backs both /profile [player] and /stats [player]. */
public class ProfileCommand implements CommandExecutor {

    private final DuelCorePlugin plugin;
    private final ProfileMenu menu;

    public ProfileCommand(DuelCorePlugin plugin) {
        this.plugin = plugin;
        this.menu = new ProfileMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Only players can view stats in-game.");
            return true;
        }

        String targetName = args.length > 0 ? args[0] : viewer.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid;
            String storedUsername;
            try (Connection conn = plugin.database().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, username FROM dc_players WHERE LOWER(username) = LOWER(?)")) {
                ps.setString(1, targetName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        viewer.sendMessage(plugin.config().color(
                                "<red>No player named '" + targetName + "' has joined this server before."));
                        return;
                    }
                    uuid = UUID.fromString(rs.getString("uuid"));
                    storedUsername = rs.getString("username");
                }
            } catch (Exception e) {
                viewer.sendMessage(plugin.config().color("<red>Something went wrong looking up that player."));
                return;
            }

            Map<String, String> kitTiers = new LinkedHashMap<>();
            double overallPoints = 0;
            try (Connection conn = plugin.database().getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT overall_points FROM dc_players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) overallPoints = rs.getDouble("overall_points");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT kit_id, tier FROM dc_kit_stats WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) kitTiers.put(rs.getString("kit_id"), rs.getString("tier"));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load profile data: " + e.getMessage());
            }

            int rank = plugin.leaderboard().getOverallRank(uuid);
            boolean discordLinked = plugin.discord() != null
                    && plugin.discord().verification() != null
                    && plugin.discord().verification().getDiscordId(uuid).isPresent();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

            ProfileMenu.ProfileData data = new ProfileMenu.ProfileData(
                    uuid, storedUsername, offlinePlayer, overallPoints, rank, discordLinked, kitTiers);

            Bukkit.getScheduler().runTask(plugin, () -> menu.open(viewer, data));
        });

        return true;
    }
}
