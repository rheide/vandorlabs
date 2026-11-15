package com.vandorlabs.ports;

import com.vandorlabs.redstone.ChannelData;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size settings plus a bounded channel list; no arbitrary NBT or resource fields. */
public final class ResourcePortMessage implements IMessage {
    private int window,mode;
    private ResourcePortSettings settings;
    private ChannelList channels;
    private boolean valid;
    public ResourcePortMessage(){}
    public ResourcePortMessage(int window,ResourcePortSettings settings,int mode,ChannelList channels){this.window=window;this.settings=settings;this.mode=mode;this.channels=channels;valid=true;}
    @Override public void toBytes(ByteBuf buffer){
        buffer.writeInt(window);int faces=0;for(int i=0;i<6;i++)faces|=settings.face(i).ordinal()<<(i*2);buffer.writeShort(faces);buffer.writeLong(settings.throughput);buffer.writeBoolean(settings.pullInputs);buffer.writeByte(settings.onTexture.ordinal());buffer.writeByte(mode);ChannelData.write(buffer,channels);buffer.writeInt(settings.housingChoice);
    }
    @Override public void fromBytes(ByteBuf buffer){
        valid=false;if(buffer.readableBytes()<18||buffer.readableBytes()>22+4*ChannelList.MAX_CHANNELS)return;
        try{
            window=buffer.readInt();int packed=buffer.readUnsignedShort();if((packed&0xf000)!=0)return;
            ResourcePortSettings.Face[] faces=new ResourcePortSettings.Face[6];for(int i=0;i<6;i++){int face=(packed>>(i*2))&3;if(face>=ResourcePortSettings.Face.values().length)return;faces[i]=ResourcePortSettings.Face.values()[face];}
            long rate=buffer.readLong();int pull=buffer.readUnsignedByte(),art=buffer.readUnsignedByte();mode=buffer.readUnsignedByte();
            if(rate<0||pull>1||art>=ResourcePortSettings.OnTexture.values().length||mode>2)return;
            channels=ChannelData.readField(buffer,0);if(channels==null||buffer.readableBytes()!=0&&buffer.readableBytes()!=4)return;int housing=buffer.isReadable()?buffer.readInt():-1;
            settings=new ResourcePortSettings(faces,rate,pull!=0,ResourcePortSettings.OnTexture.values()[art],housing);valid=true;
        }catch(RuntimeException malformed){valid=false;}
    }
    public static final class Handler implements IMessageHandler<ResourcePortMessage,IMessage>{
        @Override public IMessage onMessage(ResourcePortMessage message,MessageContext context){
            if(!message.valid)return null;EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(player.openContainer.windowId!=message.window||!(player.openContainer instanceof ResourcePortContainer))return;
                try{((ResourcePortContainer)player.openContainer).update(player,message.settings,message.mode,message.channels);}catch(IllegalArgumentException refused){player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(refused.getMessage()),true);}
            });return null;
        }
    }
}
