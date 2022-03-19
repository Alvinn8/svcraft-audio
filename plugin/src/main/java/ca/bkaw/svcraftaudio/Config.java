package ca.bkaw.svcraftaudio;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.URISyntaxException;

public class Config {
    /** The distance in blocks you need to be to another player to hear them. */
    public final int hearDistance;
    /**
     * The distance in blocks you need to be to another player to hear them
     * at full volume.
     * */
    public final int maxVolumeDistance;
    /**
     * The distance in blocks you need to be to another player for their audio to
     * start connecting. Connecting can sometimes take a little bit so therefore
     * players are connected a bit before the players can actually hear each other.
     * */
    public final int connectDistance;
    /**
     * The distance in blocks you need to be to another player for their audio to
     * disconnect. This should be set to slightly more than connectDistance to avoid
     * players disconnecting and reconnecting often when being on the edge of the
     * connection range.
     * */
    public final int disconnectDistance;
    /** The interval in ticks when the update task runs. */
    public final int updateTaskInterval;
    /** The URI of the svcraft-audio website to connect to. */
    public final URI url;
    /** Whether debug mode is enabled. */
    public final boolean debug;

    public Config(FileConfiguration config) {
        this.hearDistance = config.getInt("hearDistance", 32);
        this.maxVolumeDistance = config.getInt("maxVolumeDistance", 8);
        this.connectDistance = config.getInt("connectDistance", 40);
        this.disconnectDistance = config.getInt("disconnectDistance", 50);
        this.updateTaskInterval = config.getInt("updateTaskInterval", 20);
        this.debug = config.getBoolean("debug", true);
        try {
            this.url = new URI(config.getString("url", "https://svcraft-audio.alvinn8.repl.co"));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid svcraft-audio url", e);
        }
    }
}
