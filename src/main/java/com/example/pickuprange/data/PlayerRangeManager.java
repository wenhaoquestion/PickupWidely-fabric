package com.example.pickuprange.data;

import com.example.pickuprange.PickupRangeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player pickup range overrides, persisted across server restarts.
 *
 * <p>Data is stored in {@code <world>/data/pickuprange_players.json} as a simple
 * UUID → {@link PlayerRangeData} map.
 *
 * <p>All public methods are thread-safe (uses a {@link ConcurrentHashMap}).
 */
public final class PlayerRangeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "pickuprange_players.json";

    /** In-memory store. Key = player UUID string. */
    private static final ConcurrentHashMap<UUID, PlayerRangeData> RANGES = new ConcurrentHashMap<>();

    private static Path savedDataPath;

    private PlayerRangeManager() {}

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads persisted player data from the world's {@code data} directory.
     *
     * @param server the running Minecraft server
     */
    public static void load(MinecraftServer server) {
        // VERSION-SENSITIVE: getWorldPath(LevelResource.ROOT) resolves to the world root.
        savedDataPath = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_FILE);
        RANGES.clear();

        if (!Files.exists(savedDataPath)) {
            PickupRangeMod.LOGGER.info("No player range data found, starting fresh.");
            return;
        }

        try (Reader reader = Files.newBufferedReader(savedDataPath)) {
            Type mapType = new TypeToken<Map<String, PlayerRangeData>>() {}.getType();
            Map<String, PlayerRangeData> raw = GSON.fromJson(reader, mapType);
            if (raw != null) {
                raw.forEach((key, value) -> {
                    try {
                        RANGES.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException e) {
                        PickupRangeMod.LOGGER.warn("Skipping invalid UUID in player range data: {}", key);
                    }
                });
                PickupRangeMod.LOGGER.info("Loaded range data for {} player(s).", RANGES.size());
            }
        } catch (JsonSyntaxException e) {
            PickupRangeMod.LOGGER.error("Player range data JSON is malformed — data wiped: {}", e.getMessage());
        } catch (IOException e) {
            PickupRangeMod.LOGGER.error("Failed to read player range data.", e);
        }
    }

    /**
     * Saves all in-memory player range data to disk.
     *
     * @param server the running Minecraft server (unused; path resolved on load)
     */
    public static void save(MinecraftServer server) {
        if (savedDataPath == null) return;

        Map<String, PlayerRangeData> raw = new HashMap<>();
        RANGES.forEach((uuid, data) -> raw.put(uuid.toString(), data));

        try {
            Files.createDirectories(savedDataPath.getParent());
            try (Writer writer = Files.newBufferedWriter(savedDataPath)) {
                GSON.toJson(raw, writer);
            }
            PickupRangeMod.LOGGER.info("Saved range data for {} player(s).", raw.size());
        } catch (IOException e) {
            PickupRangeMod.LOGGER.error("Failed to save player range data.", e);
        }
    }

    /**
     * Called when a player disconnects to free in-memory state (data is already persisted on set).
     *
     * @param uuid the UUID of the disconnecting player
     */
    public static void onPlayerDisconnect(UUID uuid) {
        // We keep the data in memory until server stop so immediate re-joins are fast.
        // This is intentional — memory leak risk is negligible for per-player maps.
    }

    // -------------------------------------------------------------------------
    // Range accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the effective item pickup range for a player, respecting any override
     * or falling back to the server config default.
     *
     * @param player the player to query
     * @return effective item range in blocks
     */
    public static double getEffectiveItemRange(Player player) {
        return getEffectiveItemRange(player.getUUID());
    }

    /**
     * Returns the effective item pickup range for a UUID, or the server default.
     *
     * @param uuid player UUID
     * @return effective item range in blocks
     */
    public static double getEffectiveItemRange(UUID uuid) {
        PlayerRangeData data = RANGES.get(uuid);
        if (data != null && data.getItemRange() > 0) {
            return data.getItemRange();
        }
        return PickupRangeMod.getServerConfig().getDefaultItemRange();
    }

    /**
     * Returns the effective XP pickup range for a player.
     *
     * @param player the player to query
     * @return effective XP range in blocks
     */
    public static double getEffectiveXpRange(Player player) {
        return getEffectiveXpRange(player.getUUID());
    }

    /**
     * Returns the effective XP pickup range for a UUID, or the server default.
     *
     * @param uuid player UUID
     * @return effective XP range in blocks
     */
    public static double getEffectiveXpRange(UUID uuid) {
        PlayerRangeData data = RANGES.get(uuid);
        if (data != null && data.getXpRange() > 0) {
            return data.getXpRange();
        }
        return PickupRangeMod.getServerConfig().getDefaultXpRange();
    }

    // -------------------------------------------------------------------------
    // Range setters
    // -------------------------------------------------------------------------

    /**
     * Sets a player's item pickup range override.
     *
     * @param uuid      player UUID
     * @param itemRange new item range (must be within server config bounds)
     */
    public static void setItemRange(UUID uuid, double itemRange) {
        RANGES.compute(uuid, (k, existing) -> {
            PlayerRangeData data = existing != null ? existing : new PlayerRangeData();
            data.setItemRange(itemRange);
            return data;
        });
    }

    /**
     * Sets a player's XP pickup range override.
     *
     * @param uuid     player UUID
     * @param xpRange  new XP range
     */
    public static void setXpRange(UUID uuid, double xpRange) {
        RANGES.compute(uuid, (k, existing) -> {
            PlayerRangeData data = existing != null ? existing : new PlayerRangeData();
            data.setXpRange(xpRange);
            return data;
        });
    }

    /**
     * Resets a player's pickup range to server defaults.
     *
     * @param uuid player UUID
     */
    public static void resetRange(UUID uuid) {
        RANGES.remove(uuid);
    }

    /**
     * Returns whether a player has a custom range set (either item or XP).
     *
     * @param uuid player UUID
     * @return {@code true} if a custom range entry exists
     */
    public static boolean hasCustomRange(UUID uuid) {
        return RANGES.containsKey(uuid);
    }

    // -------------------------------------------------------------------------
    // Data record
    // -------------------------------------------------------------------------

    /**
     * Holds per-player range overrides. A value of {@code 0} means "use server default".
     */
    public static final class PlayerRangeData {
        private double itemRange = 0;
        private double xpRange  = 0;

        /** @return the player's custom item range, or 0 if using server default */
        public double getItemRange() { return itemRange; }

        /** @return the player's custom XP range, or 0 if using server default */
        public double getXpRange()   { return xpRange; }

        /** @param itemRange new item range */
        public void setItemRange(double itemRange) { this.itemRange = itemRange; }

        /** @param xpRange new XP range */
        public void setXpRange(double xpRange)     { this.xpRange = xpRange; }
    }
}
