package com.vandorlabs.network;

import com.vandorlabs.VandorLabs;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(VandorLabs.MODID);

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(HandlerSyncScreenSelector.class, MessageSyncScreenSelector.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageRampController.Handler.class, MessageRampController.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessagePlatformMotion.Handler.class, MessagePlatformMotion.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(MessageRedstoneChannel.Handler.class, MessageRedstoneChannel.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageSpaceDoor.Handler.class, MessageSpaceDoor.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableGlass.Handler.class, MessageProgrammableGlass.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableWallShade.Handler.class, MessageProgrammableWallShade.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableSlabSides.Handler.class, MessageProgrammableSlabSides.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableDiagonalWidth.Handler.class, MessageProgrammableDiagonalWidth.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableLight.Handler.class, MessageProgrammableLight.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableChair.Handler.class, MessageProgrammableChair.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableTrigger.Handler.class,
                MessageProgrammableTrigger.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageDuplifierOptions.Handler.class,
                MessageDuplifierOptions.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageDiagonalGeometry.Handler.class, MessageDiagonalGeometry.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageFaceTextures.Handler.class, MessageFaceTextures.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageConnectedSeat.Handler.class, MessageConnectedSeat.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageLandingGear.Handler.class, MessageLandingGear.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableTrapdoor.Handler.class, MessageProgrammableTrapdoor.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageSurfaceTexture.Handler.class,MessageSurfaceTexture.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageSideTexture.Handler.class,MessageSideTexture.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageRedstoneScreen.Handler.class,MessageRedstoneScreen.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammableArmor.Handler.class, MessageProgrammableArmor.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageSampleArmorTexture.Handler.class, MessageSampleArmorTexture.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessagePilotModes.Handler.class,MessagePilotModes.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageAutopilot.Handler.class,MessageAutopilot.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageLargeCanopy.Handler.class,MessageLargeCanopy.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageCanopyUse.Handler.class,MessageCanopyUse.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageCanopySettingsResult.Handler.class,MessageCanopySettingsResult.class,id++,Side.CLIENT);
        INSTANCE.registerMessage(MessageCockpit.Handler.class,MessageCockpit.class,id++,Side.SERVER);
        INSTANCE.registerMessage(com.vandorlabs.vehicle.VehicleNetwork.Server.class, com.vandorlabs.vehicle.VehicleNetwork.Packet.class, id, Side.SERVER);
        INSTANCE.registerMessage(com.vandorlabs.vehicle.VehicleNetwork.Client.class, com.vandorlabs.vehicle.VehicleNetwork.Packet.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(MessageTurntableSettings.Handler.class,MessageTurntableSettings.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageTurntableSnapshot.Server.class,MessageTurntableSnapshot.class,id,Side.SERVER);
        INSTANCE.registerMessage(MessageTurntableSnapshot.Client.class,MessageTurntableSnapshot.class,id++,Side.CLIENT);
        INSTANCE.registerMessage(MessageModularGear.Handler.class,MessageModularGear.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageModularGearUse.Handler.class,MessageModularGearUse.class,id++,Side.SERVER);
        INSTANCE.registerMessage(MessageTurntableCarry.Handler.class,MessageTurntableCarry.class,id++,Side.CLIENT);
        INSTANCE.registerMessage(MessageTankShape.Handler.class,MessageTankShape.class,id++,Side.SERVER);
    }
}
