# SVCraft Audio
Server-side only proximity chat.

Makes players able to talk when they are near each other in game.

The fact that this is server-side only simplifies things a lot for players as they don't have to install any client-side mods to use the proximity chat. They just run `/audio` and click the link.

## How does it work?
Players open a link to a website, this website asks for microphone permissions and the audio is sent to other nearby players using peer-to-peer connections.

## Configuration
```yaml
# SVCraft Audio configuration.
# 
#     hearDistance (default: 32)
# The distance in blocks you need to be to another player to hear them.
# 
#     connectDistance (default: 40)
# The distance in blocks you need to be to another player for their audio to
# start connecting. This should be slightly more than hearDistance because
# connecting isn't instant, therefore players are connected a bit before the
# players can actually hear each other.
# 
#     disconnectDistance (default: 50)
# The distance in blocks you need to be to another player for their audio to
# disconnect. This should be set to slightly more than connectDistance to avoid
# players disconnecting and reconnecting often when being on the edge of the
# connection range.
# 
#     updateTaskInterval (default: 20)
# The interval in ticks when the update task runs.
# 
#     url (default: https://svcraft-audio.alvinn8.repl.co)
# The URL of the svcraft-audio website to connect to.
# 
#     debug (default: false)
# Whether debug mode is enabled.

hearDistance: 32
maxVolumeDistance: 8
connectDistance: 40
disconnectDistance: 50
updateTaskInterval: 20
debug: false
url: https://svcraft-audio.alvinn8.repl.co
```

It is recomended to host your own websocket server and change to `url` to that. To do so, download this repository and copy the `server` and `web` folders. Then run `node server.js`. Note that the server needs https and wss, so a host like repl.it might be a good choice.

## Technical information
![image showing the technical parts of how svcraft-audio works.](img/svcraft-audio.svg)

Players join the server and can that way communicate with the plugin using commands and chat messages.

The server connects to the svcraft-audio websocket server. This websocket server has two "sides", one that handles messages to and from the client, and one to and from the plugin.

The players visit the svcraft-audio website using a web browser, fetching the html, css and javascript from a static web server.

There they connect to the svcraft-audio websocket server, and are wired to the side that handels messages to and from the clients (players via the website).

They also connect to a peer connection broker server. This is used to broker (start/negotiate) peer-to-peer connections with other users.

When a peer-to-peer connection is established the microphone sound can travel between the users, without the audio going trough any server. This is shown with the green line with an audio icon above it.

### Implementation
The blue/gray server icons in the picture, all run on the same (physical) server, same port. The code for that can be found in the `server` directory, and the static content that the http server serves can be found in `web`.

The plugin can be found in the `plugin` folder.

At first, the websocket server looked like it could be in the plugin, but the servers has to be over https and wss, otherwise browsers will not allow microphone permissions.

"connect ids" are used to create a short id that is added to the url, the websocket server can then send the user id and username to the client when it provides the connect id. The websocket server will also know about the plugin/server/server id.