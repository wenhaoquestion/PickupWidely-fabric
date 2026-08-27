package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload that pushes the active server configuration to the client.
 *
 * <p>Sent on player join (if the client has the mod) and after {@code /pickuprange reload}.
 *
 * <p>VERSION-SENSITIVE: {@link StreamCodec} and {@link ByteBufCodecs} were introduced in 1.20.5.
 *
 * @param defaultItemRange    server default item pickup range
 * @param defaultXpRange      server default XP orb pickup range
 * @param maxRange            hard cap on player-settable range
 * @param minRange            hard floor on player-settable range
 * @param allowPlayerOverride whether players may change their own range via the GUI or commands
 */
public record SyncConfigPayload(
        double defaultItemRange,
        double defaultXpRange,
        double maxRange,
        double minRange,
        boolean allowPlayerOverride
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(PickupRangeMod.MOD_ID, "sync_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, SyncConfigPayload::defaultItemRange,
                    ByteBufCodecs.DOUBLE, SyncConfigPayload::defaultXpRange,
                    ByteBufCodecs.DOUBLE, SyncConfigPayload::maxRange,
                    ByteBufCodecs.DOUBLE, SyncConfigPayload::minRange,
                    ByteBufCodecs.BOOL,   SyncConfigPayload::allowPlayerOverride,
                    SyncConfigPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
