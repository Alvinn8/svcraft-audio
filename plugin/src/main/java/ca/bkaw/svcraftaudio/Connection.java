package ca.bkaw.svcraftaudio;

import org.bukkit.command.CommandSender;
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
    private List<CompletableFuture<Connection>> futures = new ArrayList<>();
    private CommandSender peersInfoSender;

    public Connection(SVCraftAudio svcraftAudio, URI serverUri) {
        super(serverUri);
        this.svcraftAudio = svcraftAudio;

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
            String userId = message.substring("User connected with id: ".length());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }
}
