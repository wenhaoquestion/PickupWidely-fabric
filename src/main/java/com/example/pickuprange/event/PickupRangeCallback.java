package com.example.pickuprange.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric event fired just before Pickup Range causes a player to pick up an item
 * via the extended range (i.e. when the player is <em>beyond</em> vanilla pickup distance).
 *
 * <p>Other mods can listen to this event to cancel or conditionally allow pickups:
 * <pre>{@code
 * PickupRangeCallback.ITEM_PICKUP.register((player, item, range) -> {
 *     if (someCondition(player)) return InteractionResult.FAIL;  // cancel
 *     return InteractionResult.PASS;                             // allow
 * });
 * }</pre>
 *
 * <p>Returning {@link InteractionResult#FAIL} cancels the pickup for that player/item pair.
 * Returning {@link InteractionResult#PASS} (or {@link InteractionResult#SUCCESS}) allows it.
 *
 * <p><strong>Note:</strong> This event is only fired for extended-range pickups.
 * Vanilla pickups (within ~1.5 blocks) are not intercepted.
 */
@FunctionalInterface
public interface PickupRangeCallback {

    /**
     * Called before an item is picked up via the extended range.
     *
     * @param player the player attempting to pick up the item
     * @param item   the item entity being picked up
     * @param range  the effective pickup range for this player (in blocks)
     * @return {@link InteractionResult#FAIL} to cancel, anything else to allow
     */
    InteractionResult onPickup(Player player, ItemEntity item, double range);

    // -------------------------------------------------------------------------
    // Event instances
    // -------------------------------------------------------------------------

    /**
     * Fired before an item entity is picked up via extended range.
     *
     * <p>Backed by an array-backed event — all listeners are invoked in registration order.
     * The first {@link InteractionResult#FAIL} result short-circuits further listeners.
     */
    Event<PickupRangeCallback> ITEM_PICKUP = EventFactory.createArrayBacked(
            PickupRangeCallback.class,
            listeners -> (player, item, range) -> {
                for (PickupRangeCallback listener : listeners) {
                    InteractionResult result = listener.onPickup(player, item, range);
                    if (result == InteractionResult.FAIL) return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            }
    );
}
