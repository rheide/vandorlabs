package com.vandorlabs.segmented;

import com.vandorlabs.redstone.*;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed settings followed by the bounded shared channel-list encoding. */
public final class SegmentedDoorMessage implements IMessage {
    private int window,operation,ticks,texture=-1;private boolean local,opens;private ChannelList channels;private SegmentedDoorSettings settings;
    public SegmentedDoorMessage(){}
    public SegmentedDoorMessage(int window,int operation,int ticks,boolean local,boolean opens,ChannelList channels){this.window=window;this.operation=operation;this.ticks=ticks;this.local=local;this.opens=opens;this.channels=channels;}
    public SegmentedDoorMessage texture(int choice){this.texture=choice;this.operation=3;return this;}
    public SegmentedDoorMessage settings(int choice,SegmentedDoorSettings value){texture=choice;settings=value;operation=4;return this;}
    public SegmentedDoorMessage partSettings(int choice,SegmentedDoorSettings value){settings(choice,value);operation=5;return this;}
    public SegmentedDoorMessage cellTexture(int choice,boolean inherit){texture=choice;operation=inherit?7:6;return this;}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(operation);b.writeByte(ticks);b.writeByte((local?1:0)|(opens?2:0));ChannelData.write(b,channels);if((operation==3||operation==6))b.writeInt(texture);else if(operation==4||operation==5){b.writeInt(texture);settings.encode(b);}}
    @Override public void fromBytes(ByteBuf b){if(b.readableBytes()<8)throw new IllegalArgumentException("Truncated segmented settings");window=b.readInt();operation=b.readUnsignedByte();ticks=b.readUnsignedByte();int flags=b.readUnsignedByte();if(operation>7||ticks<5||ticks>30||flags>3)throw new IllegalArgumentException("Invalid segmented settings");local=(flags&1)!=0;opens=(flags&2)!=0;channels=ChannelData.readField(b,0);if(operation>=3&&operation<=6){if(operation==5&&b.readableBytes()!=4+SegmentedDoorSettings.PACKET_BYTES)throw new IllegalArgumentException("Truncated local Segmented Door settings");if((operation==4||operation==5)?(b.readableBytes()!=4+SegmentedDoorSettings.PACKET_BYTES&&b.readableBytes()!=3+SegmentedDoorSettings.PACKET_BYTES):b.readableBytes()!=4)throw new IllegalArgumentException("Invalid segmented texture field");texture=b.readInt();if(!SegmentedDoorTile.validTexture(texture))throw new IllegalArgumentException("Invalid segmented texture");if(operation==4||operation==5)settings=SegmentedDoorSettings.decode(b);}if(channels==null||b.isReadable())throw new IllegalArgumentException("Invalid segmented channels");}
    public static final class Handler implements IMessageHandler<SegmentedDoorMessage,IMessage>{
        @Override public IMessage onMessage(SegmentedDoorMessage m,MessageContext context){net.minecraft.entity.player.EntityPlayerMP actor=context.getServerHandler().player;actor.getServerWorld().addScheduledTask(()->{
            if(!(actor.openContainer instanceof SegmentedDoorContainer)||actor.openContainer.windowId!=m.window)return;SegmentedDoorContainer container=(SegmentedDoorContainer)actor.openContainer;if(!container.canInteractWith(actor))return;
            try{if(m.operation==6||m.operation==7)container.tile.configureCellTexture(actor,m.texture,m.operation==7);else if(m.operation==5)container.tile.configurePart(actor,m.channels,m.ticks,m.local,m.opens,m.texture,m.settings);else if(m.operation==4)container.tile.configureAll(actor,m.channels,m.ticks,m.local,m.opens,m.texture,m.settings);else if(m.operation==0)container.tile.configure(actor,m.channels,m.ticks,m.local,m.opens);else if(m.operation==3)container.tile.configureTexture(actor,m.texture);else container.tile.operate(actor,m.operation==1);}catch(IllegalArgumentException denied){actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}
        });return null;}
    }
}
