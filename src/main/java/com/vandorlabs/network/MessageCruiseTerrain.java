package com.vandorlabs.network;

import com.vandorlabs.VandorLabs;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** One aligned geographic tile. Fine and distant tiles can arrive independently. */
public final class MessageCruiseTerrain implements IMessage {
    public static final int SIZE=64;
    public int entity, x, z, step;
    public int[] pixels;
    public MessageCruiseTerrain() {}
    public MessageCruiseTerrain(int entity,int x,int z,int step,int[] pixels){
        this.entity=entity;this.x=x;this.z=z;this.step=step;this.pixels=pixels;
    }
    public void toBytes(ByteBuf out){
        out.writeInt(entity);out.writeInt(x);out.writeInt(z);out.writeInt(step);
        for(int color:pixels)out.writeMedium(color);
    }
    public void fromBytes(ByteBuf in){
        if(in.readableBytes()!=16+SIZE*SIZE*3)throw new IllegalArgumentException("Invalid cruise tile size");
        entity=in.readInt();x=in.readInt();z=in.readInt();step=in.readInt();
        if(step<4 || step>4096 || Integer.bitCount(step)!=1)throw new IllegalArgumentException("Invalid cruise tile scale");
        pixels=new int[SIZE*SIZE];
        for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|in.readUnsignedMedium();
    }
    public static final class Handler implements IMessageHandler<MessageCruiseTerrain,IMessage>{
        public IMessage onMessage(MessageCruiseTerrain image,MessageContext context){
            VandorLabs.proxy.cruiseTerrain(image);return null;
        }
    }
}
