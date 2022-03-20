package ca.bkaw.svcraftaudio;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {
    private final UserManager userManager;

    public EventListener(UserManager userManager) {
        this.userManager = userManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.userManager.onPlayerQuit(event.getPlayer());
    }
}
