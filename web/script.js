/**
 * The connection to the peer connection broker.
 * @type {Peer}
 */
let peer;

/**
 * The micrphone stream.
 * @type {MediaStream}
 */
let microphone;

/**
 * A list of users that are connected and can be heard.
 * @type {ConnectedUser[]}
 */
let connectedUsers = [];

/**
 * A set of user ids that should be heard.
 * @type {Set<string>}
 */
let expectedConnectedUsers = new Set();

/**
 * The connection to the svcraft-audio websocket. Which indirectly connects to
 * the plugin.
 * @type {WebSocket}
 */
let serverConnection;

/**
 * The id of this user.
 * @type {string}
 */
 let userId;

/**
 * The name of the player.
 * @type {string}
 */
let username;

/**
 * Whether to send connected peers information to the server every check.
 * @type {boolean}
 */
let sendConnectedPeers = false;

/**
 * Whether the svcraft-audio websocket has a connection to the plugin. If null it
 * is currently unknown.
 * @type {boolean | null}
 */
let hasPluginConnection = null;

/**
 * The last time the server was notified about a desync.
 * @type {number}
 */
let lastNotifyServerOfDesync = Date.now();

/**
 * The current page/tab div that the user is seeing.
 */
let currentPage = "loading";

/**
 * The testing-microphone toggle, whether the user should hear themselves right now.
 * @type {boolean}
 */
let testingMicrophone = false;

/**
 * A user that is connected.
 */
class ConnectedUser {
    /**
     * Create a new ConnectedUser.
     * 
     * @param {Peer.MediaConnection} call The peer.js call to this user.
     * @param {string} userId The id of this user, also their peer id.
     * @param HTMLAudioElement element The audio element where their microphone sound is being played.
     */
    constructor(call, userId, element) {
        /**
         * The peer.js call to this user.
         * @type {Peer.MediaConnection}
         */
        this.call = call;
        /**
         * The id of this user, also their peer id.
         * @type {string}
         */
        this.id = userId;
        /**
         * The audio element where their microphone sound is being played.
         * @type {HTMLAudioElement}
         */
        this.element = element;
        /**
         * The microphone stream for this user.
         * @type {MediaStream}
         */
        this.stream = null;
    }

    /**
     * Close the connection to this user and remove them.
     */
    close() {
        // Close the call
        this.call.close();
        // Remove the audio element
        this.element.remove();

        // Remove them from the list
        if (connectedUsers.includes(this)) {
            connectedUsers.splice(connectedUsers.indexOf(this), 1);
        }
    }
}

/**
 * Connect to the peer connection broker service. Returns a promise that resolves
 * when the connection is established, or reject if it fails.
 * 
 * @returns {Promise<void>} The promise.
 */
function connectToPeer() {
    return new Promise(function(resolve, reject) {

        document.getElementById("connecting-status").innerHTML = "Connecting to peer";

        peer = new Peer(userId, {
            host: location.hostname,
            port: location.port,
            debug: 1,
            path: '/peer'
        });

        peer.on("open", function() {
            document.getElementById("peer-connection").innerHTML = `<i class="bi bi-check-square-fill text-success"></i> Connected`;
            updateConnectionInfo();
            resolve();
        });

        peer.on("close", function() {
            document.getElementById("peer-connection").innerHTML = `<i class="bi bi-x-square-fill text-danger"></i> Not Connected`;
            updateConnectionInfo();
            reject();
        });

        peer.on("call", function(call) {
            for (let i = connectedUsers.length - 1; i >= 0; i--) {
                const user = connectedUsers[i];
                if (user.id == call.peer) {
                    warningLog("Got call for already connected user " + call.peer + ", replacing them");
                    user.close();
                }
            }
            call.answer(microphone);
            newUser(call);
        });
    });
}

/**
 * Handle the call for a new user, this could either be a user that this user just
 * asked to connect to, or it could be an incomming call that was answered.
 *
 * @param {Peer.MediaConnection} call 
 */
function newUser(call) {
    for (let i = connectedUsers.length - 1; i >= 0; i--) {
        const user = connectedUsers[i];
        if (user.id == call.peer) {
            warningLog("Connecting a user that already exists, replacing it");
            user.close();
        }
    }

    const element = document.createElement("audio");
    element.autoplay = true;
    element.volume = 0;
    element.className = "user-audio";
    element.setAttribute("data-user-id", call.peer);
    document.body.appendChild(element);

    const user = new ConnectedUser(call, call.peer, element);
    connectedUsers.push(user);

    call.on("stream", function(stream) {
        user.stream = stream;
        user.element.srcObject = stream;
    })

    call.on("close", function() {
        console.log("call with " + call.peer + " was closed.");
        disconnectUser(call.peer);
        updateConnectionInfo();
    });
}

/**
 * Update the connection info in the bottom right corner.
 */
