package com.example.pickuprange.network;

import net.minecraft.network.PacketByteBuf;

public final class SyncPlayerRangePayload {
    private final double itemRange;
    private final double xpRange;

    public SyncPlayerRangePayload(double itemRange, double xpRange) {
        this.itemRange = itemRange;
        this.xpRange = xpRange;
    }

    public double itemRange() { return itemRange; }
    public double xpRange() { return xpRange; }

    public void write(PacketByteBuf buffer) {
        buffer.writeDouble(itemRange);
        buffer.writeDouble(xpRange);
    }

    public static SyncPlayerRangePayload read(PacketByteBuf buffer) {
        return new SyncPlayerRangePayload(buffer.readDouble(), buffer.readDouble());
    }
}
