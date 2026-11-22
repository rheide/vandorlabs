package com.vandorlabs.neoforge.reservoir.client;

import com.vandorlabs.neoforge.client.NativeInlineTransmissionWidget;
import com.vandorlabs.neoforge.reservoir.BufferTransmissionPayload;
import com.vandorlabs.neoforge.reservoir.NativeBufferEntity;
import net.minecraft.client.gui.Font;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NativeBufferTransmitWidget {
    private NativeBufferTransmitWidget() {}

    public static NativeInlineTransmissionWidget create(NativeBufferEntity owner, int window, Font font,
                                                        int x, int y, int width) {
        var output = owner.fullness();
        return new NativeInlineTransmissionWidget(font, x, y, width, output.transmittedChannels(),
            output.transmissionRange(), output.localRedstone(), (channels, range, local) ->
                PacketDistributor.sendToServer(new BufferTransmissionPayload(window, channels, range, local)));
    }
}
