package ca.bkaw.svcraftaudio;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SVCraftAudio extends JavaPlugin {
    public static final String SERVER_ID = IdUtil.randomServerId();
    private Config config;
    private UserManager userManager;
    private Connection connection;
    private UpdateTask updateTask;
    private AudioCommand command;

    @Override
    public void onEnable() {
        this.loadConfig();

        this.userManager = new UserManager(this);

        this.getServer().getPluginManager().registerEvents(new EventListener(this.userManager), this);
        this.registerCommand();

        this.connection = new Connection(this, this.userManager, this.getWebsocketUrl());

        this.updateTask = new UpdateTask(this, this.userManager);
        this.updateTask.runTaskTimer(this, this.config.updateTaskInterval, this.config.updateTaskInterval);
    }

    @Override
    public void onDisable() {
        this.connection.close();
    }

    /**
     * Register the /audio command.
     */
    private void registerCommand() {
        this.command = new AudioCommand(this, this.userManager);

        // Why can't there just be brigadier support? :(
        try {
            Method getServer = Bukkit.getServer().getClass().getDeclaredMethod("getServer");
            Object server = getServer.invoke(Bukkit.getServer());
            Field[] fields = server.getClass().getSuperclass().getDeclaredFields();
            for (Field field : fields) {
                if (field.getType().getName().contains("CommandDispatcher")) {
                    field.setAccessible(true);
                    Object commands = field.get(server);
                    Field[] fields2 = commands.getClass().getDeclaredFields();
                    for (Field field2 : fields2) {
                        if (field2.getType().getName().contains("CommandDispatcher")) {
                            field2.setAccessible(true);
                            Object commandDispatcher = field2.get(commands);
                            CommandDispatcher<BukkitBrigadierCommandSource> dispatcher = (CommandDispatcher<BukkitBrigadierCommandSource>) commandDispatcher;
                            this.command.register(dispatcher);
                            return;
                        }
                    }
                }
            }
            throw new ReflectiveOperationException("Unable to find CommandDispatcher.");
        } catch (ReflectiveOperationException | ClassCastException e) {
            this.getLogger().severe("Failed to register command.");
            e.printStackTrace();
        }
    }

    /**
     * Get the url to the svcraft-audio websocket. This will be the url the user has
     * configured, but with http(s) changed to ws(s).
     *
     * @return The url.
     */
    private URI getWebsocketUrl() {
        String href = this.config.url.toString().replaceFirst("^http", "ws");
        return URI.create(href);
    }

    /**
     * Load the configuration.
     */
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
        this.getConfig().options()
            .header(String.join("\n", header))
            .copyDefaults(true);

        this.saveConfig();
    }

    /**
     * Reload the configuration and ensure all services are using the new configuration.
     */
    public void reloadConfiguration() {
        this.getLogger().info("Reloading the configuration.");

        this.reloadConfig();
        this.loadConfig();

        // Restart timer with new interval
        this.getLogger().info("Restarting update task with interval " + this.config.updateTaskInterval);
        if (this.updateTask != null && !this.updateTask.isCancelled()) {
            this.updateTask.cancel();
        }
        this.updateTask = new UpdateTask(this, this.userManager);
        this.updateTask.runTaskTimer(this, this.config.updateTaskInterval, this.config.updateTaskInterval);
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
     * Get the user manager.
     *
     * @return The user manager.
     */
    public UserManager getUserManager() {
        return this.userManager;
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

    /**
     * Get the /audio command.
     *
     * @return The command.
     */
    public AudioCommand getCommand() {
        return this.command;
    }

    /**
     * Get a component for a user id that is formatted in a human-readable way by
     * using the player username, and provide a hover for the user id.
     *
     * @param userId The user id.
     * @return The text component.
     */
    public Component getUserComponent(String userId) {
        User user = this.userManager.getUser(userId);
        if (user != null) {
            return this.getUserComponent(user);
        } else {
            return Component.text("<user not found: "+ userId +">", NamedTextColor.RED);
        }
    }

    /**
     * Get a component for a user.
     *
     * @param user The user.
     * @return The component.
     * @see #getUserComponent(String)
     */
    public Component getUserComponent(User user) {
        return Component.text(user.getPlayer().getName())
            .hoverEvent(Component.text("user id: " + user.getId()));
    }
}
