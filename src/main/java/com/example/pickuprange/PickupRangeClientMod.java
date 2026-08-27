package com.example.pickuprange;

import com.example.pickuprange.client.KeyBindings;
import com.example.pickuprange.config.ClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Client-only mod initializer for Pickup Range.
 *
 * <p>Must never be referenced from server-only code paths. All client classes are
 * isolated in the {@code client} sub-package and annotated with
 * {@link Environment}({@link EnvType#CLIENT}) where appropriate.
 */
@Environment(EnvType.CLIENT)
public class PickupRangeClientMod implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("PickupRange/Client");

    private static ClientConfig clientConfig;

    // Effective ranges for the local player (updated by server sync packets).
    private static double clientItemRange     = 1.5;
    private static double clientXpRange       = 8.0;

    // Server-enforced bounds — used to clamp the GUI sliders.
    private static double clientMinRange      = 0.5;
    private static double clientMaxRange      = 64.0;
    private static boolean clientAllowOverride = true;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Pickup Range client initializing…");

        Path configPath = FabricLoader.getInstance()
                .getConfigDir().resolve("pickup-range-client.json");
        clientConfig = ClientConfig.load(configPath);

        KeyBindings.register();

        // Isolated in ModPacketsClient to keep client-only imports off the server.
        com.example.pickuprange.network.ModPacketsClient.registerClientReceivers();

        LOGGER.info("Pickup Range client initialized.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return active client config (never null) */
    public static ClientConfig getClientConfig() {
        return clientConfig != null ? clientConfig : ClientConfig.createDefaults();
    }

    /** @return effective item pickup range for the local player */
    public static double getClientItemRange()      { return clientItemRange; }

    /** @return effective XP orb range for the local player */
    public static double getClientXpRange()        { return clientXpRange; }

    /** @return server-enforced minimum range (used to clamp GUI sliders) */
    public static double getClientMinRange()       { return clientMinRange; }

    /** @return server-enforced maximum range (used to clamp GUI sliders) */
    public static double getClientMaxRange()       { return clientMaxRange; }

    /** @return whether the server allows players to change their own range */
    public static boolean isClientAllowOverride()  { return clientAllowOverride; }

    // -------------------------------------------------------------------------
    // Called by network layer
    // -------------------------------------------------------------------------

    /**
     * Updates all server-provided config values (called on join and after reload).
     *
     * @param itemRange     effective item range for this player
     * @param xpRange       effective XP range for this player
     * @param maxRange      server hard cap
     * @param minRange      server hard floor
     * @param allowOverride whether the player may change their own range
     */
    public static void onServerConfigReceived(double itemRange, double xpRange,
                                               double maxRange, double minRange,
                                               boolean allowOverride) {
        clientItemRange     = itemRange;
        clientXpRange       = xpRange;
        clientMaxRange      = maxRange;
        clientMinRange      = minRange;
        clientAllowOverride = allowOverride;
        LOGGER.debug("Server config received — item: {}, xp: {}, min: {}, max: {}, override: {}",
                itemRange, xpRange, minRange, maxRange, allowOverride);
    }

    /**
     * Updates only the effective ranges (called when per-player range is set by an op).
     *
     * @param itemRange new effective item range
     * @param xpRange   new effective XP range
     */
    public static void onPlayerRangeReceived(double itemRange, double xpRange) {
        clientItemRange = itemRange;
        clientXpRange   = xpRange;
    }
}