function updateConnectionInfo() {
    // The connection to the svcraft-audio websocket (indirectly the plugin)
    const isServerConnected = serverConnection == null ? false : serverConnection.readyState == WebSocket.OPEN;
    document.getElementById("svcraft-audio-connection").innerHTML =
        isServerConnected
        ? `<i class="bi bi-check-square-fill text-success"></i> Connected`
        : `<i class="bi bi-x-square-fill text-danger"></i> Not Connected`;

    // The connection to the peer connection broker
    document.getElementById("peer-connection").innerHTML =
        (peer == null ? false : peer.open)
        ? `<i class="bi bi-check-square-fill text-success"></i> Connected`
        : `<i class="bi bi-x-square-fill text-danger"></i> Not Connected`;

    // Are the correct amount of users connected?
    const usersCount = connectedUsers.length;
    let connectedUserCount = 0;
    const expectedPeerCount = expectedConnectedUsers.size;

    // The amount of users that are actually connected, not just in the array
    for (const user of connectedUsers) {
        if (user.call.open) connectedUserCount++;
    }

    // Are all these three numbers the same?
    const allGood = usersCount == connectedUserCount && usersCount == expectedPeerCount;

    document.getElementById("connected-peers").innerHTML =
    allGood
    ? `<i class="bi bi-check-square-fill text-success"></i> Correct amount`
    : `<span class="text-danger"><i class="bi bi-x-square-fill text-danger"></i> Incorrect amount</span>`;

    // Does the svcraft-audio websocket have a connection to the plugin?
    if (typeof hasPluginConnection == "boolean" || !isServerConnected) {
        document.getElementById("server-connection").innerHTML =
        hasPluginConnection
        ? `<i class="bi bi-check-square-fill text-success"></i> Connected`
        : `<i class="bi bi-x-square-fill text-danger"></i> Not Connected`;
    }

    // Has the server requested the connected peers information recently?
    if (sendConnectedPeers) {
        // ... in that case, send it
        sendConnectedPeers = false;
        if (serverConnection != null && serverConnection.readyState == WebSocket.OPEN) {
            serverConnection.send("connected-peers " + allGood + ` ${usersCount} / ${connectedUserCount} / ${expectedPeerCount}`);
        }
    }

    if (!allGood) {
        console.log("We are desynced");
        // Only notify the server once every 30 seconds to avoid it being too spammy
        if ((Date.now() - lastNotifyServerOfDesync) > 1000 * 29) {
            warningLog("desync ("+ ` ${usersCount} / ${connectedUserCount} / ${expectedPeerCount}` +"): connected: " + connectedUsers.map(user => user.id).join(", ") + " | expected: " + Array.from(expectedConnectedUsers).join(", "));
            for (let i = connectedUsers.length - 1; i >= 0; i--) {
                const user = connectedUsers[i];
                if (!user.call.open) {
                    warningLog(user.id + " was not connected, removing them.");
                    user.close();
                }
            }
        }
    }
}

/**
 * Connect to the websocket server and send the connect id. Returns a promise that
 * resolves when the server has replied with the user id and username.
 * 
 * @param {string} serverUrl The url to the websocket server to connect to.
 * @param {string} connectId The connect id to specify after connecting.
 * @returns The promise.
 */
function connectToServer(serverUrl, connectId) {
    return new Promise(function (resolve, reject) {

        serverConnection = new WebSocket(serverUrl);
        serverConnection.addEventListener("open", function () {
            serverConnection.send("Connect id: " + connectId);
        });
        serverConnection.addEventListener("close", function () {
            if (currentPage == "loading"
            || currentPage == "connecting"
            || currentPage == "ready") {
                showPage("connection-lost");
                closeAll();
            }
            reject();
        });

        serverConnection.addEventListener("message", function (e) {
            const message = e.data + "";
            console.log(message);

            if (message.startsWith("Welcome")) {
                const match = /Welcome, your user id: (?<userId>[A-z0-9-]+) and your username is: (?<username>.+)/.exec(message);
                userId = match.groups.userId;
                username = match.groups.username;
                document.getElementById("ready-username").appendChild(document.createTextNode(username));
                // We would not be accepted in if there was no connected plugin
                hasPluginConnection = true;
                resolve();
            }

            handleMessage(message);
        });
    });
}

/**
 * Handle an incomming message from the svcraft-audio websocket, usually from
 * the plugin.
 * 
 * @param {string} message The message from the server.
 */
