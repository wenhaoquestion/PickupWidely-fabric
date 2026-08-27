package com.example.pickuprange.network;

import net.minecraft.network.PacketByteBuf;

public final class SyncConfigPayload {
    private final double defaultItemRange;
    private final double defaultXpRange;
    private final double maxRange;
    private final double minRange;
    private final boolean allowPlayerOverride;

    public SyncConfigPayload(double defaultItemRange, double defaultXpRange,
                             double maxRange, double minRange,
                             boolean allowPlayerOverride) {
        this.defaultItemRange = defaultItemRange;
        this.defaultXpRange = defaultXpRange;
        this.maxRange = maxRange;
        this.minRange = minRange;
        this.allowPlayerOverride = allowPlayerOverride;
    }

    public double defaultItemRange() { return defaultItemRange; }
    public double defaultXpRange() { return defaultXpRange; }
    public double maxRange() { return maxRange; }
    public double minRange() { return minRange; }
    public boolean allowPlayerOverride() { return allowPlayerOverride; }

    public void write(PacketByteBuf buffer) {
        buffer.writeDouble(defaultItemRange);
        buffer.writeDouble(defaultXpRange);
        buffer.writeDouble(maxRange);
        buffer.writeDouble(minRange);
        buffer.writeBoolean(allowPlayerOverride);
    }

    public static SyncConfigPayload read(PacketByteBuf buffer) {
        return new SyncConfigPayload(
                buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(),
                buffer.readBoolean());
    }
}
