package ca.bkaw.svcraftaudio;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * A user that is connected to svcraft audio.
 */
public class User {
    /**
     * The plugin instance.
     */
    private final SVCraftAudio svcraftAudio;
    /**
     * The id of this user. This is also their peer id.
     */
    private final String userId;
    /**
     * The player entity.
     */
    private final Player player;
    /**
     * A set of other users this user can hear.
     */
    private final Set<User> hearingUsers = new HashSet<>();
    /**
     * A map of the last volumes that were sent for a specific other user.
     */
    private final Object2DoubleMap<User> sentVolumes = new Object2DoubleOpenHashMap<>();

    public User(SVCraftAudio svcraftAudio, String userId, Player player) {
        this.svcraftAudio = svcraftAudio;
        this.userId = userId;
        this.player = player;
    }

    /**
     * Get the id of this user. This is also their peer id.
     *
     * @return The user id.
     */
    public String getUserId() {
        return this.userId;
    }

    /**
     * Get the player entity.
     *
     * @return The player entity.
     */
    public Player getPlayer() {
        return this.player;
    }

    /**
     * Get the player name of this player.
     *
     * @return The name of the player.
     */
    public String getName() {
        return this.player.getName();
    }

    /**
     * Send a message to this user that is received and handled on the website/client.
     *
     * @param message The message to send.
     */
    private void send(String message) {
        this.svcraftAudio.getConnection().send("To " + this.userId +": " + message);
    }

    /**
     * Whether this user can hear the other user.
     *
     * @param other The user to check whether this user can hear.
     * @return Whether this user can hear the other user.
     */
    public boolean canHear(User other) {
        return this.hearingUsers.contains(other);
    }

    /**
     * Make this user start hearing the other user.
     * <p>
     * Will also make the other user start hearing this user.
     *
     * @param other The user this user should start hearing.
     * @param doConnection Whether this user should init the call.
     */
    public void startHearing(User other, boolean doConnection) {
        // Check if this user can already hear that user
        if (this.hearingUsers.contains(other)) return;

        if (this.svcraftAudio.getConfiguration().debug) {
            this.player.sendMessage("> You will now start hearing " + other.getName());
            this.svcraftAudio.getLogger().info("> " + this.getName() + " will now start hearing " + other.getName());
        }

        this.hearingUsers.add(other);
        this.sentVolumes.put(other, 0);

        if (doConnection) {
            this.send("Connect to " + other.userId);
        } else {
            this.send("Wait for " + other.userId);
        }

        if (!other.canHear(this)) {
            other.startHearing(this, false);
        }
    }

    /**
     * Make this user stop hearing the other user.
     * <p>
     * Will also make the other user stop hearing this user.
     *
     * @param other The user this user should stop hearing.
     */
    public void stopHearing(User other) {
        if (!this.hearingUsers.contains(other)) return;

        if (this.svcraftAudio.getConfiguration().debug) {
            this.player.sendMessage("< You will now stop hearing " + other.getName());
            this.svcraftAudio.getLogger().info("< " + this.getName() + " will now stop hearing " + other.getName());
        }

        this.hearingUsers.remove(other);
        this.sentVolumes.removeDouble(other);

        this.send("Disconnect " + other.userId);

        if (other.canHear(this)) {
            other.stopHearing(this);
        }
    }

    /**
     * Get the distance squared to the other user.
     *
     * @param other The other user.
     * @return The distance squared.
     */
    public double getDistanceSqTo(User other) {
        Location location = this.player.getLocation();
        Location otherLocation = other.player.getLocation();
        if (location.getWorld() == otherLocation.getWorld()) {
            return location.distanceSquared(otherLocation);
        }
        // Different worlds = infinite distance
        return Integer.MAX_VALUE;
    }

    public double getDistanceTo(User other) {
        return Math.sqrt(this.getDistanceSqTo(other));
    }

    public double getVolumeFor(User other) {
        return this.getVolume(this.getDistanceTo(other));
    }

    public double getVolume(double distance) {
        //
        //                hearDistance - distance
        // volume = ----------------------------------
        //           hearDistance - maxVolumeDistance
        //

        Config config = this.svcraftAudio.getConfiguration();
        double volume = (config.hearDistance - distance) / (config.hearDistance - config.maxVolumeDistance);
        if (volume > 1) volume = 1;
        if (volume < 0) volume = 0;
        return volume;
    }
}
