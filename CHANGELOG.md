# Changelog

All notable changes to Pickup Range are documented here.

---

## [2.0.1+1.16.5] — 2026-08-26

Maintenance release for Minecraft 1.16.5.

### Fixed
- Require the intended operator level for privileged commands.
- Exclude spectators and dead or dying players from extended item pickup.
- Preserve vanilla XP-orb target selection and movement timing while applying custom ranges.

---

## [2.0.0+1.16.5] — 2026-08-26

Complete rewrite of the mod.

### Added
- **Minecraft 1.16.5 port** — built against Yarn `1.16.5+build.10` and the legacy Fabric networking APIs
- **Per-player pickup ranges** — stored in `<world>/data/pickuprange_players.json`, persisted across restarts
- **XP orb range** — separate configurable attraction radius for experience orbs
- **Commands** — `/pickuprange set/get/reset/setxp/reload` with proper permission checks
- **Networking handshake** — server detects mod presence on client and syncs config on join
- **Settings screen** — client-side GUI for item and XP ranges, opened with the rebindable `R` key
- **Hot-reload** — `/pickuprange reload` applies config changes without a server restart
- **`PickupRangeCallback` event** — allows other mods to intercept or cancel extended pickups
- **JSON server config** — server defaults, bounds, and override permissions in `pickup-range-server.json`
- **`ExperienceOrbMixin`** — extends XP orb attraction range beyond vanilla 8 blocks
- **`PlayerRangeManager`** — thread-safe in-memory store with JSON persistence

### Changed
- Mod ID changed from the legacy `pickupwidely` / `item-pickup-range` IDs to `pickuprange`
- Package changed from `me.hadroncollision.pickupwidely` to `com.example.pickuprange`
- Logger migrated from `java.util.logging.Logger` to Log4j
- Pickup radius type changed from `byte` (max 127) to `double` (default bounds 0.5–64.0)
- Config format changed from `.properties` to JSON
- Java 8 target (required for Minecraft 1.16.5)

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
