package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.event.PickupRangeCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow private int pickupDelay;
    @Shadow public abstract void onPlayerCollision(PlayerEntity player);

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void pickuprange$enforceConfiguredRange(PlayerEntity player, CallbackInfo info) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (item.world.isClient) {
            return;
        }

        double range = PickupRangeMod.getServerConfig().clamp(
                PlayerRangeManager.getEffectiveItemRange(player));
        if (!Double.isFinite(range) || item.squaredDistanceTo(player) > range * range) {
            info.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void pickuprange$extendPickupRange(CallbackInfo info) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (item.world.isClient || !item.isAlive() || pickupDelay != 0) {
            return;
        }

        double maxRange = PickupRangeMod.getServerConfig().getMaxRange();
        for (PlayerEntity player : item.world.getPlayers()) {
            if (!item.isAlive()) {
                break;
            }
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (insideVanillaPickupBox(player, item)) {
                continue;
            }

            double range = Math.min(
                    PlayerRangeManager.getEffectiveItemRange(player), maxRange);
            if (!Double.isFinite(range) || range <= 0.0
                    || item.squaredDistanceTo(player) > range * range) {
                continue;
            }

            ActionResult result = PickupRangeCallback.ITEM_PICKUP.invoker()
                    .onPickup(player, item, range);
            if (result != ActionResult.FAIL) {
                onPlayerCollision(player);
            }
        }
    }

    private static boolean insideVanillaPickupBox(PlayerEntity player, ItemEntity item) {
        Box pickupBox;
        Entity vehicle = player.getVehicle();
        if (player.hasVehicle() && vehicle != null && vehicle.isAlive()) {
            pickupBox = player.getBoundingBox().union(vehicle.getBoundingBox())
                    .expand(1.0, 0.0, 1.0);
        } else {
            pickupBox = player.getBoundingBox().expand(1.0, 0.5, 1.0);
        }
        return pickupBox.intersects(item.getBoundingBox());
    }
}
