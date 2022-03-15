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

// Start server

server.listen(PORT);
console.log("Server listening on port " + PORT);