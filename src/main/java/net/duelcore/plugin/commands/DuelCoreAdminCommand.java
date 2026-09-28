package net.duelcore.plugin.commands;

import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DuelCoreAdminCommand implements CommandExecutor {

    private final DuelCorePlugin plugin;

    public DuelCoreAdminCommand(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /duelcoreadmin <reload>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.config().load();
            plugin.hotbar().build();
            sender.sendMessage(plugin.config().color("<#55ff55>DuelCore config reloaded."));
            return true;
        }

        sender.sendMessage("Unknown subcommand.");
        return true;
    }
}
