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
    }
}
