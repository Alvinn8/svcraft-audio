package ca.bkaw.svcraftaudio;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SVCraftAudio extends JavaPlugin {
    public static final String SERVER_ID = IdUtil.randomServerId();
    private Config config;
    private UpdateTask updateTask;
    private Connection connection;

    @Override
    public void onEnable() {
        this.loadConfig();

        this.getServer().getPluginManager().registerEvents(new EventListener(), this);

        this.updateTask = new UpdateTask();
        this.updateTask.runTaskTimer(this, this.config.updateTaskInterval, this.config.updateTaskInterval);

        this.connection = new Connection(this, this.config.url);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void loadConfig() {
        this.config = new Config(this.getConfig());

        List<String> header = new ArrayList<>();
        InputStream inputStream = this.getResource("config-header.txt");
        if (inputStream == null) {
            throw new RuntimeException("No config-header.txt file present in the plugin jar.");
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = in.readLine()) != null) {
                header.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.getConfig().options().setHeader(header);

        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

    /**
     * Get the configuration for the plugin.
     *
     * @return The config.
     */
    public Config getConfiguration() {
        return config;
    }

    /**
     * Get the connection, and if it is not connected, reconnect and wait for the
     * connection to establish before completing the future.
     *
     * @return The future.
     */
    public CompletableFuture<Connection> getConnectionAsync() {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        if (this.connection.isClosed()) {
            this.connection.addFuture(future);
            this.connection.reconnect();
        } else {
            future.complete(this.connection);
        }
        return future;
    }

    /**
     * Get the connection, and if it is not connected, reconnect synchronously and
     * wait for the connection to establish before returning.
     * <p>
     * This will block the thread and risks blocking the main thread (freezing the
     * server), so the {@link #getConnectionAsync()} method should be used instead
     * when possible.
     *
     * @return The connection.
     */
    @Deprecated
    public Connection getConnection() {
        if (this.connection.isClosed()) {
            this.getLogger().info("Reconnecting...");
            try {
                this.connection.reconnectBlocking();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return this.connection;
    }

    /**
     * Get the raw connection, the connection might have disconnected.
     *
     * @return The connection.
     */
    @Deprecated
    public Connection getConnectionRaw() {
        return this.connection;
    }

}
