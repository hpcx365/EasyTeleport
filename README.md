# EasyTeleport

A mod adding `/tpb`, `/tpp`, `/anchor` and `/config`.

Made for servers! Clients don't have to install EasyTeleport to connect to the server.

## Usage

`/tpb` Teleport the player to the position where the command `/tpp <anchor-name>` was most recently called.

`/tpp` Undo the `/tpb`.

`/tpp <anchor-name>` Teleport the player to a specific anchor.

`/anchor list` List all anchors.

`/anchor clear` Remove all anchors.

`/anchor set <anchor-name>` Set an anchor at the player's current position.

`/anchor remove <anchor-name>` Remove an anchor.

`/config depth <stack-depth>` Set teleport stack depth, which can be set in **_easy-teleport.properties_** as well.

`/config limit <anchor-limit>` Set the maximum number of anchors that players can have, which can be set in *
*_easy-teleport.properties_** as well.

## Github

[EasyTeleport](https://github.com/LittleAmber2005/EasyTeleport)

[LittleAmber2005](https://github.com/LittleAmber2005)

## License Statements

### Fiber

EasyTeleport depends on and includes [Fiber](https://github.com/FabLabsMC/fiber), a Fabric configuration library, which
is licensed under the [Apache License 2.0](https://github.com/FabLabsMC/fiber/blob/master/LICENSE).

Fiber is Copyright FabLabsMC. No modifications were made to the original source.
