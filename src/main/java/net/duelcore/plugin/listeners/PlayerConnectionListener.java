package net.duelcore.plugin.listeners;

import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerConnectionListener implements Listener {

    private final DuelCorePlugin plugin;

    public PlayerConnectionListener(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.players().loadAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName(), () -> {
            plugin.hotbar().giveHotbar(event.getPlayer());
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.hotbar().giveHotbar(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.players().unload(event.getPlayer().getUniqueId());
    }
}
