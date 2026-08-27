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
 * {@code ExperienceOrb#followNearbyPlayer()} in 1.21.11.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    /** Vanilla's cached target; kept coherent for save/debug/mod interoperability. */
    @Shadow private Player followingPlayer;

    /**
     * Selects the nearest player whose own effective range contains this orb, then applies
     * vanilla's attraction curve using that range as the radius.
     */
    @Inject(at = @At("HEAD"), method = "followNearbyPlayer", cancellable = true)
    private void onFollowNearbyPlayer(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;

        // Keep the client path untouched; it provides vanilla prediction/render motion.
        if (self.level().isClientSide()) return;

        ServerConfig config = PickupRangeMod.getServerConfig();
        Player nearest = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        double nearestEffectiveRange = 0.0;

        // getNearestPlayer cannot express a different maximum distance per player. The
        // server player list is normally small and avoids a large entity-volume query.
        for (Player player : self.level().players()) {
            if (player.isSpectator() || player.isDeadOrDying()) continue;

            double rawRange = PlayerRangeManager.getEffectiveXpRange(player);
            if (!Double.isFinite(rawRange)) continue;
            double effectiveRange = config.clamp(rawRange);

            Vec3 delta = player.position()
                    .add(0.0, player.getBbHeight() * 0.5, 0.0)
                    .subtract(self.position());
            double distanceSq = delta.lengthSqr();
            if (distanceSq > effectiveRange * effectiveRange) continue;

            if (distanceSq < nearestDistanceSq) {
                nearest = player;
                nearestDistanceSq = distanceSq;
                nearestEffectiveRange = effectiveRange;
            }
        }

        this.followingPlayer = nearest;

        if (nearest != null) {
            // This is vanilla's force formula with 8.0 replaced by the effective range.
            Vec3 delta = nearest.position()
                    .add(0.0, nearest.getBbHeight() * 0.5, 0.0)
                    .subtract(self.position());
            double strength = 1.0 - Math.sqrt(delta.lengthSqr()) / nearestEffectiveRange;

            self.setDeltaMovement(self.getDeltaMovement().add(
                    delta.normalize().scale(strength * strength * 0.1)));
        }

        ci.cancel();
    }
}
