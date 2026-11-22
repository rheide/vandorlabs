package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "vandorlabs", value = Dist.CLIENT)
public final class NativeVehicleClient {
    @SubscribeEvent public static void sendControls(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof NativeGroundVehicle craft) || !craft.admitted()) return;
        var bindings = mc.options;
        int keys = 0;
        if (mc.screen == null) {
            if (bindings.keyUp.isDown()) keys |= NativeVehicleInput.FORWARD;
            if (bindings.keyDown.isDown()) keys |= NativeVehicleInput.BACK;
            if (bindings.keyLeft.isDown()) keys |= NativeVehicleInput.LEFT;
            if (bindings.keyRight.isDown()) keys |= NativeVehicleInput.RIGHT;
            if (bindings.keyJump.isDown()) keys |= NativeVehicleInput.BRAKE;
            if (bindings.keyShift.isDown()) keys |= NativeVehicleInput.DOWN;
            if (bindings.keySprint.isDown()) keys |= NativeVehicleInput.SPRINT;
        }
        PacketDistributor.sendToServer(new NativeVehicleInput(craft.getId(), keys));
    }

    @SubscribeEvent public static void useMountedControl(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof NativeGroundVehicle craft) || !craft.admitted()
                || !craft.pointsAtMountedControl(player)) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new NativeVehicleInput(craft.getId(), NativeVehicleInput.USE));
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) NativeGroundVehicleRenderer.clear();
    }

    private NativeVehicleClient() {}
}
