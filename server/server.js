const express = require("express");
const http = require("http");
const ws = require("ws");
const path = require("path");
const { ExpressPeerServer } = require("peer");

const PORT = process.env.WEB_PORT || 8000;

const app = express();
const httpServer = http.createServer(app);

// === Peer Server ===

const peerServer = ExpressPeerServer(httpServer, {
    proxied: true,
    debug: true,
    path: '/peer',
    ssl: {}
});

app.use(peerServer);

// Remove the upgrade listener, because we are running two websockets on the same
// http server we need to handle upgrades manually and forward it to the correct
// websocket. See below.
let peerWebSocketListener;
for (const func of httpServer.listeners("upgrade")) {
    httpServer.removeListener("upgrade", func);
    peerWebSocketListener = func;
}

// === Static HTTP Server ===

app.use(express.static(path.resolve(__dirname, "../web")));

app.get("/", (request, response) => {
    response.sendFile(path.resolve(__dirname, "../web/index.html"));
});

app.post("/heartbeat", (request, response) => {
    console.log("Got heartbeat");
    response.statusCode = 204;
    response.end();
});

// === Web Socket ===

/**
 * A list of servers that have been registered on this svcraft-audio websocket.
 * @type {ServerData[]}
 */
const servers = [];

/**
 * A list of connect ids that clients can use to connect. Holds information about
 * what server to connect to and the username of the player.
 * @type {ConnectIdData[]}
 */
const connectIds = [];

/**
 * A map of a websocket to a connection handler. Is used to send incoming messages
 * to the right handler.
 * @type {Map<ws, ConnectionHandler>}
 */
const connections = new Map();

const websocketServer = new ws.Server({ noServer: true });

httpServer.on("upgrade", function(request, socket, head) {
    if (request.url && request.url.startsWith("/peer")) {
        // A connection to the peer, let the peer server handle the request
        peerWebSocketListener(request, socket, head);
    } else {
        // A connection to the svcraft-audio websocket, let the websocketServer
        // handle the request.
        websocketServer.handleUpgrade(request, socket, head, function(socket2) {
            websocketServer.emit("connection", socket2, request);
        });
    }
});

/**
 * Get a server by id.
 * 
 * @param {number} id The id of the server to get.
 * @returns {ServerData | null} The found server, or null.
 */
function getServerById(id) {
    for (const server of servers) {
        if (server.id == id) {
            return server;
        }
    }
    return null;
}

/**
 * Get a server by websocket.
 * 
 * @param {ws} websocket The websocket to get the server for.
 * @returns {ServerData | null} The found server, or null.
 */
 function getServerByWebsocket(websocket) {
    for (const server of servers) {
        if (server.websocket == websocket) {
            return server;
        }
    }
    return null;
}

/**
 * Get a ConnectIdData from a connect id.
 * 
 * @param {string} id The connect id.
 * @returns {ConnectIdData | null} The ConnectIdData instance, or null if none was found.
 */
function getConnectId(id) {
    for (const connectId of connectIds) {
        if (connectId.connectId == id) {
            return connectId;
        }
    }
    return null;
}

/**
 * Get a ConnectedUser instance from a websocket.
 * 
 * @param {ws} websocket The websocket to get the user for.
 * @returns {ConnectedUser | null} The ConnectedUser instance, or null if none is found.
 */
function getConnectedUserByWebsocket(websocket) {
    for (const server of servers) {
        for (const connectedUser of server.connectedUsers) {
            if (connectedUser.websocket == websocket) {
                return connectedUser;
            }
        }
    }
    return null;
}

/**
 * Something capable of handling incoming messages connections.
 */
class ConnectionHandler {
    /**
     * Handle an incoming message.
     * 
     * @param {string} message The received message.
     * @abstract
     */
    onMessage(message) {
        throw new Error("onMessage is an abstract method that needs to be implemented.");
    }
}

/**
 * Data from a plugin / a server, that clients can connect to.
 */
