package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed fields and a bounded channel list; world access stays on the server thread. */
public final class MessageNavigationTable implements IMessage {
    private int window,texture,speed,mask,behavior;private boolean animated,redstone,security;private ChannelList channels;
    public MessageNavigationTable(){}
    public MessageNavigationTable(int window,NavigationScreenSettings settings,int mask,int behavior,ChannelList channels){
        this.window=window;texture=settings.texture;speed=settings.speed;animated=settings.animated;redstone=settings.redstone;security=settings.security;this.mask=mask;this.behavior=behavior;this.channels=channels;
    }
    @Override public void fromBytes(ByteBuf b){
        window=b.readInt();texture=b.readInt();speed=b.readUnsignedByte();animated=b.readBoolean();redstone=b.readBoolean();security=b.readBoolean();mask=b.readUnsignedByte();behavior=b.readUnsignedByte();
        int count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()!=count*4)throw new IllegalArgumentException("Invalid navigation channels");
        int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();channels=ChannelList.of(values);
    }
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(texture);b.writeByte(speed);b.writeBoolean(animated);b.writeBoolean(redstone);b.writeBoolean(security);b.writeByte(mask);b.writeByte(behavior);b.writeByte(channels.size());for(int value:channels.toArray())b.writeInt(value);}
    public static final class Handler implements IMessageHandler<MessageNavigationTable,IMessage>{
        @Override public IMessage onMessage(MessageNavigationTable m,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!(player.openContainer instanceof ContainerTokenSlot)||player.openContainer.windowId!=m.window||m.channels==null||m.speed>2||m.behavior>3||!NavigationScreenSettings.validTexture(m.texture))return;
                if(m.mask!=0&&m.mask!=127&&(m.mask<2||m.mask>64||Integer.bitCount(m.mask)!=1))return;
                ContainerTokenSlot container=(ContainerTokenSlot)player.openContainer;
                if(!container.canInteractWith(player)||!(container.tile instanceof TileEntityNavigationTable))return;
                ((TileEntityNavigationTable)container.tile).configureDisplay(new NavigationScreenSettings(m.texture,m.animated,m.speed,m.redstone,m.security),false,m.mask,m.behavior,m.channels);
            });return null;
        }
    }
}
