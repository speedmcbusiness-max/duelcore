package net.duelcore.plugin.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.duelcore.plugin.DuelCorePlugin;

import javax.security.auth.login.LoginException;
import java.util.concurrent.TimeUnit;

public class DiscordManager {

    private final DuelCorePlugin plugin;
    private JDA jda;
    private DiscordVerificationService verificationService;

    public DiscordManager(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String token = plugin.getConfig().getString("discord.bot-token", "");
        if (token == null || token.isBlank() || token.equals("PUT_YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("Discord is enabled in config.yml but no bot token was set. Skipping Discord startup.");
            return;
        }

        try {
            this.jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .build();
            jda.awaitReady();
        } catch (LoginException | InterruptedException e) {
            plugin.getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            return;
        }

        this.verificationService = new DiscordVerificationService(plugin, jda);
        jda.addEventListener(new DiscordListener(plugin, verificationService));

        registerSlashCommands();
        plugin.getLogger().info("Discord bot connected as " + jda.getSelfUser().getAsTag());
    }

    private void registerSlashCommands() {
        String guildId = plugin.getConfig().getString("discord.guild-id", "0");
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Discord guild-id in config.yml is invalid; slash commands not registered.");
            return;
        }
        guild.updateCommands().addCommands(
                Commands.slash("verify", "Link your Minecraft account using a /verify code generated in-game")
                        .addOption(OptionType.STRING, "code", "The code you got from running /verify in-game", true),
                Commands.slash("profile", "View a player's DuelCore profile")
                        .addOption(OptionType.STRING, "player", "Minecraft username (defaults to your linked account)", false)
        ).queue();
    }

    /** Posts a tier-up announcement, pinging the linked Discord account if one exists. */
    public void announceTierUp(String username, String kitDisplay, String tierDisplay, String discordId) {
        if (jda == null) return;
        String channelId = plugin.getConfig().getString("discord.tier-announce-channel-id", "0");
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        String template = discordId != null
                ? plugin.getConfig().getString("discord.tier-up-message")
                : plugin.getConfig().getString("discord.tier-up-message-no-link");

        String message = template
                .replace("%player%", username)
                .replace("%kit%", kitDisplay)
                .replace("%tier%", tierDisplay)
                .replace("%discord_id%", discordId != null ? discordId : "");

        channel.sendMessage(message).queue();
    }

    /** Applies the "verified" role to a Discord member after a successful /verify. */
    public void applyVerifiedRole(String discordId) {
        String guildId = plugin.getConfig().getString("discord.guild-id", "0");
        String roleId = plugin.getConfig().getString("discord.verified-role-id", "0");
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(roleId);
        if (role == null) return;
        guild.retrieveMemberById(discordId).queue(member ->
                guild.addRoleToMember(member, role).queue());
    }

    public DiscordVerificationService verification() {
        return verificationService;
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            try {
                jda.awaitShutdown(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
