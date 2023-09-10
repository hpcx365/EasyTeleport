# EasyTeleport

A simple mod adding `/anchor`, `/tpp` and `/tpb`.

Made for servers! Clients don't have to install EasyTeleport to connect to the server.

## Usage

`/anchor set <anchorName>` Set an anchor at the player's current position.

`/anchor remove <anchorName>` Remove a specific anchor.

`/anchor clear` Remove all anchors.

`/anchor list` List all anchors.

`/anchor limit <anchorLimit>` Set the maximum number of anchors that players can have.

`/tpp` Undo the `/tpb`.

`/tpp <anchorName>` Teleport the player to a specific anchor.

`/tpp depth <stackDepth>` Set teleport stack depth, which can be set in **easyConfig.properties** as well.

`/tpb` Teleport the player to the position where the command `/tpp <anchorName>` was most recently called.

## Github

[EasyTeleport](https://github.com/LittleAmber2005/EasyTeleport)

[LittleAmber2005](https://github.com/LittleAmber2005)

## License Statements

### Fiber

EasyTeleport depends on and includes [Fiber](https://github.com/FabLabsMC/fiber), a Fabric configuration library, which is licensed under the [Apache License 2.0](https://github.com/FabLabsMC/fiber/blob/master/LICENSE).

Fiber is Copyright FabLabsMC. No modifications were made to the original source.