function handleMessage(message) {
    if (message.startsWith("Connect to ")) {
        const userId = message.substring("Connect to ".length);
        expectedConnectedUsers.add(userId);
        // Might be temporary desync if the timing is unfortunate,
        // so avoid telling the server
        lastNotifyServerOfDesync = Date.now();
        connectTo(userId);
    }

    if (message.startsWith("Wait for ")) {
        const userId = message.substring("Wait for ".length);
        lastNotifyServerOfDesync = Date.now();
        // The user is about to connect to us, we expect their call soon
        expectedConnectedUsers.add(userId);
    }

    if (message.startsWith("Disconnect ")) {
        const userId = message.substring("Disconnect ".length);
        expectedConnectedUsers.delete(userId);
        disconnectUser(userId);
    }

    if (message.startsWith("Volume ")) {
        const match = /Volume: (?<userId>[A-z0-9-]+): (?<volume>.+)/.exec(message);
        const { userId, volume } = match.groups;
        setVolumeFor(userId, parseFloat(volume));
    }

    if (message.startsWith("Has plugin connection? ")) {
        const value = message.substring("Has plugin connection? ".length);
        hasPluginConnection = value == "true";
    }

    if (message == "Invalid link") {
        showPage("invalid-link");
        closeAll();
    }
    if (message == "You left the game, goodbye") {
        showPage("left-the-game");
        closeAll();
    }
    if (message == "You are not on the server") {
        showPage("not-on-server");
        closeAll();
    }
    if (message == "You connected elsewhere") {
        showPage("connected-elsewhere");
        serverConnection.close("connected-elsewhere");
        serverConnection = null;
        closeAll();
    }

    if (message == "Reload") {
        location.reload();
    }

    if (message == "peersinfo") {
        sendConnectedPeers = true;
        updateConnectionInfo();
    }

    if (message == "resync") {
        console.log("Doing a resync");

        for (let i = connectedUsers.length - 1; i >= 0; i--) {
            const user = connectedUsers[i];
            user.close();
        }

        for (const element of document.getElementsByClassName("user-audio")) {
            element.remove();
            warningLog("(resync) An audio element existed even though it shouldn't, data-user-id=" + element.getAttribute("data-user-id"));
        }

        expectedConnectedUsers = new Set();
        connectedUsers = [];
    }
}

/**
 * Show a page and hide all others.
 * 
 * @param {string} page The id of the page element to show.
 */
 function showPage(page) {
    for (const element of document.getElementsByClassName("page")) {
        element.style.display = "none";
    }
    document.getElementById(page).style.display = "block";
    currentPage = page;
}

/**
 * Log a warning message to the web console and notify the plugin about the error.
 * 
 * @param {string} msg The message to log.
 */
 function warningLog(msg) {
    console.warn(msg);
    if (serverConnection != null && serverConnection.readyState == WebSocket.OPEN) {
        serverConnection.send("Warning " + msg);
    }
}

/**
 * Connect to a user and await their answer.
 * 
 * @param {string} userId The id of the user to connect to.
 */
function connectTo(userId) {
    const call = peer.call(userId, microphone);
    newUser(call);
}

/**
 * Set the volume this user should hear the specified user at.
 * 
 * @param {string} userId The id of the user to set the volume for.
 * @param {number} volume The volume of the user.
 */
function setVolumeFor(userId, volume) {
    for (const user of connectedUsers) {
        if (user.id == userId) {
            user.element.volume = volume;
        }
    }
}

/**
 * Disconnect a user.
 * 
 * @param {string} userId The id of the user to disconnect.
 */
function disconnectUser(userId) {
    let removalCount = 0;
    for (let i = connectedUsers.length - 1; i >= 0; i--) {
        const user = connectedUsers[i];
        if (user.id == userId) {
            user.close();
            removalCount++;
        }
    }
    
    if (removalCount == 0) {
        warningLog("Tried to disconnect user that wans't here: " + userId);
    } else if (removalCount != 1) {
        warningLog("Disconnected a user that was here "+ removalCount +" times: " + userId);
    }
}

/**
 * Close, disconnect and remove all users and close the microphone. This function
 * is to be used when the website should no longer be used, for example when the
 * user has left the server or disconnected.
 */
function closeAll() {
    console.log("Closing everything.");

    // Close the microphone
    if (microphone != null) {
        for (const track of microphone.getTracks()) {
            track.stop();
        }
    }
    // Close the connection to the server
    if (serverConnection != null) {
        serverConnection.close();
    }
    // Close the peer
    if (peer != null) {
        peer.destroy();
    }
    // Hide the status as it would be all red, but that's intentional
    document.getElementById("status").style.display = "none";
}

window.addEventListener("DOMContentLoaded", function() {
    document.getElementById("test-microphone").addEventListener("click", function() {
        testingMicrophone = !testingMicrophone;
        const icon = document.getElementById("test-microphone-icon");
        /** @type {HTMLAudioElement} */
        const audio = document.getElementById("test-microphone-audio");
        const warning = document.getElementById("test-microphone-warning");
        if (testingMicrophone) {
            icon.classList.remove("bi-play-fill");
            icon.classList.add("bi-pause-fill");
            audio.srcObject = microphone;
            audio.autoplay = true;
            audio.play();
            warning.style.display = "block";
        } else {
            icon.classList.remove("bi-pause-fill");
            icon.classList.add("bi-play-fill");
            audio.srcObject = null;
            audio.pause();
            warning.style.display = "none";
        }
    });
});

// Entrypoint

(async function() {
    const url = new URL(location.href);
    const connectId = url.searchParams.get("connectId");

    if (connectId == null) {
        showPage("invalid-link");
        return;
    }

    await getMicrophoneStream();

    showPage("connecting");
    document.getElementById("connecting-status").innerHTML = "Connecting to server";

    await connectToServer(serverUrl, connectId);

    await connectToPeer();

    // Unless an error page has been shown, we are ready
    if (currentPage == "connecting") {
        showPage("ready");
    }

    updateConnectionInfo();
})();

setInterval(updateConnectionInfo, 5000);
setTimeout(updateConnectionInfo, 1000);