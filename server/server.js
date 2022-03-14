const express = require("express");
const http = require("http");
const ws = require("ws");
const { ExpressPeerServer } = require("peer");

const WEB_PORT = process.env.WEB_PORT || 8000;
const WS_PORT = process.env.WS_PORT || 8001;

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

// === Static HTTP Server ===

app.use(express.static(__dirname));

app.get("/", (request, response) => {
    response.sendFile(__dirname + "/index.html");
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
    console.log("got upgrade");
    websocketServer.handleUpgrade(request, socket, head, function(socket) {
        console.log("done");
        websocketServer.emit('connection', socket, request);
    });
});

// Start server

server.listen(WEB_PORT);
console.log("Server listening on port " + WEB_PORT);