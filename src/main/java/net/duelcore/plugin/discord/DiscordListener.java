package net.duelcore.plugin.discord;

import net.duelcore.plugin.DuelCorePlugin;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordListener extends ListenerAdapter {

    private final DuelCorePlugin plugin;
    private final DiscordVerificationService verification;

    public DiscordListener(DuelCorePlugin plugin, DiscordVerificationService verification) {
        this.plugin = plugin;
        this.verification = verification;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event);
            case "profile" -> handleProfile(event);
            default -> { }
        }
    }

    private void handleVerify(SlashCommandInteractionEvent event) {
        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();

        event.deferReply(true).queue();
        verification.tryLink(code, discordId, result -> {
            String message = switch (result.status) {
                case SUCCESS -> "✅ Linked! Your Minecraft account is now connected to Discord.";
                case INVALID_CODE -> "❌ That code isn't valid. Run `/verify` in-game to get a new one.";
                case EXPIRED -> "❌ That code expired. Run `/verify` in-game to get a new one.";
                case DISCORD_ALREADY_LINKED -> "❌ This Discord account is already linked to a Minecraft account.";
                case ACCOUNT_ALREADY_LINKED -> "❌ That Minecraft account is already linked to a Discord account.";
                case ERROR -> "❌ Something went wrong. Try again in a moment.";
            };
            event.getHook().sendMessage(message).queue();
        });
    }

    private void handleProfile(SlashCommandInteractionEvent event) {
        // Full profile embed (tiers, currency, points per kit) is built alongside
        // the leaderboard system in the next batch — this wires the command up
        // so it's ready to fill in without touching anything else.
        event.reply("Profiles are coming online with the leaderboard update — hang tight!")
                .setEphemeral(true)
                .queue();
    }
}
