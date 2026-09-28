package net.duelcore.plugin.listeners;

import net.duelcore.plugin.DuelCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-clicking any hotbar item opens the same menu as the matching
 * command (e.g. the "duel" item opens what /duel opens). Menu GUIs
 * themselves are implemented in QueueMenu / DuelMenu / etc (next batch);
 * this listener just needs to dispatch to the right command name so
 * nothing else has to change once those are added.
 */
public class HotbarListener implements Listener {

    private final DuelCorePlugin plugin;

    public HotbarListener(DuelCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = plugin.hotbar().getIdFor(event.getItem());
        if (id == null) return;

        event.setCancelled(true);
        switch (id) {
            case "queue" -> event.getPlayer().performCommand("queue");
            case "duel" -> event.getPlayer().performCommand("duel");
            case "leaderboard" -> event.getPlayer().performCommand("leaderboard");
            case "party" -> event.getPlayer().performCommand("party");
            case "settings" -> event.getPlayer().performCommand("duelsettings");
            case "kiteditor" -> event.getPlayer().performCommand("kiteditor");
            default -> { /* unknown hotbar id, ignore */ }
        }
    }
}
