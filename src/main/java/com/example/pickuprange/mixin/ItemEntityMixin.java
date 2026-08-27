package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.event.PickupRangeCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into {@link ItemEntity} to implement extended item pickup range.
 *
 * <p>Injects at the TAIL of {@link ItemEntity#tick()} after vanilla has already processed
 * its own proximity check. For players beyond vanilla range but within the configured range,
 * {@link #playerTouch(Player)} is called directly — identical to what vanilla does when a
 * player walks over an item. No custom animation; behaviour is vanilla-identical.
 *
 * <p><strong>Vanilla range note:</strong> Vanilla inflates the pickup AABB by (1.0, 0.0, 1.0),
 * giving roughly a 1.25-block horizontal reach. Players within {@link #VANILLA_RANGE_SQ} are
 * skipped here because vanilla's own tick has already handled them.
 *
 * <p>VERSION-SENSITIVE (Mojang mappings):
 * <ul>
 *   <li>{@code ItemEntity#tick()} — stable across 1.18–1.21.x</li>
 *   <li>{@code ItemEntity#playerTouch(Player)} — Mojang name; was {@code onPlayerCollision}
 *       in older Yarn mappings.</li>
 *   <li>{@code ItemEntity#pickupDelay} — private field, same name in Mojang 1.21.x</li>
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
        AABB searchBox = self.getBoundingBox().inflate(maxRange);
        List<Player> nearbyPlayers = self.level().getEntitiesOfClass(Player.class, searchBox);

        for (Player player : nearbyPlayers) {
            if (self.isRemoved()) break;
            if (player.isSpectator()) continue;

            double distSq = self.distanceToSqr(player);

            // Skip players already within vanilla range — they were handled above.
            if (distSq <= VANILLA_RANGE_SQ) continue;

            double rangeSq = PlayerRangeManager.getEffectiveItemRange(player);
            rangeSq *= rangeSq;
            if (distSq > rangeSq) continue;

            InteractionResult result =
                    PickupRangeCallback.ITEM_PICKUP.invoker().onPickup(player, self,
                            Math.sqrt(rangeSq));
            if (result == InteractionResult.FAIL) continue;

            playerTouch(player);
        }
    }

    /**
     * Squared distance below which vanilla's own pickup logic is sufficient.
     * Vanilla inflates by (1.0, 0.0, 1.0); 1.75 gives a safe margin above that.
     */
    private static final double VANILLA_RANGE_SQ = 1.75 * 1.75;
}
