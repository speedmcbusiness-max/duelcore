package net.duelcore.plugin.commands;

import net.duelcore.plugin.DuelCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VerifyCommand implements CommandExecutor {

    private final DuelCorePlugin plugin;

    public VerifyCommand(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (plugin.discord() == null || plugin.discord().verification() == null) {
            player.sendMessage(plugin.config().color("<red>Discord linking is not enabled on this server."));
            return true;
        }

        if (plugin.discord().verification().getDiscordId(player.getUniqueId()).isPresent()) {
            player.sendMessage(plugin.config().color("<red>Your account is already linked to a Discord account."));
            return true;
        }

        String code = plugin.discord().verification().generateCode(player.getUniqueId());
        int expiry = plugin.getConfig().getInt("discord.verify-code-expiry-minutes", 10);

        Component message = plugin.config().color(
                "<gray>Run <#55fcf1>/verify " + code + "<gray> in our Discord server within <white>"
                        + expiry + " minutes<gray> to link your account.")
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")))
                .clickEvent(ClickEvent.copyToClipboard("/verify " + code));

        player.sendMessage(message);
        return true;
    }
}
