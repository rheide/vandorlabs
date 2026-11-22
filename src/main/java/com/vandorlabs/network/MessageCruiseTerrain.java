package com.vandorlabs.network;

import com.vandorlabs.VandorLabs;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size seed raster sent only to riders during high-altitude flight. */
public final class MessageCruiseTerrain implements IMessage {
    public static final int SIZE = 64;
    public int entity, centerX, centerZ, step;
    public final int[] pixels = new int[SIZE*SIZE];
    public MessageCruiseTerrain() {}
    public MessageCruiseTerrain(int entity, int centerX, int centerZ, int step) {
        this.entity=entity; this.centerX=centerX; this.centerZ=centerZ; this.step=step;
    }
    public void toBytes(ByteBuf out) {
        out.writeInt(entity); out.writeInt(centerX); out.writeInt(centerZ); out.writeInt(step);
        for (int color : pixels) out.writeMedium(color);
    }
    public void fromBytes(ByteBuf in) {
        if (in.readableBytes()!=16+SIZE*SIZE*3) throw new IllegalArgumentException("Invalid cruise map size");
        entity=in.readInt(); centerX=in.readInt(); centerZ=in.readInt(); step=in.readInt();
        if (step<16 || step>2048) throw new IllegalArgumentException("Invalid cruise map scale");
        for (int i=0;i<pixels.length;i++) pixels[i]=0xff000000|in.readUnsignedMedium();
    }
    public static final class Handler implements IMessageHandler<MessageCruiseTerrain,IMessage> {
        public IMessage onMessage(MessageCruiseTerrain image,MessageContext context) {
            VandorLabs.proxy.cruiseTerrain(image);
            return null;
        }
    }
}
