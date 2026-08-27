# Changelog

All notable changes to Pickup Range are documented here.

---

## [2.0.0] — 2026-04-06

Complete rewrite of the mod.

### Added
- **Mojang official mappings** — replaced Yarn mappings for forward compatibility with future unobfuscated Minecraft versions
- **Per-player pickup ranges** — stored in `<world>/data/pickuprange_players.json`, persisted across restarts
- **XP orb range** — separate configurable attraction radius for experience orbs
- **Commands** — `/pickuprange set/get/reset/setxp/reload` with proper permission checks
- **Networking handshake** — server detects mod presence on client and syncs config on join
- **Magnet animation** — optional smooth item attraction toward player before pickup
- **Particle ring indicator** — client-side visual showing current pickup radius (toggle with `R`)
- **Hot-reload** — `/pickuprange reload` applies config changes without a server restart
- **`PickupRangeCallback` event** — allows other mods to intercept or cancel extended pickups
- **Dual config system** — separate server (`pickup-range-server.json`) and client (`pickup-range-client.json`) config files
- **`ExperienceOrbMixin`** — extends XP orb attraction range beyond vanilla 8 blocks
- **`PlayerRangeManager`** — thread-safe in-memory store with JSON persistence

### Changed
- Mod ID changed from `pickupwidely` to `pickuprange`
- Package changed from `me.hadroncollision.pickupwidely` to `com.example.pickuprange`
- Logger migrated from `java.util.logging.Logger` to `org.slf4j.Logger` (Fabric standard)
- Pickup radius type changed from `byte` (max 127) to `double` (0.5–64.0)
- Config format changed from `.properties` to JSON
- Minimum Java version raised from 17 to 21 (required for Minecraft 1.21+)

### Fixed
- Original mixin only searched for one nearby player — now handles multiple players
- Original mixin used `BlockPos` wrapping which lost fractional coordinates
- Config used `System.out.println` instead of the mod logger
- Pickup delay (`pickupDelay`) was not respected — could pick up freshly dropped items

### Removed
- `SimpleConfig`, `ModConfigProvider`, `ModConfigs` — replaced by `ServerConfig` / `ClientConfig` using Gson

---

## [0.0.1-1.20.4] — 2024-02-10

- Initial release for Minecraft 1.20.4
- Single global pickup radius, byte-sized (1–127 blocks)
- `.properties`-based config

## [0.0.1-1.20.1] — 2024-01-15

- Initial release for Minecraft 1.20.1
