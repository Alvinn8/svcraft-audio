package ca.bkaw.svcraftaudio;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The connection to the svcraft-audio websocket, indirectly the connection to
 * the clients (players).
 */
public class Connection extends WebSocketClient {
    private final SVCraftAudio svcraftAudio;
    private final UserManager userManager;
    private List<CompletableFuture<Connection>> futures = new ArrayList<>();
    private CommandSender peersInfoSender;

    public Connection(SVCraftAudio svcraftAudio, UserManager userManager, URI serverUri) {
        super(serverUri);
        this.svcraftAudio = svcraftAudio;
        this.userManager = userManager;

        this.connect();
    }

    /**
     * Get the plugin instance.
     *
     * @return The plugin instance.
     */
    public SVCraftAudio getSVCraftAudio() {
        return this.svcraftAudio;
    }

    /**
     * Add a future that completes when the connection has been established and is
     * ready for commands.
     *
     * @param future The future to add.
     */
    public void addFuture(CompletableFuture<Connection> future) {
        this.futures.add(future);
    }

    /**
     * Set the sender where debug peer info messages should be sent.
     *
     * @param sender The sender that should receive peer info messages.
     */
    public void setPeerInfoSender(CommandSender sender) {
        this.peersInfoSender = sender;
    }

    /**
     * Add a new connect id that the websocket server uses to give clients their
     * user id, username and server id.
     *
     * @param connectId The id.
     * @param userId The user id.
     * @param username The username.
     */
    public void addConnectId(String connectId, String userId, String username) {
        this.send("New connect id: " + connectId
            + " with user id " + userId
            + " and with username: " + username);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.send("I am a server with id " + SVCraftAudio.SERVER_ID);

        for (CompletableFuture<Connection> future : this.futures) {
            future.complete(this);
        }
        this.futures = new ArrayList<>();
    }

    @Override
    public void onMessage(String message) {
        if (message.startsWith("User connected with id: ")) {
            String[] parts = message.substring("User connected with id: ".length()).split(" and username: ");
            String userId = parts[0];
            String username = parts[1];

            Player player = Bukkit.getPlayer(username);
            if (player != null) {
                User user = new User(this.svcraftAudio, userId, player);
                this.userManager.addNewUser(user);
                player.sendMessage("You connected to svcraft-audio.");
            } else {
                this.send("To " + userId +": You are not on the server");
            }
        }
        else if (message.startsWith("User disconnected ")) {
            String userId = message.substring("User disconnected ".length());
            this.userManager.removeUser(userId);
        }
        else if (message.startsWith("Warning from user ")) {
            String[] parts = message.substring("Warning from user ".length()).split(": ");
            String userId = parts[0];
            String warn = parts[1];

            Component component = Component.text()
                .color(NamedTextColor.YELLOW)
                .append(Component.text("[svcraft-audio warn] "))
                .append(this.svcraftAudio.getUserComponent(userId))
                .append(Component.text(": " + warn))
                .build();

            List<CommandSender> senders = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("svcraftaudio.warn")) {
                    senders.add(player);
                }
            }
            senders.add(Bukkit.getConsoleSender());

            Audience.audience(senders).sendMessage(component);
        }
        else if (message.startsWith("Peer info from ") && this.peersInfoSender != null) {
            String content = message.substring("Peer info from ".length());
            String[] parts = content.split(": ");
            String userId = parts[0];
            String part2 = parts[1];

            Component peersInfo = Component.text(' ' + part2.substring(5),
                part2.startsWith("good") ? NamedTextColor.GREEN : NamedTextColor.RED);

            this.peersInfoSender.sendMessage(Component.text()
                .append(this.svcraftAudio.getUserComponent(userId))
                .append(peersInfo)
            );
        }
        else if (message.startsWith("Heartbeat response from ")) {
            String userId = message.substring("Heartbeat response from ".length());
            User user = this.userManager.getUser(userId);
            if (user != null) {
                user.gotHeartbeatResponse();
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.svcraftAudio.getLogger().info(
            "Connection closed with code " + code
                + " reason: \"" + reason + "\" "
                + " and remote = " + remote
        );
        // Disconnect all users
        for (User user : new ArrayList<>(this.userManager.getUsers())) {
            this.userManager.removeUser(user.getId());
        }
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}
