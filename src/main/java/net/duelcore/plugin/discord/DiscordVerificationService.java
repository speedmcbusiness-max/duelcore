package net.duelcore.plugin.discord;

import net.duelcore.plugin.DuelCorePlugin;
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;

import java.security.SecureRandom;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class DiscordVerificationService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 confusion
    private final SecureRandom random = new SecureRandom();

    private final DuelCorePlugin plugin;
    private final JDA jda;

    public DiscordVerificationService(DuelCorePlugin plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
    }

    /** Generates a fresh single-use code for a player and stores it, expiring per config. */
    public String generateCode(UUID uuid) {
        String code = randomCode();
        int expiryMinutes = plugin.getConfig().getInt("discord.verify-code-expiry-minutes", 10);
        long now = System.currentTimeMillis();
        long expiresAt = now + (expiryMinutes * 60_000L);

        try (Connection conn = plugin.database().getConnection()) {
            // one active code per player at a time
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM dc_verify_codes WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO dc_verify_codes (code, uuid, created_at, expires_at) VALUES (?, ?, ?, ?)")) {
                ins.setString(1, code);
                ins.setString(2, uuid.toString());
                ins.setLong(3, now);
                ins.setLong(4, expiresAt);
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to generate verify code: " + e.getMessage());
        }
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /** Attempts to consume a code entered on the Discord side and link the account. Runs off-thread. */
    public void tryLink(String code, String discordId, LinkResultCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            LinkResult result = attemptLink(code.trim().toUpperCase(), discordId);
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(result));
        });
    }

    private LinkResult attemptLink(String code, String discordId) {
        try (Connection conn = plugin.database().getConnection()) {
            UUID uuid = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, expires_at FROM dc_verify_codes WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return LinkResult.INVALID_CODE;
                    }
                    if (rs.getLong("expires_at") < System.currentTimeMillis()) {
                        return LinkResult.EXPIRED;
                    }
                    uuid = UUID.fromString(rs.getString("uuid"));
                }
            }

            // one Minecraft account <-> one Discord account
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT uuid FROM dc_discord_links WHERE discord_id = ?")) {
                check.setString(1, discordId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return LinkResult.DISCORD_ALREADY_LINKED;
                    }
                }
            }
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT discord_id FROM dc_discord_links WHERE uuid = ?")) {
                check.setString(1, uuid.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return LinkResult.ACCOUNT_ALREADY_LINKED;
                    }
                }
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO dc_discord_links (uuid, discord_id, linked_at) VALUES (?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setString(2, discordId);
                ins.setLong(3, System.currentTimeMillis());
                ins.executeUpdate();
            }
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM dc_verify_codes WHERE code = ?")) {
                del.setString(1, code);
                del.executeUpdate();
            }

            plugin.discord().applyVerifiedRole(discordId);
            return LinkResult.success(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to link Discord account: " + e.getMessage());
            return LinkResult.ERROR;
        }
    }

    /** Looks up the Discord id linked to a Minecraft UUID, or empty if not linked. */
    public Optional<String> getDiscordId(UUID uuid) {
        try (Connection conn = plugin.database().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT discord_id FROM dc_discord_links WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("discord_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to look up Discord link: " + e.getMessage());
        }
        return Optional.empty();
    }

    public interface LinkResultCallback {
        void onResult(LinkResult result);
    }

    public static final class LinkResult {
        public static final LinkResult INVALID_CODE = new LinkResult(Status.INVALID_CODE, null);
        public static final LinkResult EXPIRED = new LinkResult(Status.EXPIRED, null);
        public static final LinkResult DISCORD_ALREADY_LINKED = new LinkResult(Status.DISCORD_ALREADY_LINKED, null);
        public static final LinkResult ACCOUNT_ALREADY_LINKED = new LinkResult(Status.ACCOUNT_ALREADY_LINKED, null);
        public static final LinkResult ERROR = new LinkResult(Status.ERROR, null);

        public enum Status { SUCCESS, INVALID_CODE, EXPIRED, DISCORD_ALREADY_LINKED, ACCOUNT_ALREADY_LINKED, ERROR }

        public final Status status;
        public final UUID linkedUuid;

        private LinkResult(Status status, UUID linkedUuid) {
            this.status = status;
            this.linkedUuid = linkedUuid;
        }

        public static LinkResult success(UUID uuid) {
            return new LinkResult(Status.SUCCESS, uuid);
        }
    }
}
