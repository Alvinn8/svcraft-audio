package ca.bkaw.svcraftaudio;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A task that runs to update who hears who, and at what volume.
 */
public class UpdateTask extends BukkitRunnable {
    /**
     * The plugin instance.
     */
    private final SVCraftAudio svcraftAudio;
    /**
     * The user manager.
     */
    private final UserManager userManager;
    /**
     * The last time the volumes were all resent.
     */
    private long lastVolumeResend = System.currentTimeMillis();
    /**
     * The last time a heartbeat request was sent.
     */
    private long lastHeartbeat = System.currentTimeMillis();

    public UpdateTask(SVCraftAudio svcraftAudio, UserManager userManager) {
        this.svcraftAudio = svcraftAudio;
        this.userManager = userManager;
    }

    @Override
    public void run() {
        if (this.userManager.getUserCount() == 0) {
            return;
        }

        Config config = this.svcraftAudio.getConfiguration();
        int connectDistanceSq = config.connectDistance * config.connectDistance;
        int disconnectDistanceSq = config.disconnectDistance * config.disconnectDistance;

        List<User> users = this.userManager.getUsers();
        for (User user : users) {
            for (User otherUser : users) {
                if (user == otherUser) {
                    continue;
                }

                double distanceSq = user.getDistanceSqTo(otherUser);
                if (user.canHear(otherUser)) {
                    if (distanceSq > disconnectDistanceSq) {
                        user.stopHearing(otherUser);
                    }
                } else {
                    if (distanceSq < connectDistanceSq) {
                        user.startHearing(otherUser, true);
                    }
                }

                if (user.canHear(otherUser)) {
                    double currentVolume = user.getVolume(Math.sqrt(distanceSq));
                    double lastVolume = user.getLastSentVolumeFor(otherUser);
                    double diff = Math.abs(currentVolume - lastVolume);
                    if (diff > 0.05 || (currentVolume == 1 && lastVolume != 1) || (currentVolume == 0 && lastVolume != 0)) {
                        double roundedVolume = Math.round(currentVolume * 100.0) / 100.0;
                        user.setVolumeFor(otherUser, roundedVolume);
                        if (config.debug) {
                            user.getPlayer().sendMessage("You will now hear " + otherUser.getName() + " at volume " + roundedVolume);
                        }
                    }
                }
            }
        }

        if (System.currentTimeMillis() - this.lastVolumeResend > 10000) {
            // Every 10 seconds
            if (config.debug) {
                this.svcraftAudio.getLogger().info("Resending volumes");
            }
            for (User user : users) {
                for (User hearingUser : user.getHearingUsers()) {
                    double volume = user.getVolumeFor(hearingUser);
                    user.setVolumeFor(hearingUser, volume);
                }
            }
            this.lastVolumeResend = System.currentTimeMillis();
        }

        if (System.currentTimeMillis() - this.lastHeartbeat > 300000) {
            // Every 5 minutes
            if (config.debug) {
                this.svcraftAudio.getLogger().info("Sending heartbeat");
            }
            // Send a heartbeat to the http server
            Bukkit.getScheduler().runTaskAsynchronously(this.svcraftAudio, () -> {
                String urlString = config.url.toString();
                if (!urlString.endsWith("/")) {
                    urlString += '/';
                }
                urlString += "heartbeat";
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    int responseCode = connection.getResponseCode();
                    if (responseCode != 204) {
                        this.svcraftAudio.getLogger().warning("Heartbeat failed, got status code: " + responseCode);
                    }
                } catch (IOException e) {
                    this.svcraftAudio.getLogger().severe("Failed to send heartbeat: " + e.getMessage());
                }
            });
            // Send a heartbeat to all clients
            for (User user : users) {
                user.sendHeartbeat();
            }
            // After 30 seconds, disconnect users that did not reply to the heartbeat
            Bukkit.getScheduler().runTaskLater(this.svcraftAudio, () -> {
                List<User> toRemove = new ArrayList<>();
                for (User user : this.userManager.getUsers()) {
                    if (user.isAwaitingHeartbeatResponse()) {
                        toRemove.add(user);
                    }
                }
                for (User user : toRemove) {
                    this.svcraftAudio.getLogger().info(
                        "Disconnecting " + user.getId() + " (" + user.getName()
                        + ") due to heartbeat timing out."
                    );
                    this.userManager.removeUser(user.getId());
                }
            }, 600);

            this.lastHeartbeat = System.currentTimeMillis();
        }
    }
}
