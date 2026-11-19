package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.ramp.NativeRampRiders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Short same-tick rider steps, using the shared native support correction. */
final class NativeTurntableRiders {
    static final int MAX_RIDERS = 256;

    record Step(Entity actor, Vec3 delta, float yaw) {}
    private record Saved(Entity actor, Vec3 position, AABB box, Vec3 velocity,
                         float yaw, float fall, boolean grounded) {}
    private record Delivery(ServerPlayer player, NativeTurntableRiderPayload packet) {}

    static boolean eligible(Entity actor) {
        return NativeRampRiders.eligible(actor) && !actor.isVehicle()
                && (actor instanceof Player || actor instanceof Pig pig && !pig.isLeashed()
                || actor instanceof Cow cow && !cow.isLeashed());
    }

    static boolean sameSaveOwner(Step step) {
        return step.actor() instanceof ServerPlayer || step.actor().chunkPosition().equals(
                new ChunkPos(BlockPos.containing(step.actor().position().add(step.delta()))));
    }

    static void apply(List<Step> steps) {
        if (steps.isEmpty()) return;
        var before = new ArrayList<Saved>();
        var deliveries = new ArrayList<Delivery>();
        try {
            for (var step : steps) {
                var actor = step.actor();
                before.add(new Saved(actor, actor.position(), actor.getBoundingBox(), actor.getDeltaMovement(),
                        actor.getYRot(), actor.fallDistance, actor.onGround()));
                var delta = step.delta();
                NativeRampRiders.move(actor, delta.x, delta.y, delta.z, (player, packet) ->
                        deliveries.add(new Delivery(player, new NativeTurntableRiderPayload(packet, step.yaw()))));
                actor.setYRot(actor.getYRot() + step.yaw());
            }
        } catch (RuntimeException failure) {
            for (var saved : before) {
                var actor = saved.actor();
                actor.setPos(saved.position());
                actor.setBoundingBox(saved.box());
                actor.setDeltaMovement(saved.velocity());
                actor.setYRot(saved.yaw());
                actor.fallDistance = saved.fall();
                actor.setOnGround(saved.grounded());
                NativeRampRiders.supported(actor);
            }
            throw failure;
        }
        for (var delivery : deliveries) {
            var player = delivery.player();
            if (player.connection == null) continue;
            try {
                PacketDistributor.sendToPlayer(player, delivery.packet());
            } catch (RuntimeException failure) {
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
        }
    }

    private NativeTurntableRiders() {}
}
