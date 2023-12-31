# EasyTeleport

A mod that implements multiple teleport functions, adding `/tpb`, `/tpp`, `/tpa`, `/tphere`, `/tpaccept`, `/home`,
`/sethome`, `/anchor`, `/public` and `/tpconfig`.

Made for servers! Clients don't have to install EasyTeleport to connect to the server.

## Usage

`/tpb` Teleport you to your previous position, including where you died.

`/tpp` Revoke the command `/tpb`.

`/tpp <anchor-name>` Teleport you to an anchor.

`/tpa <target-player>` Request to teleport you to a player.

`/tphere <origin-player>` Request to teleport a player to you.

`/tpaccept` Accept all requests that have not timed out.

`/tpaccept <origin-player>` Accept the request from a player.

`/home` Teleport you to home.

`/sethome` Set home at your current position.

`/anchor list` List all your anchors.

`/anchor clear` Remove all your anchors.

`/anchor set <anchor-name>` Set an anchor at your current position.

`/anchor remove <anchor-name>` Remove an anchor.

`/anchor share <anchor-name>` Share an anchor with all players.

`/anchor share <anchor-name> <target-player>` Share an anchor with a player.

`/anchor accept` Accept all received anchors.

`/anchor accept <anchor-name>` Accept a received anchor.

`/public list` List all public anchors.

`/public clear` Remove all public anchors.

`/public set <anchor-name>` Set a public anchor at your current position.

`/public remove <anchor-name>` Remove a public anchor.

`/tpconfig depth <stack-depth>` Set the teleport stack depth.

`/tpconfig limit <anchor-limit>` Set the maximum number of anchors that players can have.

`/tpconfig timeout <request-timeout>` Set the timeout of requests in milliseconds.

`/tpconfig default` Restore all default values.

`stack-depth`, `anchor-limit` and `request-timeout` can be set in **_easy-teleport.properties_** as well.