class ServerData extends ConnectionHandler {
    /**
     * Create a new ServerData.
     * 
     * @param {string} id The id of the server.
     * @param {ws} websocket The connection to the plugin.
     */
    constructor(id, websocket) {
        super();
        /**
         * The id of the server.
         * @type {string}
         */
        this.id = id;
        /**
         * The websocket connection to the server.
         * @type {ws}
         */
        this.websocket = websocket;
        /**
         * A list of users that are connected to this server.
         * @type {ConnectedUser[]}
         */
        this.connectedUsers = [];
    }

    /**
     * Get a connected user by id.
     * 
     * @param {string} userId The id of the user to get.
     * @returns {ConnectedUser | null} The connected user, or null if not found.
     */
    getConnectedUserById(userId) {
        for (const connectedUser of this.connectedUsers) {
            if (connectedUser.id == userId) {
                return connectedUser;
            }
        }
        return null;
    }

    /**
     * Send a message to all connected users.
     * 
     * @param {string} message The message to send to all connected users.
     */
    sendToAll(message) {
        for (const connectedUser of this.connectedUsers) {
            if (connectedUser.websocket.readyState == 1) {
                connectedUser.websocket.send(message);
            }
        }
    }

    /**
     * Called when the server sends a message to the svcraft-audio websocket server.
     * 
     * @param {string} message The message.
     */
    onMessage(message) {
        // Forward messages to users
        if (message.startsWith("To ")) {
            const match = /^To (?<userId>[A-z0-9-]+): (?<userMessage>.*)/.exec(message);
            const { userId, userMessage } = match.groups;

            const user = this.getConnectedUserById(userId);
            if (user != null) {
                user.websocket.send(userMessage);
            } else {
                console.warn("Tried to send message to user that wasn't connected: " + userId);
            }
        }

        // Creation of new connect ids
        if (message.startsWith("New connect id: ")) {
            const match = /New connect id: (?<connectId>[A-z0-9]+) with user id (?<userId>[A-z0-9-]+) and with username: (?<username>.+)/.exec(message);
            const { connectId, userId, username } = match.groups;

            const data = new ConnectIdData(connectId, userId, username, this);
            connectIds.push(data);
        }

        // Resync
        if (message == "resync") {
            this.sendToAll("resync");
        }

        // peers info
        if (message == "peersinfo") {
            this.sendToAll("peersinfo");
        }
    }
}

/**
 * An id that clients use to connect. Holds information about what server to
 * connect to and the username of the player.
 */
class ConnectIdData {
    /**
     * Create a new ConnectIdData.
     * 
     * @param {string} connectId The connect id.
     * @param {string} userId The user id.
     * @param {string} username The username of the player.
     * @param {ServerData} server The server to connect the user to.
     */
    constructor(connectId, userId, username, server) {
        /**
         * The connect id.
         * @type {string}
         */
        this.connectId = connectId;
        /**
         * The user id.
         * @type {string}
         */
        this.userId = userId;
        /**
         * The username of the player.
         * @type {string}
         */
        this.username = username;
        /**
         * The server to connect the user to.
         * @type {ServerData}
         */
        this.server = server;
    }

    /**
     * Check whether this connect id is still valid.
     * @returns {boolean} Whether this connect id is valid.
     */
    isValid() {
        return this.server.websocket.readyState == this.server.websocket.OPEN;
    }
}

/**
 * A user that is connected.
 */
class ConnectedUser extends ConnectionHandler {
    /**
     * Create a new ConnectedUser.
     * 
     * @param {ws} websocket The web socket to the user.
     * @param {string} id The id of the user.
     * @param {ServerData} server The server this user is connected to.
     */
    constructor(websocket, id, server) {
        super();
        /**
         * The web socket to the user.
         * @type {ws}
         */
        this.websocket = websocket;
        /**
         * The id of the user. This will be their peer id that is used to connect
         * to other users via peer-to-peer WebRTC. This is also what the server
         * uses to refer to users to specify which user to send messages to.
         * @type {string}
         */
        this.id = id;
        /**
         * The server this user is connected to.
         * @type {ServerData}
         */
        this.server = server;
    }

