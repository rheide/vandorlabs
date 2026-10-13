package com.vandorlabs.network;

import com.vandorlabs.tiles.TileEntityCanopy;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Acknowledges the exact live edit, including authoritative values after rejection. */
public final class MessageCanopySettingsResult implements IMessage {
    public BlockPos pos;public int depth,scale,shade,tint;public String requested;public boolean applied;
    private int requestedDepth,requestedScale,requestedShade,requestedTint;
    public MessageCanopySettingsResult(){}
    public MessageCanopySettingsResult(TileEntityCanopy tile,int d,int s,int c,int t,boolean applied){pos=tile.getPos();requestedDepth=d;requestedScale=s;requestedShade=c;requestedTint=t;depth=tile.large.depth;scale=tile.large.scale;shade=tile.large.shade;tint=tile.large.tint;this.applied=applied;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(requestedDepth);b.writeByte(requestedScale);b.writeByte(requestedShade);b.writeByte(requestedTint);b.writeByte(depth);b.writeByte(scale);b.writeByte(shade);b.writeByte(tint);b.writeBoolean(applied);}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());requestedDepth=b.readUnsignedByte();requestedScale=b.readUnsignedByte();requestedShade=b.readUnsignedByte();requestedTint=b.readUnsignedByte();requested=requestedDepth+":"+requestedScale+":"+requestedShade+":"+requestedTint;depth=b.readUnsignedByte();scale=b.readUnsignedByte();shade=b.readUnsignedByte();tint=b.readUnsignedByte();applied=b.readBoolean();}
    public static final class Handler implements IMessageHandler<MessageCanopySettingsResult,IMessage>{
        public IMessage onMessage(MessageCanopySettingsResult message,MessageContext context){net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();mc.addScheduledTask(()->{if(mc.currentScreen instanceof com.vandorlabs.client.GuiLargeCanopy)((com.vandorlabs.client.GuiLargeCanopy)mc.currentScreen).acknowledge(message);});return null;}
    }
}
