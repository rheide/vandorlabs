package com.vandorlabs.network;

import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityConstructor;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageMachineDisplay extends DisplaySettingsMessage {
    private int[] surfaces;private com.vandorlabs.drives.DriveSettings drive;
    public MessageMachineDisplay withDrive(com.vandorlabs.drives.DriveSettings settings){drive=settings;return this;}
    public MessageMachineDisplay(){}
    public MessageMachineDisplay(int window,NavigationScreenSettings settings,int[] surfaces,int mask,int behavior,ChannelList channels){super(window,settings,mask,behavior,channels);this.surfaces=surfaces.clone();}
    @Override public void fromBytes(ByteBuf b){readHeader(b,1);int count=b.readUnsignedByte();if(count>3||b.readableBytes()!=count*4&&b.readableBytes()!=count*4+4)throw new IllegalArgumentException("Invalid machine surfaces");surfaces=new int[count];for(int i=0;i<count;i++)surfaces[i]=b.readInt();if(b.isReadable())drive=com.vandorlabs.drives.DriveSettings.read(b);}
    @Override public void toBytes(ByteBuf b){writeHeader(b);b.writeByte(surfaces.length);for(int value:surfaces)b.writeInt(value);if(drive!=null)drive.write(b);}
    public static final class Handler implements IMessageHandler<MessageMachineDisplay,IMessage>{
        @Override public IMessage onMessage(MessageMachineDisplay m,MessageContext context){m.withContainer(context,c->{if(c.tile instanceof TileEntityConstructor){TileEntityConstructor tile=(TileEntityConstructor)c.tile;if(m.drive!=null)tile.configureDrive(m.drive);tile.configureMachine(m.settings(),m.surfaces,false,m.mask,m.behavior,m.channels);}});return null;}
    }
}