    /**
     * Called when the user sends a message to the svcraft-audio websocket server.
     * 
     * @param {string} message The message.
     */
    onMessage(message) {
        if (message.startsWith("Warning ")) {
            if (this.server != null) {
                const match = /Warning (?<warning>.+)/.exec(message);
                const { warning } = match.groups;
                this.server.websocket.send("Warning from user " + this.id + ": " + warning);
            }
        }

        if (message.startsWith("connected-peers ")) {
            if (this.server.websocket.readyState == this.server.websocket.OPEN) {
                this.server.websocket.send("Peer info from " + this.id + ": " + message.substring("connected-peers ".length));
            }
        }

        if (message == "Heartbeat response") {
            if (this.server.websocket.readyState == this.server.websocket.OPEN) {
                this.server.websocket.send("Heartbeat response from " + this.id);
            }
        }
    }
}


// Main handler

websocketServer.on("connection", function(websocket) {
    console.log("New connection");
    websocket.on("message", function (rawMessage) {
        try {
            const message = Buffer.isBuffer(rawMessage) ? rawMessage.toString("utf-8") : rawMessage;
            console.log("> " + message);
            const handler = connections.get(websocket);
            if (handler != null) {
                handler.onMessage(message);
            } else {
                // Handshaking, probably

                // A (new) server
                if (message.startsWith("I am a server")) {
                    const match = /I am a server with id (?<serverId>[A-z0-9-]+)/.exec(message);
                    const { serverId } = match.groups;
                    
                    let server = getServerById(serverId);
                    if (server == null) {
                        // A new server
                        server = new ServerData(serverId, websocket);
                        servers.push(server);
                        connections.set(websocket, server);
                    } else {
                        // Replace the server connection
                        if (server.websocket.readyState == server.websocket.OPEN) {
                            server.websocket.close();
                        }
                        server.websocket = websocket;
                    }
                }

                // A new client (user)
                if (message.startsWith("I am a user")) {
                    const match = /I am a user, connect id: (?<connectId>.+)/.exec(message);
                    const { connectId } = match.groups;

                    const connectIdData = getConnectId(connectId);

                    if (connectIdData != null && connectIdData.isValid()) {
                        const server = connectIdData.server;
                        const user = new ConnectedUser(websocket, connectIdData.userId, server);
                        server.connectedUsers.push(user);
                        connections.set(websocket, user);

                        // Notify server
                        server.websocket.send("User connected with id: " + connectIdData.userId + " and username: " + connectIdData.username);

                        // Notify user
                        websocket.send("Welcome, your user id: " + connectIdData.userId + " and your username is: " + connectIdData.username);
                    } else {
                        websocket.send("Invalid link");
                        websocket.close();
                    }
                }
            }
        } catch (e) {
            console.error("Error while handling websocket message");
            console.error(e);
        }
    });

    websocket.on("close", function(code, reason) {
        connections.delete(websocket);
        
        const user = getConnectedUserByWebsocket(websocket);
        if (user != null) {
            user.server.connectedUsers.splice(user.server.connectedUsers.indexOf(user), 1);
            if (user.server.websocket != null && user.server.websocket.readyState == 1) {
                // When a user gets kicked for being connected elsewhere another user with the
                // same user id is about to connect and the plugin has already removed the user
                // from the list of users, if the user(s) that got kicked disconnect would send
                // "User disconnected" messages, chances are the new user would get disconnected
                // and lost track of on the plugin. We therefore don't send user disconnected when
                // the close reason is connected-elsewhere, the plugin has already removed the
                // user from the user list, so it's fine.
                if (reason != "connected-elsewhere") {
                    user.server.websocket.send("User disconnected " + user.id);
                }
            }
        }

        const server = getServerByWebsocket(websocket);
        if (server != null) {
            console.log("Plugin disconnected from " + server.id);
            server.websocket = null;
            server.sendToAll("Has plugin connection? false");
        }
    });
});

// Start

httpServer.listen(PORT);
console.log("Server listening on port " + PORT);