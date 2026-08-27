package com.example.pickuprange.config;

import com.example.pickuprange.PickupRangeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side configuration for Pickup Range.
 *
 * <p>Serialized as {@code config/pickup-range-server.json}. All fields have safe defaults
 * so a corrupt or missing file always falls back gracefully without crashing.
 *
 * <p>Field names in this class are the JSON property names — do not rename them without
 * updating saved configs.
 */
public final class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Default values ---
    private static final double DEFAULT_ITEM_RANGE = 1.5;
    private static final double DEFAULT_XP_RANGE   = 8.0;
    private static final double DEFAULT_MAX_RANGE   = 64.0;
    private static final double DEFAULT_MIN_RANGE   = 0.5;

    // --- Config fields (directly serialized) ---

    /** Default item pickup range for all players, in blocks. */
    private double defaultItemRange = DEFAULT_ITEM_RANGE;

    /** Default XP orb pickup/attraction range for all players, in blocks. */
    private double defaultXpRange = DEFAULT_XP_RANGE;

    /** Hard cap — no player may exceed this range. */
    private double maxRange = DEFAULT_MAX_RANGE;

    /** Hard floor — no player may set a range lower than this. */
    private double minRange = DEFAULT_MIN_RANGE;

    /** If {@code true}, players can set their own range via {@code /pickuprange set}. */
    private boolean allowPlayerOverride = true;

    /**
     * If {@code true}, changing your own range requires the {@code pickuprange.self} permission.
     * Ops (level ≥ 2) are always exempt.
     */
    private boolean requirePermission = false;

    // --- Accessors ---

    /** @return default item pickup range in blocks */
    public double getDefaultItemRange() { return defaultItemRange; }

    /** @return default XP orb attraction range in blocks */
    public double getDefaultXpRange()   { return defaultXpRange; }

    /** @return hard maximum range any player may use */
    public double getMaxRange()         { return maxRange; }

    /** @return hard minimum range any player may use */
    public double getMinRange()         { return minRange; }

    /** @return whether players are allowed to override their own range */
    public boolean isAllowPlayerOverride() { return allowPlayerOverride; }

    /** @return whether the {@code pickuprange.self} permission node is enforced */
    public boolean isRequirePermission()   { return requirePermission; }


    /**
     * Clamps {@code value} to the configured [{@link #minRange}, {@link #maxRange}] interval.
     *
     * @param value the raw value to clamp
     * @return a value guaranteed to be within the valid range
     */
    public double clamp(double value) {
        return Math.max(minRange, Math.min(maxRange, value));
    }

    // --- Persistence ---

    /**
     * Loads the server config from {@code configPath}, falling back to defaults on any error.
     *
     * @param configPath path to the JSON config file
     * @return a valid, fully-populated {@link ServerConfig}
     */
    public static ServerConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
                if (loaded == null) {
                    PickupRangeMod.LOGGER.warn("Server config was empty, using defaults.");
                    return createDefaults();
                }
                loaded.validate();
                return loaded;
            } catch (JsonSyntaxException e) {
                PickupRangeMod.LOGGER.error("Server config JSON is malformed, using defaults: {}", e.getMessage());
            } catch (IOException e) {
                PickupRangeMod.LOGGER.error("Failed to read server config, using defaults.", e);
            }
        }
        // File doesn't exist or failed to parse — write defaults then return them.
        ServerConfig defaults = createDefaults();
        defaults.save(configPath);
        return defaults;
    }

    /**
     * Saves this config to {@code configPath}, creating parent directories as needed.
     *
     * @param configPath destination path
     */
    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            PickupRangeMod.LOGGER.error("Failed to save server config.", e);
        }
    }

    /**
     * Creates a new {@link ServerConfig} pre-populated with vanilla-safe defaults.
     *
     * @return new default config
     */
    public static ServerConfig createDefaults() {
        return new ServerConfig();
    }

    /**
     * Validates and clamps all values to sane bounds.
     * Called after deserialization to prevent bad config from crashing the server.
     */
    private void validate() {
        if (Double.isNaN(defaultItemRange) || defaultItemRange <= 0) {
            PickupRangeMod.LOGGER.warn("defaultItemRange is invalid ({}), resetting to {}", defaultItemRange, DEFAULT_ITEM_RANGE);
            defaultItemRange = DEFAULT_ITEM_RANGE;
        }
        if (Double.isNaN(defaultXpRange) || defaultXpRange <= 0) {
            PickupRangeMod.LOGGER.warn("defaultXpRange is invalid ({}), resetting to {}", defaultXpRange, DEFAULT_XP_RANGE);
            defaultXpRange = DEFAULT_XP_RANGE;
        }
        if (Double.isNaN(maxRange) || maxRange <= 0) {
            PickupRangeMod.LOGGER.warn("maxRange is invalid ({}), resetting to {}", maxRange, DEFAULT_MAX_RANGE);
            maxRange = DEFAULT_MAX_RANGE;
        }
        if (Double.isNaN(minRange) || minRange < 0) {
            PickupRangeMod.LOGGER.warn("minRange is invalid ({}), resetting to {}", minRange, DEFAULT_MIN_RANGE);
            minRange = DEFAULT_MIN_RANGE;
        }
        if (minRange > maxRange) {
            PickupRangeMod.LOGGER.warn("minRange ({}) > maxRange ({}), swapping.", minRange, maxRange);
            double tmp = minRange;
            minRange = maxRange;
            maxRange = tmp;
        }
        defaultItemRange = Math.max(minRange, Math.min(maxRange, defaultItemRange));
        defaultXpRange   = Math.max(0.5, defaultXpRange);
    }
}
