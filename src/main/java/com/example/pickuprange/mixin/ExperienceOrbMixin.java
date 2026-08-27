package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.18.2 XP attraction implementation.
 *
 * <p>Vanilla keeps target selection in {@code scanForEntities()} and applies its
 * fixed eight-block force inline in {@code tick()}. The mixin preserves vanilla's
 * 20-tick cached-target rhythm and orb merging, but replaces target eligibility and
 * attraction strength with the target player's configured range.</p>
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Shadow private Player followingPlayer;

    @Unique private Player pickuprange$targetBeforeScan;
    @Unique private Player pickuprange$suspendedTarget;

    @Inject(method = "scanForEntities", at = @At("HEAD"))
    private void rememberConfiguredTarget(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (!self.getLevel().isClientSide) {
            this.pickuprange$targetBeforeScan = this.followingPlayer;
        }
    }

    @Inject(method = "scanForEntities", at = @At("TAIL"))
    private void selectConfiguredTarget(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (self.getLevel().isClientSide) return;

        Player cached = this.pickuprange$targetBeforeScan;
        this.pickuprange$targetBeforeScan = null;
        if (cached != null && pickuprange$isInsideSelectionRange(cached, self)) {
            this.followingPlayer = cached;
            return;
        }

        Player nearest = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (Player player : self.getLevel().players()) {
            if (player.isSpectator() || player.isDeadOrDying()) continue;

            double range = pickuprange$effectiveRange(player);
            if (!Double.isFinite(range) || range <= 0.0) continue;

            double distanceSq = player.distanceToSqr(self);
            if (distanceSq > range * range || distanceSq >= nearestDistanceSq) continue;

            nearest = player;
            nearestDistanceSq = distanceSq;
        }
        this.followingPlayer = nearest;
    }

    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;followingPlayer:Lnet/minecraft/world/entity/player/Player;",
            opcode = Opcodes.GETFIELD, ordinal = 0))
    private void suspendVanillaAttraction(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (self.getLevel().isClientSide) return;

        Player target = this.followingPlayer;
        if (target != null && (target.isSpectator() || target.isDeadOrDying())) {
            target = null;
        }
        this.pickuprange$suspendedTarget = target;
        this.followingPlayer = null;
    }

    /**
     * Restore the cached target and update velocity immediately before vanilla reads
     * the {@link Vec3} passed to {@code move}. Injecting at {@code move} itself is too
     * late: the JVM has already evaluated {@code getDeltaMovement()} onto the operand
     * stack by then, so that tick would move with the pre-attraction velocity.
     *
     * <p>In 1.18.2 this is the third {@code getDeltaMovement()} call in {@code tick}:
     * gravity, vanilla attraction, then the argument to {@code move}.</p>
     */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;",
            ordinal = 2))
    private void restoreTargetAndApplyConfiguredAttraction(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (self.getLevel().isClientSide) return;

        Player target = this.pickuprange$suspendedTarget;
        this.pickuprange$suspendedTarget = null;
        this.followingPlayer = target;
        if (target == null || self.isRemoved()) return;

        double range = pickuprange$effectiveRange(target);
        if (!Double.isFinite(range) || range <= 0.0) return;

        Vec3 delta = new Vec3(
                target.getX() - self.getX(),
                target.getY() + target.getEyeHeight() / 2.0 - self.getY(),
                target.getZ() - self.getZ());
        double forceDistanceSq = delta.lengthSqr();
        if (forceDistanceSq >= range * range) return;

        double strength = Math.max(0.0, 1.0 - Math.sqrt(forceDistanceSq) / range);
        if (strength <= 0.0) return;
        self.setDeltaMovement(self.getDeltaMovement().add(
                delta.normalize().scale(strength * strength * 0.1)));
    }

    @Unique
    private static boolean pickuprange$isInsideSelectionRange(Player player, ExperienceOrb orb) {
        double range = pickuprange$effectiveRange(player);
        return Double.isFinite(range) && range > 0.0
                && player.distanceToSqr(orb) <= range * range;
    }

    @Unique
    private static double pickuprange$effectiveRange(Player player) {
        ServerConfig config = PickupRangeMod.getServerConfig();
        double rawRange = PlayerRangeManager.getEffectiveXpRange(player);
        return Double.isFinite(rawRange) ? config.clamp(rawRange) : Double.NaN;
    }
}
