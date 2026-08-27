package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link ExperienceOrb} to extend the XP orb attraction range.
 *
 * <p>Vanilla's private follow routine uses one fixed 8-block radius. This mixin replaces
 * that routine on the logical server so each player can be evaluated against their own
 * effective XP range. The default value of 8.0 follows vanilla's selection and force curve.
 *
 * <p>Vanilla still handles the final absorption when the orb reaches the player.
 *
 * <p>VERSION-SENSITIVE: targets the Mojang-mapped private method
 * {@code ExperienceOrb#followNearbyPlayer()} in 1.21.10.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    /** Vanilla's cached target; kept coherent for save/debug/mod interoperability. */
    @Shadow private Player followingPlayer;

    /**
     * Retains vanilla's cached target while that player's effective range still contains
     * this orb. When a new target is needed, selects the nearest eligible player using
     * vanilla's entity-to-entity distance, then applies the vanilla attraction curve.
     */
    @Inject(at = @At("HEAD"), method = "followNearbyPlayer", cancellable = true)
    private void onFollowNearbyPlayer(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;

        // Keep the client path untouched; it provides vanilla prediction/render motion.
        if (self.level().isClientSide()) return;

        ServerConfig config = PickupRangeMod.getServerConfig();
        Player target = this.followingPlayer;
        double targetEffectiveRange = 0.0;

        if (target != null) {
            targetEffectiveRange = config.clamp(
                    PlayerRangeManager.getEffectiveXpRange(target));
            if (!isEligibleTarget(target, self, targetEffectiveRange)) {
                target = null;
            }
        }

        if (target == null) {
            double nearestDistanceSq = Double.POSITIVE_INFINITY;

            // getNearestPlayer cannot express a different maximum distance per player. The
            // server player list is normally small and avoids a large entity-volume query.
            for (Player player : self.level().players()) {
                double rawRange = PlayerRangeManager.getEffectiveXpRange(player);
                if (!Double.isFinite(rawRange)) continue;
                double effectiveRange = config.clamp(rawRange);
                if (!isEligibleTarget(player, self, effectiveRange)) continue;

                double distanceSq = player.distanceToSqr(self);
                if (distanceSq < nearestDistanceSq) {
                    target = player;
                    nearestDistanceSq = distanceSq;
                    targetEffectiveRange = effectiveRange;
                }
            }
        }

        this.followingPlayer = target;

        if (target != null) {
            // Exact vanilla force point: half the player's eye height above their base Y.
            Vec3 delta = new Vec3(
                    target.getX() - self.getX(),
                    target.getY() + target.getEyeHeight() * 0.5 - self.getY(),
                    target.getZ() - self.getZ());
            double strength = Math.max(0.0,
                    1.0 - Math.sqrt(delta.lengthSqr()) / targetEffectiveRange);

            self.setDeltaMovement(self.getDeltaMovement().add(
                    delta.normalize().scale(strength * strength * 0.1)));
        }

        ci.cancel();
    }

    /** Returns whether vanilla may keep or newly select this player as the target. */
    private static boolean isEligibleTarget(Player player, ExperienceOrb orb,
                                            double effectiveRange) {
        if (player.isRemoved() || player.isSpectator() || player.isDeadOrDying()) {
            return false;
        }
        if (!Double.isFinite(effectiveRange) || effectiveRange <= 0.0) {
            return false;
        }
        return player.distanceToSqr(orb) <= effectiveRange * effectiveRange;
    }
}
