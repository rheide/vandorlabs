package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleTransferChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Captures visible server-authoritative poses from a real native client. */
@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeVehicleArmPlayerChecks {
    private static final BlockPos ARM = new BlockPos(0, 1, -2);
    private static int captured;

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!NativeVehicleTransferChecks.armGallery()) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.player.isSpectator()
            || client.screen != null || client.getOverlay() != null) return;
        client.options.hideGui = true;
        var entities = client.level.getEntitiesOfClass(NativeGroundVehicle.class,
            new AABB(NativeVehicleTransferChecks.ARM_GALLERY_DONE).inflate(8));
        if (!entities.isEmpty()) {
            var vehicle = entities.getFirst();
            if (!vehicle.admitted() || vehicle.visual() == null) return;
            int pose = vehicle.armPose(ARM);
            if (pose < 0) return;
            int phase = pose & 65535;
            if (captured == 0 && phase == 65535 || captured == 1 && phase > 20000 && phase < 45000
                || captured == 2 && phase == 0) {
                var names = new String[]{"arm-deployed.png", "arm-moving.png", "arm-folded.png"};
                var file = client.gameDirectory.toPath().toAbsolutePath().getParent().resolve(names[captured]);
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    image.writeToFile(file);
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Cannot capture native carried Arm", failure);
                }
                captured++;
            }
        }
        if (client.level.getBlockState(NativeVehicleTransferChecks.ARM_GALLERY_DONE).is(Blocks.GOLD_BLOCK)) {
            if (captured != 3) throw new AssertionError("Missing a carried Arm appearance pose, captured " + captured);
            System.out.println("[vandorlabs][vehicle-arm-player] client PASS deployed/moving/folded");
            client.stop();
        }
    }

    private NativeVehicleArmPlayerChecks() {}
}
