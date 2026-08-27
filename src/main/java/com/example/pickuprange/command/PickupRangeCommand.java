package com.example.pickuprange.command;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.network.ModPackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;

import java.nio.file.Path;
import java.util.Locale;

public final class PickupRangeCommand {
    private PickupRangeCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pickuprange")
                .then(CommandManager.literal("get")
                        .executes(context -> executeGet(context.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> executeGet(context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("range", DoubleArgumentType.doubleArg(0.0))
                                .executes(context -> executeSetSelf(context.getSource(),
                                        DoubleArgumentType.getDouble(context, "range"))))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("range", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> executeSetOther(context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "range"))))))
                .then(CommandManager.literal("setxp")
                        .then(CommandManager.argument("range", DoubleArgumentType.doubleArg(0.0))
                                .executes(context -> executeSetXpSelf(context.getSource(),
                                        DoubleArgumentType.getDouble(context, "range"))))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.argument("range", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> executeSetXpOther(context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "range"))))))
                .then(CommandManager.literal("reset")
                        .executes(context -> executeReset(context.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> executeReset(context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> executeReload(context.getSource()))));
    }

    private static int executeGet(ServerCommandSource source, ServerPlayerEntity target)
            throws CommandSyntaxException {
        ServerPlayerEntity player = target != null ? target : source.getPlayer();
        double item = PlayerRangeManager.getEffectiveItemRange(player);
        double xp = PlayerRangeManager.getEffectiveXpRange(player);

        if (target == null) {
            source.sendFeedback(new TranslatableText("pickuprange.command.get.self",
                    format(item), format(xp)), false);
        } else {
            source.sendFeedback(new TranslatableText("pickuprange.command.get.other",
                    target.getName().getString(), format(item), format(xp)), false);
        }
        return 1;
    }

    private static int executeSetSelf(ServerCommandSource source, double range)
            throws CommandSyntaxException {
        if (!canOverride(source)) {
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        double value = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setItemRange(player.getUuid(), value);
        pushRangeToClient(player);
        source.sendFeedback(new TranslatableText("pickuprange.command.set.self",
                format(value)), true);
        return 1;
    }

    private static int executeSetOther(ServerCommandSource source,
                                       ServerPlayerEntity target, double range) {
        double value = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setItemRange(target.getUuid(), value);
        pushRangeToClient(target);
        source.sendFeedback(new TranslatableText("pickuprange.command.set.other",
                target.getName().getString(), format(value)), true);
        return 1;
    }

    private static int executeSetXpSelf(ServerCommandSource source, double range)
            throws CommandSyntaxException {
        if (!canOverride(source)) {
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        double value = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setXpRange(player.getUuid(), value);
        pushRangeToClient(player);
        source.sendFeedback(new TranslatableText("pickuprange.command.set.xp.self",
                format(value)), true);
        return 1;
    }

    private static int executeSetXpOther(ServerCommandSource source,
                                         ServerPlayerEntity target, double range) {
        double value = PickupRangeMod.getServerConfig().clamp(range);
        PlayerRangeManager.setXpRange(target.getUuid(), value);
        pushRangeToClient(target);
        source.sendFeedback(new TranslatableText("pickuprange.command.set.xp.other",
                target.getName().getString(), format(value)), true);
        return 1;
    }

    private static int executeReset(ServerCommandSource source, ServerPlayerEntity target)
            throws CommandSyntaxException {
        if (target == null && !canOverride(source)) {
            return 0;
        }

        ServerPlayerEntity player = target != null ? target : source.getPlayer();
        PlayerRangeManager.resetRange(player.getUuid());
        pushRangeToClient(player);

        if (target == null) {
            source.sendFeedback(new TranslatableText("pickuprange.command.reset.self",
                    format(PlayerRangeManager.getEffectiveItemRange(player)),
                    format(PlayerRangeManager.getEffectiveXpRange(player))), true);
        } else {
            source.sendFeedback(new TranslatableText("pickuprange.command.reset.other",
                    target.getName().getString()), true);
        }
        return 1;
    }

    private static int executeReload(ServerCommandSource source) {
        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve("pickup-range-server.json");
        ServerConfig config = ServerConfig.load(path);
        PickupRangeMod.setServerConfig(config);
        ModPackets.broadcastConfigReload(source.getMinecraftServer(), config);
        source.sendFeedback(new TranslatableText("pickuprange.command.reload"), true);
        return 1;
    }

    private static boolean canOverride(ServerCommandSource source) {
        ServerConfig config = PickupRangeMod.getServerConfig();
        if (!config.isAllowPlayerOverride()) {
            source.sendError(new TranslatableText("pickuprange.command.error.override"));
            return false;
        }
        if (config.isRequirePermission() && !source.hasPermissionLevel(2)) {
            source.sendError(new TranslatableText("pickuprange.command.error.permission"));
            return false;
        }
        return true;
    }

    private static void pushRangeToClient(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, ModPackets.SYNC_PLAYER_RANGE)) {
            ModPackets.sendSyncPlayerRange(player,
                    PlayerRangeManager.getEffectiveItemRange(player),
                    PlayerRangeManager.getEffectiveXpRange(player));
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
