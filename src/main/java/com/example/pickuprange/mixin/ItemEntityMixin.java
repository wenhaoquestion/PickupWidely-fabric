package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.event.PickupRangeCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link ItemEntity} to implement extended item pickup range.
 *
 * <p>Injects at the TAIL of {@link ItemEntity#tick()} after vanilla has already processed
 * its own proximity check. For players beyond vanilla range but within the configured range,
 * {@link #playerTouch(Player)} is called directly — identical to what vanilla does when a
 * player walks over an item. No custom animation; behaviour is vanilla-identical.
 *
 * <p><strong>Vanilla range note:</strong> The player tick checks the player's AABB inflated by
 * (1.0, 0.5, 1.0), or the union of the player and vehicle AABBs inflated horizontally while
 * riding. This mixin uses that same intersection test to avoid duplicate handling.
 *
 * <p>VERSION-SENSITIVE (Mojang mappings):
 * <ul>
 *   <li>{@code ItemEntity#tick()} — stable across 1.18–1.21.x</li>
 *   <li>{@code ItemEntity#playerTouch(Player)} — Mojang name; was {@code onPlayerCollision}
 *       in older Yarn mappings.</li>
 *   <li>{@code ItemEntity#pickupDelay} — private field, same name in Mojang 1.20.6</li>
 * </ul>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    /** Controls pickup delay countdown; 0 means the item can be picked up. */
    @Shadow private int pickupDelay;

    /**
     * Triggers vanilla pickup logic for a specific player.
     *
     * <p>Mojang mapping: {@code playerTouch(Player)}.
     * Yarn equivalent (1.20.x): {@code onPlayerCollision(PlayerEntity)}.
     */
    @Shadow public abstract void playerTouch(Player player);

    /**
     * Applies the configured range to vanilla collision pickups as well as this mixin's
     * extended calls. Without this guard, values below vanilla's pickup AABB (for example
     * the configured 0.5 minimum) would have no effect.
     */
    @Inject(at = @At("HEAD"), method = "playerTouch", cancellable = true)
    private void enforceConfiguredPickupRange(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide()) return;

        ServerConfig config = PickupRangeMod.getServerConfig();
        double effectiveRange = config.clamp(
                PlayerRangeManager.getEffectiveItemRange(player));
        if (!Double.isFinite(effectiveRange)
                || self.distanceToSqr(player) > effectiveRange * effectiveRange) {
            ci.cancel();
        }
    }

    /**
     * Extends the item pickup range beyond vanilla's default.
     *
     * <p>Runs after vanilla has already attempted pickup for nearby players.
     * Calls {@link #playerTouch} for any additional players within their configured range.
     * Fires {@link PickupRangeCallback#ITEM_PICKUP} before each pickup so other mods can cancel.
     */
    @Inject(at = @At("TAIL"), method = "tick")
    private void onPickupRangeTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;

        if (self.level().isClientSide()) return;
        if (self.isRemoved()) return; // vanilla already picked it up this tick
        if (pickupDelay != 0) return;

        double maxRange = PickupRangeMod.getServerConfig().getMaxRange();

        // Player lists are much cheaper here than a maxRange-sized entity volume query for
        // every item entity on every tick.
        for (Player player : self.level().players()) {
            if (self.isRemoved()) break;
            if (player.isSpectator() || player.isDeadOrDying()) continue;

            // Vanilla's player tick owns this exact overlap. A fixed-radius approximation
            // incorrectly swallowed valid configured ranges such as 1.5 blocks.
            if (isInsideVanillaPickupBox(player, self)) continue;

            double effectiveRange = Math.min(
                    PlayerRangeManager.getEffectiveItemRange(player), maxRange);
            if (!Double.isFinite(effectiveRange) || effectiveRange <= 0.0) continue;
            double rangeSq = effectiveRange * effectiveRange;
            double distSq = self.distanceToSqr(player);
            if (distSq > rangeSq) continue;

            InteractionResult result =
                    PickupRangeCallback.ITEM_PICKUP.invoker().onPickup(player, self,
                            effectiveRange);
            if (result == InteractionResult.FAIL) continue;

            playerTouch(player);
        }
    }

    /**
     * Mirrors the AABB used by {@code Player#aiStep()} before it invokes
     * {@link ItemEntity#playerTouch(Player)}.
     */
    private static boolean isInsideVanillaPickupBox(Player player, ItemEntity item) {
        AABB playerPickupBox;
        Entity vehicle = player.getVehicle();

        if (player.isPassenger() && vehicle != null && !vehicle.isRemoved()) {
            playerPickupBox = player.getBoundingBox()
                    .minmax(vehicle.getBoundingBox())
                    .inflate(1.0, 0.0, 1.0);
        } else {
            playerPickupBox = player.getBoundingBox().inflate(1.0, 0.5, 1.0);
        }

        return playerPickupBox.intersects(item.getBoundingBox());
    }
}
