package ca.bkaw.svcraftaudio;

import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * An event called when a player disconnects from the audio.
 */
public class UserDisconnectEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    private final User user;

    public UserDisconnectEvent(User user) {
        super(user.getPlayer());
        this.user = user;
    }

    /**
     * Get the user that was disconnected.
     *
     * @return The user.
     */
    public User getUser() {
        return this.user;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
