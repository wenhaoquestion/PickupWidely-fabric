package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload carrying the <em>effective</em> pickup ranges for the
 * receiving player (combining server defaults with any per-player override).
 *
 * <p>Sent on join and after a {@code /pickuprange set} command.
 *
 * @param itemRange effective item pickup range for this player
 * @param xpRange   effective XP orb pickup range for this player
 */
public record SyncPlayerRangePayload(
        double itemRange,
        double xpRange
) implements CustomPacketPayload {

    /** Unique identifier for this payload type. */
    public static final CustomPacketPayload.Type<SyncPlayerRangePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(PickupRangeMod.MOD_ID, "sync_player_range"));

    /** Codec used to encode/decode this payload over the network. */
    public static final StreamCodec<FriendlyByteBuf, SyncPlayerRangePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, SyncPlayerRangePayload::itemRange,
                    ByteBufCodecs.DOUBLE, SyncPlayerRangePayload::xpRange,
                    SyncPlayerRangePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
