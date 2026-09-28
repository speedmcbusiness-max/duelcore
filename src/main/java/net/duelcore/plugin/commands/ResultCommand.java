package net.duelcore.plugin.commands;

import net.duelcore.plugin.DuelCorePlugin;
import net.duelcore.plugin.tiers.TierService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** /result <player> <gamemode> <tier> — staff sets a player's tier for a kit. */
public class ResultCommand implements CommandExecutor, TabCompleter {

    private final DuelCorePlugin plugin;

    public ResultCommand(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duelcore.admin")) {
            sender.sendMessage(plugin.config().color("<red>You don't have permission to do that."));
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage(plugin.config().color("<red>Usage: /result <player> <gamemode> <tier>"));
            return true;
        }

        String targetName = args[0];
        String kitId = args[1].toLowerCase();
        String tierId = args[2].toUpperCase();

        plugin.tiers().setTier(targetName, kitId, tierId, result -> {
            switch (result) {
                case SUCCESS -> {
                    String kitDisplay = plugin.tiers().getKitDisplay(kitId);
                    String tierDisplay = plugin.tiers().getTierDisplay(tierId);
                    sender.sendMessage(plugin.config().color(
                            "<#55ff55>Set <white>" + targetName + "<#55ff55>'s "
                                    + kitDisplay + "<#55ff55> tier to " + tierDisplay + "<#55ff55>."));
                }
                case UNKNOWN_PLAYER -> sender.sendMessage(plugin.config().color(
                        "<red>No player named '" + targetName + "' has joined this server before."));
                case INVALID_KIT -> sender.sendMessage(plugin.config().color(
                        "<red>'" + kitId + "' isn't a configured kit. Check the 'kits' section in config.yml."));
                case INVALID_TIER -> sender.sendMessage(plugin.config().color(
                        "<red>'" + tierId + "' isn't a configured tier. Check 'tiers.order' in config.yml."));
                case ERROR -> sender.sendMessage(plugin.config().color(
                        "<red>Something went wrong — check the console."));
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) options.add(p.getName());
            }
        } else if (args.length == 2) {
            Set<String> kits = plugin.getConfig().getConfigurationSection("kits") != null
                    ? plugin.getConfig().getConfigurationSection("kits").getKeys(false)
                    : Set.of();
            for (String kit : kits) {
                if (kit.startsWith(args[1].toLowerCase())) options.add(kit);
            }
        } else if (args.length == 3) {
            for (String tier : plugin.getConfig().getStringList("tiers.order")) {
                if (tier.toLowerCase().startsWith(args[2].toLowerCase())) options.add(tier);
            }
        }
        return options;
    }
}
