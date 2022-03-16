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
 * The current page/tab div that the user is seeing.
 */
let currentPage = "loading";

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
 * Connect to the peer connection broker service. Returns a promise that resolves
 * when the connection is established, or reject if it fails.
 * 
 * @returns {Promise<void>} The promise.
 */
function connectToPeer() {
    return new Promise(function(resolve, reject) {

        document.getElementById("connecting-status").innerHTML = "Connecting to peer";

        peer = new Peer(username, {
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
                if (user.username == call.peer) {
                    warningLog("Got call for already connected user " + call.peer + ", replacing them");
                    user.close();
                }
            }
            call.answer(microphone);
            newUser(call);
        });
    });
}