package ca.bkaw.svcraftaudio;

import org.bukkit.scheduler.BukkitRunnable;

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
    }
}
