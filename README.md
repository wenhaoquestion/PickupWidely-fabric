# Pickup Range

A Fabric mod for Minecraft that lets server operators and players configure how far they can reach to pick up items and XP orbs.

---

## Features

### Core
- Configurable **item pickup range** per player (default: 1.5 blocks, vanilla ≈ 1.25)
- Configurable **XP orb attraction range** per player (default: 8 blocks, same as vanilla)
- **Per-player overrides** stored server-side and persisted across restarts

### Settings GUI
- Press **R** (rebindable) to open the in-game **Pickup Range Settings** screen
- Two sliders — one for item range, one for XP range
- Sliders are bounded by the server's configured min/max; changes are applied instantly via commands
- Works in singleplayer and on servers that have the mod installed

### Commands
Full `/pickuprange` command suite for operators (see [Commands](#commands) below).

### Networking
- **Handshake on join** — server detects whether the client has the mod and syncs config automatically
- Works with **server-only**, **client-only**, and **both-sides** deployments
- Graceful fallback: a client without the mod can still join a server with the mod and benefit from the expanded range server-side

### Events API
- `PickupRangeCallback.ITEM_PICKUP` — other mods can cancel extended-range pickups

---

## Installation

| Scenario | Instructions |
|----------|-------------|
| **Server only** | Drop the mod JAR into the server's `mods/` folder. Clients without the mod can still join. |
| **Client only** | Drop the JAR into the client `mods/` folder. Works in singleplayer. On multiplayer servers without the mod, range changes have no effect. |
| **Both sides** | Install on both server and client for the full feature set: config sync, per-player ranges, and the settings GUI. |

Game runtime requirements: **Minecraft 1.16.5**, **Fabric Loader ≥ 0.15.11**, **Fabric API**, and **Java 8**.

---

## Configuration

### Server config — `config/pickup-range-server.json`

Generated automatically on first launch.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `defaultItemRange` | double | `1.5` | Item pickup range for all players (blocks) |
| `defaultXpRange` | double | `8.0` | XP orb attraction range (blocks) |
| `maxRange` | double | `64.0` | Hard cap — no player can exceed this |
| `minRange` | double | `0.5` | Hard floor — no player can go below this (minimum `0.1`) |
| `allowPlayerOverride` | boolean | `true` | Allow players to change their own range via GUI or commands |
| `requirePermission` | boolean | `false` | Require op level ≥ 2 to use `/pickuprange set` on self |

## Settings GUI

Press **R** in-game (rebindable under **Controls → Pickup Range**) to open the settings screen.

```
┌─────────────────────────────────────┐
│       Pickup Range Settings         │
│                                     │
│  Item Pickup Range                  │
│  [══════════════|──────] 5.0 blocks │
│                                     │
│  XP Orb Range                       │
│  [═══════════════════|─] 12.0 blocks│
│                                     │
│      [ Apply ]    [ Cancel ]        │
└─────────────────────────────────────┘
```

- Sliders are clamped to the server's configured `minRange` / `maxRange`
- Clicking **Apply** sends the values to the server; if the server has `allowPlayerOverride: false`, you will see an error in chat

---

## Commands

All commands are under `/pickuprange`.

| Command | Permission | Description |
|---------|-----------|-------------|
| `/pickuprange get` | Any player | Show your current item and XP range |
| `/pickuprange get <player>` | Op (level 2) | Show another player's range |
| `/pickuprange set <range>` | Any player* | Set your item pickup range |
| `/pickuprange set <player> <range>` | Op (level 2) | Set another player's item range |
| `/pickuprange setxp <range>` | Any player* | Set your XP orb attraction range |
| `/pickuprange setxp <player> <range>` | Op (level 2) | Set another player's XP range |
| `/pickuprange reset` | Any player* | Reset your range to server default |
| `/pickuprange reset <player>` | Op (level 2) | Reset another player's range |
| `/pickuprange reload` | Op (level 2) | Hot-reload the server config from disk |

\* Requires `allowPlayerOverride: true`. If `requirePermission: true`, also needs op level ≥ 2.

### Examples

```
/pickuprange set 10          # Set your item range to 10 blocks
/pickuprange setxp 20        # Set your XP range to 20 blocks
/pickuprange set Steve 5     # Set Steve's item range to 5 blocks
/pickuprange reset           # Reset your ranges to server defaults
/pickuprange reload          # Reload config without restarting
```

---

## Version Compatibility

| Mod Version | Minecraft |
|-------------|-----------|
| 2.0.1+1.16.5 (this branch) | 1.16.5 |
| 0.0.1 | 1.20.4 |

---

## FAQ

**Do clients need this mod installed?**
No. The server-side installation handles the core functionality. The client mod adds the settings GUI and receives config sync from the server.

**Does this work with other pickup-related mods?**
Generally yes. The `PickupRangeCallback.ITEM_PICKUP` event lets other mods cancel pickups triggered by this mod.

**How do I give different players different ranges?**
Use `/pickuprange set <player> <range>` as an operator. Ranges are persisted in `<world>/data/pickuprange_players.json`.

**The GUI sliders are very wide — why?**
The slider range reflects the server's `maxRange` (default 64 blocks). If you want tighter sliders, lower `maxRange` in the server config.

**What happens if `allowPlayerOverride` is false?**
The GUI still opens but clicking Apply will fail with a server-side permission error in chat. Operators can still set ranges with the command.

---

## Building from Source

Build and development requirements: **JDK 17** and an internet connection (the first build downloads Minecraft assets). The compiled mod still targets Java 8 for the Minecraft 1.16.5 game runtime.

```bash
git clone https://github.com/wenhaoquestion/PickupWidely-fabric.git
cd PickupWidely-fabric
git switch mc/1.16.5
./gradlew build
# Output JAR: build/libs/pickup-range-2.0.1+1.16.5.jar
```

```bash
./gradlew runClient   # test client
./gradlew runServer   # test server
```

---

## License

The 2.0 rewrite is MIT-licensed — see [LICENSE](LICENSE). Credit to HadronCollision for the original PickupWidely concept.
