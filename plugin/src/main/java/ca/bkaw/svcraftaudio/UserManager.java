package ca.bkaw.svcraftaudio;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds and manages a list of connected users.
 */
public class UserManager {
    private final SVCraftAudio svcraftAudio;
    private final List<User> users = new ArrayList<>();

    public UserManager(SVCraftAudio svcraftAudio) {
        this.svcraftAudio = svcraftAudio;
    }

    /**
     * Get a user by id.
     *
     * @param userId The id of the user to get.
     * @return The user, or null.
     */
    public User getUser(String userId) {
        for (User user : users) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Get the amount of connected users.
     *
     * @return The amount of connected users.
     */
    public int getUserCount() {
        return this.users.size();
    }

    public List<User> getUsers() {
        return this.users;
    }

    /**
     * Check whether the specified player is connected via svcraft-audio.
     *
     * @param player The player.
     * @return Whether the player is connected.
     */
    public boolean isConnected(Player player) {
        for (User user : this.users) {
            if (user.getPlayer() == player) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a newly created user to the list of users.
     * <p>
     * Will disconnect any user that has the same user id or player uuid with the
     * connected-elsewhere message.
     *
     * @param user The user to add.
     */
    public void addNewUser(User user) {
        this.users.removeIf(existingUser -> {
            if (user.getId().equals(existingUser.getId())
                || user.getPlayer().getUniqueId().equals(existingUser.getPlayer().getUniqueId())) {
                existingUser.sendConnectedElseWhere();
                return true;
            }
            return false;
        });

        this.users.add(user);
    }

    /**
     * Remove a user with the specified id.
     * <p>
     * Will clear the user from hearing sets of other users.
     *
     * @param userId The id of the user to remove.
     */
    public void removeUser(String userId) {
        // This hopefully should never be more than one, but just to be safe.
        this.users.removeIf(user -> {
            if (user.getId().equals(userId)) {
                Player player = user.getPlayer();
                if (player.isOnline()) {
                    player.sendMessage("You disconnected from svcraft-audio.");
                }
                // Call the event on the main thread
                Bukkit.getScheduler().runTask(this.svcraftAudio,
                    () -> new UserDisconnectEvent(user).callEvent());

                return true;
            }
            return false;
        });

        // Make other users stop hearing this user
        for (User user : this.users) {
            for (User hearingUser : user.getHearingUsers()) {
                if (hearingUser.getId().equals(userId)) {
                    user.stopHearing(hearingUser);
                }
            }
        }
    }

    /**
     * Handle a player leaving the game. In case this player is a connected user,
     * disconnect that user.
     *
     * @param player The player that is leaving.
     */
    public void onPlayerQuit(Player player) {
        this.users.removeIf(user -> {
            if (user.getPlayer() == player) {
                user.sendQuitGame();
                return true;
            }
            return false;
        });
    }
}
