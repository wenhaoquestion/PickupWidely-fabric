package com.example.pickuprange.command;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.network.ModPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Registers and handles all {@code /pickuprange} sub-commands.
 *
 * <p>Command tree:
 * <pre>
 *   /pickuprange get [player]                 — query range
 *   /pickuprange set &lt;range&gt;                  — set own item range
 *   /pickuprange set &lt;player&gt; &lt;range&gt;         — set another player's item range (op)
 *   /pickuprange setxp &lt;range&gt;               — set own XP range
 *   /pickuprange setxp &lt;player&gt; &lt;range&gt;      — set another player's XP range (op)
 *   /pickuprange reset [player]               — reset to server default
 *   /pickuprange reload                       — hot-reload config (op)
 * </pre>
 *
 * <p>All messages sent to players use {@link Component#translatable(String, Object...)} so
 * the client resolves them against its own {@code en_us.json}.
 */
public final class PickupRangeCommand {

    private PickupRangeCommand() {}

    /**
     * Registers the {@code /pickuprange} command tree with the given dispatcher.
     *
     * @param dispatcher Brigadier command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pickuprange")

                // /pickuprange get [player]
                .then(Commands.literal("get")
                    .executes(ctx -> executeGet(ctx.getSource(), null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> executeGet(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))))

                // /pickuprange set <range>
                .then(Commands.literal("set")
                    .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0))
                        .executes(ctx -> executeSetSelf(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "range"))))
                    // /pickuprange set <player> <range>  (op only)
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0))
                            .executes(ctx -> executeSetOther(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"),
                                    DoubleArgumentType.getDouble(ctx, "range"))))))

                // /pickuprange setxp <range>
                .then(Commands.literal("setxp")
                    .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0))
                        .executes(ctx -> executeSetXpSelf(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "range"))))
                    // /pickuprange setxp <player> <range>  (op only)
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("range", DoubleArgumentType.doubleArg(0.0))
                            .executes(ctx -> executeSetXpOther(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"),
                                    DoubleArgumentType.getDouble(ctx, "range"))))))

                // /pickuprange reset [player]
                .then(Commands.literal("reset")
                    .executes(ctx -> executeReset(ctx.getSource(), null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> executeReset(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))))

                // /pickuprange reload  (op level 2)
                .then(Commands.literal("reload")
                    .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                    .executes(ctx -> executeReload(ctx.getSource())))
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private static int executeGet(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {

        if (target == null) {
            ServerPlayer self = source.getPlayerOrException();
            double item = PlayerRangeManager.getEffectiveItemRange(self);
            double xp   = PlayerRangeManager.getEffectiveXpRange(self);
            source.sendSuccess(() ->
                Component.translatable("pickuprange.command.get.self",
                    format1(item), format1(xp)), false);
        } else {
            double item = PlayerRangeManager.getEffectiveItemRange(target);
            double xp   = PlayerRangeManager.getEffectiveXpRange(target);
            String name = target.getName().getString();
            source.sendSuccess(() ->
                Component.translatable("pickuprange.command.get.other",
                    name, format1(item), format1(xp)), false);
        }
        return 1;
    }

    private static int executeSetSelf(CommandSourceStack source, double range)
            throws CommandSyntaxException {

        ServerConfig config = PickupRangeMod.getServerConfig();

        if (!config.isAllowPlayerOverride()) {
            source.sendFailure(Component.translatable("pickuprange.command.error.override"));
            return 0;
        }
        if (config.isRequirePermission() && !hasAdminPermission(source)) {
            source.sendFailure(Component.translatable("pickuprange.command.error.permission"));
            return 0;
        }

        double clamped = config.clamp(range);
        ServerPlayer self = source.getPlayerOrException();
        PlayerRangeManager.setItemRange(self.getUUID(), clamped);
        pushRangeToClient(self);

        double displayValue = clamped;
        source.sendSuccess(() ->
            Component.translatable("pickuprange.command.set.self", format1(displayValue)), true);
        return 1;
    }

    private static int executeSetOther(CommandSourceStack source, ServerPlayer target, double range) {
        double clamped = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setItemRange(target.getUUID(), clamped);
        pushRangeToClient(target);

        String name = target.getName().getString();
        source.sendSuccess(() ->
            Component.translatable("pickuprange.command.set.other",
                name, format1(clamped)), true);
        return 1;
    }

    private static int executeSetXpSelf(CommandSourceStack source, double range)
            throws CommandSyntaxException {

        ServerConfig config = PickupRangeMod.getServerConfig();

        if (!config.isAllowPlayerOverride()) {
            source.sendFailure(Component.translatable("pickuprange.command.error.override"));
            return 0;
        }
        if (config.isRequirePermission() && !hasAdminPermission(source)) {
            source.sendFailure(Component.translatable("pickuprange.command.error.permission"));
            return 0;
        }

        double clamped = config.clamp(range);
        ServerPlayer self = source.getPlayerOrException();
        PlayerRangeManager.setXpRange(self.getUUID(), clamped);
        pushRangeToClient(self);

        source.sendSuccess(() ->
            Component.translatable("pickuprange.command.set.xp.self", format1(clamped)), true);
        return 1;
    }

    private static int executeSetXpOther(CommandSourceStack source, ServerPlayer target, double range) {
        double clamped = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setXpRange(target.getUUID(), clamped);
        pushRangeToClient(target);

        String name = target.getName().getString();
        source.sendSuccess(() ->
            Component.translatable("pickuprange.command.set.xp.other",
                name, format1(clamped)), true);
        return 1;
    }

    private static int executeReset(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {

        ServerConfig config = PickupRangeMod.getServerConfig();

        if (target == null) {
            if (!config.isAllowPlayerOverride()) {
                source.sendFailure(Component.translatable("pickuprange.command.error.override"));
                return 0;
            }
            if (config.isRequirePermission() && !hasAdminPermission(source)) {
                source.sendFailure(Component.translatable("pickuprange.command.error.permission"));
                return 0;
            }

            ServerPlayer self = source.getPlayerOrException();
            PlayerRangeManager.resetRange(self.getUUID());
            pushRangeToClient(self);

            double di = config.getDefaultItemRange();
            double dx = config.getDefaultXpRange();
            source.sendSuccess(() ->
                Component.translatable("pickuprange.command.reset.self",
                    format1(di), format1(dx)), true);
        } else {
            PlayerRangeManager.resetRange(target.getUUID());
            pushRangeToClient(target);
            String name = target.getName().getString();
            source.sendSuccess(() ->
                Component.translatable("pickuprange.command.reset.other", name), true);
        }
        return 1;
    }

    private static int executeReload(CommandSourceStack source) {
        Path configPath = FabricLoader.getInstance()
                .getConfigDir().resolve("pickup-range-server.json");

        ServerConfig newConfig = ServerConfig.load(configPath);
        PickupRangeMod.setServerConfig(newConfig);
        ModPackets.broadcastConfigReload(source.getServer(), newConfig);

        source.sendSuccess(() -> Component.translatable("pickuprange.command.reload"), true);
        PickupRangeMod.LOGGER.info("Config reloaded by {}.", source.getTextName());
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Pushes the current effective range to the player's client if they have the mod. */
    private static void pushRangeToClient(ServerPlayer player) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                .canSend(player, ModPackets.SYNC_PLAYER_RANGE_TYPE)) {
            ModPackets.sendSyncPlayerRange(player,
                    PlayerRangeManager.getEffectiveItemRange(player),
                    PlayerRangeManager.getEffectiveXpRange(player));
        }
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_ADMINS);
    }

    /**
     * Formats a double to one decimal place as a {@link String} for use in
     * {@link Component#translatable(String, Object...)} arguments.
     *
     * @param value the value to format
     * @return formatted string, e.g. {@code "5.0"}
     */
    private static String format1(double value) {
        return String.format("%.1f", value);
    }
}
