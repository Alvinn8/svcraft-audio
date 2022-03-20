package ca.bkaw.svcraftaudio;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The plugin's configuration.
 * <p>
 * The config instance should not be stored, but accessed via
 * {@link SVCraftAudio#getConfiguration()} so that a new config instance is fetched
 * after config reloads.
 */
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
     * start connecting. This should be slightly more than hearDistance because
     * connecting isn't instant, therefore players are connected a bit before the
     * players can actually hear each other.
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
        this.hearDistance = getInt(config, "hearDistance", 32);
        this.maxVolumeDistance = getInt(config, "maxVolumeDistance", 8);
        this.connectDistance = getInt(config, "connectDistance", 40);
        this.disconnectDistance = getInt(config, "disconnectDistance", 50);
        this.updateTaskInterval = getInt(config, "updateTaskInterval", 20);
        this.debug = getBoolean(config, "debug", true);
        try {
            this.url = new URI(getString(config, "url", "https://svcraft-audio.alvinn8.repl.co"));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid svcraft-audio url", e);
        }
    }

    private int getInt(FileConfiguration config, String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path);
    }

    private boolean getBoolean(FileConfiguration config, String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path);
    }

    private String getString(FileConfiguration config, String path, String def) {
        config.addDefault(path, def);
        return config.getString(path);
    }
}
