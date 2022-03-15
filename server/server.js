const express = require("express");
const http = require("http");
const ws = require("ws");
const path = require("path");
const { ExpressPeerServer } = require("peer");

const PORT = process.env.WEB_PORT || 8000;

const app = express();
const server = http.createServer(app);

// === Peer Server ===

const peerServer = ExpressPeerServer(server, {
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
for (const func of server.listeners("upgrade")) {
    server.removeListener("upgrade", func);
    peerWebSocketListener = func;
}

// === Static HTTP Server ===

app.use(express.static(path.resolve(__dirname, "../web")));

app.get("/", (request, response) => {
    response.sendFile(path.resolve(__dirname, "../web/index.html"));
});

app.get("/test", (request, response) => {
    response.statusCode = 426;
    response.contentType = "text/plain";
    const body = http.STATUS_CODES[426];

    response.end(body);
});

// === Web Socket ===

const websocketServer = new ws.Server({ noServer: true });

websocketServer.on("connection", function(ws) {
    console.log("New connection");
    ws.on("message", function (rawMessage) {
        console.log("Got a message");
        ws.send(rawMessage);
    });
});

server.on("upgrade", function(request, socket, head) {
    if (request.url && request.url.startsWith("/peer")) {
        // A connection to the peer, let the peer server handle the request
        peerWebSocketListener(request, socket, head);
    } else {
        // A connection to the svcraft-audio websocket, let the websocketServer
        // handle the request.
        websocketServer.handleUpgrade(request, socket, head, function(socket) {
            websocketServer.emit("connection", socket, request);
        });
    }
});

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
 * Get a server by id.
 * 
 * @param {number} id The id of the server to get.
 * @returns {ServerData | null} The found server, or null.
 */
function getServer(id) {
    for (const server of servers) {
        if (server.id == id) {
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
        if (connectId.id == id) {
            return connectId;
        }
    }
    return null;
}

/**
 * Get a ConnectedUser instance from a websocket.
 * 
 * @param {ws} websocket The websocket to get the user for.
 * @returns {ws | null} The ConnectedUser instance, or null if none is found.
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
 * Data from a plugin / a server, that clients can connect to.
 */
class ServerData {
    /**
     * Create a new ServerData.
     * 
     * @param {number} id The id of the server.
     */
    constructor(id, websocket) {
        /**
         * The id of the server.
         * @type {number}
         */
        this.id = id;
        /**
         * The websocket connection to the server.
         * @type {ws}
         * */
        this.websocket = websocket;
        /**
         * A list of users that are connected to this server.
         * @type {ConnectedUser[]}
         */
        this.connectedUsers = [];
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
     * @param {string} id The connect id.
     * @param {string} username The username of the player.
     */
    constructor(id, username) {
        /**
         * The connect id.
         * @type {string}
         */
        this.id = id;
        /**
         * The username of the player.
         * @type {string}
         */
        this.username = username;
    }
}

/**
 * A user that is connected.
 */
class ConnectedUser {
    /**
     * Create a new ConnectedUser.
     * 
     * @param {ws} websocket The web socket to the user.
     * @param {string} id The id of the user.
     * @param {string} username The username of the player.
     */
    constructor(websocket, id, username) {
        /**
         * The web socket to the user.
         * @type {ws}
         * */
        this.websocket = websocket;
        /**
         * The id of the user. This will be their peer id that is used to connect
         * to other users via peer-to-peer WebRTC. This is also what the server
         * uses to refer to users to specify which user to send messages to.
         * @type {string}
         */
        this.id = id;
        /**
         * The username of the player.
         * @type {string}
        */
        this.username = username;
    }

}

// Start

server.listen(PORT);
console.log("Server listening on port " + PORT);