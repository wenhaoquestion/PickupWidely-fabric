package com.example.pickuprange.config;

import com.example.pickuprange.PickupRangeMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side configuration for Pickup Range.
 *
 * <p>Serialized as {@code config/pickup-range-client.json}. Must never be accessed
 * from server-only code paths.
 */
@Environment(EnvType.CLIENT)
public final class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Item pickup range override used in singleplayer when the server mod is absent.
     * {@code 0} means use the server default (1.5 blocks).
     */
    private double rangeOverrideInSingleplayer = 0.0;

    /** @return singleplayer range override, or {@code 0} to use the server default */
    public double getRangeOverrideInSingleplayer() { return rangeOverrideInSingleplayer; }

    // --- Persistence ---

    /**
     * Loads the client config from {@code configPath}, falling back to defaults on any error.
     *
     * @param configPath path to the JSON config file
     * @return a valid {@link ClientConfig}
     */
    public static ClientConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
                if (loaded != null) return loaded;
                PickupRangeMod.LOGGER.warn("Client config was empty, using defaults.");
            } catch (JsonSyntaxException e) {
                PickupRangeMod.LOGGER.error("Client config JSON is malformed, using defaults: {}", e.getMessage());
            } catch (IOException e) {
                PickupRangeMod.LOGGER.error("Failed to read client config, using defaults.", e);
            }
        }
        ClientConfig defaults = createDefaults();
        defaults.save(configPath);
        return defaults;
    }

    /**
     * Saves this config to {@code configPath}.
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
            PickupRangeMod.LOGGER.error("Failed to save client config.", e);
        }
    }

    /** @return a new {@link ClientConfig} populated with defaults */
    public static ClientConfig createDefaults() {
        return new ClientConfig();
    }
}
