package com.example.pickuprange.mixin;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link ExperienceOrb} to extend the XP orb attraction range.
 *
 * <p>Vanilla uses {@link net.minecraft.world.level.Level#getNearestPlayer(net.minecraft.world.entity.Entity, double)}
 * with a radius of 8.0 blocks to find a player to follow. If the configured XP range exceeds
 * vanilla's 8.0 blocks, this mixin injects at the TAIL of {@link ExperienceOrb#tick()} to
 * add supplemental attraction velocity toward the nearest player in the expanded range.
 *
 * <p>Vanilla still handles the final absorption when the orb reaches the player.
 *
 * <p>VERSION-SENSITIVE: Mojang mapping name {@code ExperienceOrb#tick()} is stable across
 * 1.18–1.21.x. If vanilla changes the attraction radius constant from 8.0, update
 * {@link #VANILLA_ATTRACTION_RANGE} accordingly.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    /** Vanilla XP orb attraction radius. Update if vanilla changes this. */
    private static final double VANILLA_ATTRACTION_RANGE = 8.0;

    /**
     * Extends XP orb attraction range beyond vanilla's 8-block limit when the server
     * config requests a larger range.
     *
     * <p>Only runs on the server side; only when configured range exceeds vanilla's.
     * When the orb is already within vanilla range of any player, vanilla handles it.
     */
    @Inject(at = @At("TAIL"), method = "tick")
    private void onExtendedXpRange(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;

        if (self.level().isClientSide()) return;
        if (self.isRemoved()) return;

        ServerConfig config = PickupRangeMod.getServerConfig();
        double configuredRange = config.getDefaultXpRange();

        // No-op if our range doesn't exceed vanilla's.
        if (configuredRange <= VANILLA_ATTRACTION_RANGE) return;

        // Find nearest player within our extended range.
        Player nearest = self.level().getNearestPlayer(self, configuredRange);
        if (nearest == null) return;

        double dist = self.distanceTo(nearest);

        // If within vanilla range, vanilla already handles this orb.
        if (dist <= VANILLA_ATTRACTION_RANGE) return;

        // Apply per-player XP range if available, otherwise use server default.
        double effectiveRange = PlayerRangeManager.getEffectiveXpRange(nearest);
        if (dist > effectiveRange) return;

        // Attract toward player. Speed scales from slow at max range to fast when close.
        Vec3 orbPos    = self.position();
        Vec3 playerPos = nearest.position().add(0, nearest.getBbHeight() * 0.5, 0);
        Vec3 dir       = playerPos.subtract(orbPos).normalize();

        double t     = 1.0 - (dist / effectiveRange);
        double speed = 0.03 + t * 0.12;

        self.setDeltaMovement(self.getDeltaMovement().add(dir.scale(speed)));
        self.hurtMarked = true;
    }
}
