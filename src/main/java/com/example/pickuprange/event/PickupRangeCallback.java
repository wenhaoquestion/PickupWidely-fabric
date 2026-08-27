package com.example.pickuprange.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

@FunctionalInterface
public interface PickupRangeCallback {
    ActionResult onPickup(PlayerEntity player, ItemEntity item, double range);

    Event<PickupRangeCallback> ITEM_PICKUP = EventFactory.createArrayBacked(
            PickupRangeCallback.class,
            listeners -> (player, item, range) -> {
                for (PickupRangeCallback listener : listeners) {
                    if (listener.onPickup(player, item, range) == ActionResult.FAIL) {
                        return ActionResult.FAIL;
                    }
                }
                return ActionResult.PASS;
            });
}
