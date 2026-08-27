package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link ExperienceOrb} to extend the XP orb attraction range.
 *
 * <p>In 1.20.6 vanilla refreshes the followed player from the private
 * {@code scanForEntities()} method and applies the attraction curve in {@code tick()}.
 * This mixin redirects target selection and replaces only the two range constants, leaving
 * movement, gravity, merging, ageing, and client prediction in vanilla code.
 *
 * <p>Vanilla still handles the final absorption when the orb reaches the player.
 *
 * <p>VERSION-SENSITIVE: targets the Mojang-mapped {@code scanForEntities()} and
 * {@code tick()} implementation in 1.20.6.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    /** Vanilla's cached target; kept coherent for save/debug/mod interoperability. */
    @Shadow private Player followingPlayer;

    /** Selects the nearest player whose own effective XP range contains this orb. */
    @Redirect(
            method = "scanForEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"
            )
    )
    private Player pickupRange$findNearestEligiblePlayer(Level level, Entity orb,
                                                          double vanillaRange) {
        // Keep the client path byte-for-byte equivalent to vanilla target selection.
        if (level.isClientSide()) {
            return level.getNearestPlayer(orb, vanillaRange);
        }

        ServerConfig config = PickupRangeMod.getServerConfig();
        Player nearest = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;

        for (Player player : level.players()) {
            if (player.isSpectator() || player.isDeadOrDying()) continue;

            double rawRange = PlayerRangeManager.getEffectiveXpRange(player);
            if (!Double.isFinite(rawRange)) continue;
            double effectiveRange = config.clamp(rawRange);

            // Match vanilla target selection and retention: entity-to-entity distance.
            double distanceSq = player.distanceToSqr(orb);
            if (distanceSq > effectiveRange * effectiveRange) continue;

            if (distanceSq < nearestDistanceSq) {
                nearest = player;
                nearestDistanceSq = distanceSq;
            }
        }

        return nearest;
    }

    /** Replaces the 8² target-retention check in the periodic target scan. */
    @ModifyConstant(method = "scanForEntities", constant = @Constant(doubleValue = 64.0))
    private double pickupRange$scanDistanceSquared(double vanillaDistanceSquared) {
        double range = pickupRange$currentFollowingRange();
        return range * range;
    }

    /** Replaces the 8² attraction eligibility check in the vanilla movement tick. */
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 64.0))
    private double pickupRange$attractionDistanceSquared(double vanillaDistanceSquared) {
        double range = pickupRange$currentFollowingRange();
        return range * range;
    }

    /** Replaces the divisor in vanilla's {@code 1 - distance / 8} force curve. */
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 8.0))
    private double pickupRange$attractionRange(double vanillaRange) {
        return pickupRange$currentFollowingRange();
    }

    /** Returns the clamped range belonging to vanilla's currently followed player. */
    private double pickupRange$currentFollowingRange() {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (self.level().isClientSide() || followingPlayer == null) {
            return 8.0;
        }

        ServerConfig config = PickupRangeMod.getServerConfig();
        double rawRange = PlayerRangeManager.getEffectiveXpRange(followingPlayer);
        if (!Double.isFinite(rawRange)) {
            rawRange = config.getDefaultXpRange();
        }
        return config.clamp(rawRange);
    }
}
