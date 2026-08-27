package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.data.PlayerRangeManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbMixin {
    @Shadow private PlayerEntity target;

    @Redirect(
            method = "expensiveUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/PlayerEntity;"))
    private PlayerEntity pickuprange$selectPlayer(World world, Entity orb, double ignoredRange) {
        if (world.isClient) {
            return world.getClosestPlayer(orb, ignoredRange);
        }

        PlayerEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double range = PickupRangeMod.getServerConfig().clamp(
                    PlayerRangeManager.getEffectiveXpRange(player));
            if (!Double.isFinite(range) || range <= 0.0) {
                continue;
            }
            // Vanilla selects and retains targets by entity-position distance. Keep that
            // geometry here; the eye-height midpoint is used only by vanilla's force vector.
            double distance = orb.squaredDistanceTo(player);
            if (distance <= range * range && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @ModifyConstant(method = {"tick", "expensiveUpdate"}, constant = @Constant(doubleValue = 8.0))
    private double pickuprange$attractionRadius(double vanillaRadius) {
        ExperienceOrbEntity orb = (ExperienceOrbEntity) (Object) this;
        if (orb.world.isClient || target == null) {
            return vanillaRadius;
        }
        return PickupRangeMod.getServerConfig().clamp(
                PlayerRangeManager.getEffectiveXpRange(target));
    }

    @ModifyConstant(method = {"tick", "expensiveUpdate"}, constant = @Constant(doubleValue = 64.0))
    private double pickuprange$attractionRadiusSquared(double vanillaRadiusSquared) {
        ExperienceOrbEntity orb = (ExperienceOrbEntity) (Object) this;
        if (orb.world.isClient || target == null) {
            return vanillaRadiusSquared;
        }
        double range = PickupRangeMod.getServerConfig().clamp(
                PlayerRangeManager.getEffectiveXpRange(target));
        // This threshold is paired with the dynamic denominator above. Vanilla therefore
        // evaluates (1 - distance / range)^2 only while the unsquared strength is positive.
        return range * range;
    }
}
