package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageTokenSlot implements IMessage {
    private int window,mode,mask;private boolean any;private String channels;private int height=-1,tilt,direction;
    public MessageTokenSlot(){}
    public MessageTokenSlot(int window,int mode,boolean any,int mask,String channels){this.window=window;this.mode=mode;this.any=any;this.mask=mask;this.channels=channels;}
    public MessageTokenSlot withMount(boolean enabled,int height,int tilt,int direction){if(enabled){this.height=height;this.tilt=tilt;this.direction=direction;}return this;}
    public int mountHeight(){return height;}
    public int mountTilt(){return tilt;}
    public int mountDirection(){return direction;}
    @Override public void fromBytes(ByteBuf b){window=b.readInt();mode=b.readUnsignedByte();any=b.readBoolean();mask=b.readUnsignedByte();int bytes=ByteBufUtils.readVarInt(b,2);if(bytes<0||bytes>1024||bytes>b.readableBytes())throw new IllegalArgumentException("Mount channel text too long");channels=b.toString(b.readerIndex(),bytes,java.nio.charset.StandardCharsets.UTF_8);b.skipBytes(bytes);if(channels.length()>256)throw new IllegalArgumentException("Mount channel text too long");height=-1;if(b.readableBytes()!=0){if(b.readableBytes()!=3)throw new IllegalArgumentException("Invalid mount pose extension");height=b.readUnsignedByte();tilt=b.readUnsignedByte();direction=b.readUnsignedByte();if(!com.vandorlabs.blocks.SignalControlMount.valid(height,tilt,direction))throw new IllegalArgumentException("Invalid mount pose");}}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(mode);b.writeBoolean(any);b.writeByte(mask);ByteBufUtils.writeUTF8String(b,channels);if(height>=0){b.writeByte(height);b.writeByte(tilt);b.writeByte(direction);}}
    public static final class Handler implements IMessageHandler<MessageTokenSlot,IMessage>{
        public IMessage onMessage(MessageTokenSlot message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(player.openContainer.windowId!=message.window||!(player.openContainer instanceof ContainerTokenSlot)||message.mode>3||message.mask!=0&&message.mask!=127&&(message.mask<2||message.mask>64||Integer.bitCount(message.mask)!=1)||message.channels.length()>256)return;
            ContainerTokenSlot container=(ContainerTokenSlot)player.openContainer;ChannelList channels=ChannelList.parse(message.channels);if(!container.canInteractWith(player)||channels==null)return;
            if(message.height>=0&&(!(container.tile.getBlockType() instanceof com.vandorlabs.blocks.BlockTokenSlot)||!com.vandorlabs.blocks.SignalControlMount.valid(message.height,message.tilt,message.direction)))return;
            if(container.tile instanceof com.vandorlabs.tiles.TileEntityNavigationTable){
                com.vandorlabs.tiles.TileEntityNavigationTable tile=(com.vandorlabs.tiles.TileEntityNavigationTable)container.tile;
                tile.configureDisplay(tile.screenSettings(),message.any,message.mask,message.mode,channels);
            }else{container.tile.configure(message.any,message.mask,message.mode);if(message.height>=0)container.tile.configureMount(message.height,message.tilt,message.direction);container.tile.setRedstoneChannels(com.vandorlabs.redstone.TransmitterSlots.channels(container.tile));}
        });return null;}
    }
}
