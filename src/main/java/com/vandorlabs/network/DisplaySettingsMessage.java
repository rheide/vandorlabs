package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Shared bounded codec and server-thread authorization for secured display dialogs. */
abstract class DisplaySettingsMessage implements IMessage {
    protected int window,texture,speed,mask,behavior;protected boolean animated,redstone,security;protected ChannelList channels;
    protected DisplaySettingsMessage(){}
    protected DisplaySettingsMessage(int window,NavigationScreenSettings settings,int mask,int behavior,ChannelList channels){this.window=window;texture=settings.texture;speed=settings.speed;animated=settings.animated;redstone=settings.redstone;security=settings.security;this.mask=mask;this.behavior=behavior;this.channels=channels;}
    protected final void readHeader(ByteBuf b,int suffix){
        window=b.readInt();texture=b.readInt();speed=b.readUnsignedByte();animated=b.readBoolean();redstone=b.readBoolean();security=b.readBoolean();mask=b.readUnsignedByte();behavior=b.readUnsignedByte();
        int count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()<count*4+suffix)throw new IllegalArgumentException("Invalid display channels");
        int[] values=new int[count];for(int i=0;i<count;i++)values[i]=b.readInt();channels=ChannelList.of(values);
    }
    protected final void writeHeader(ByteBuf b){b.writeInt(window);b.writeInt(texture);b.writeByte(speed);b.writeBoolean(animated);b.writeBoolean(redstone);b.writeBoolean(security);b.writeByte(mask);b.writeByte(behavior);b.writeByte(channels.size());for(int value:channels.toArray())b.writeInt(value);}
    protected final NavigationScreenSettings settings(){return new NavigationScreenSettings(texture,animated,speed,redstone,security);}
    protected final void withContainer(MessageContext context,java.util.function.Consumer<ContainerTokenSlot> action){
        net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(()->{
            if(!(player.openContainer instanceof ContainerTokenSlot)||player.openContainer.windowId!=window||channels==null||speed>2||behavior>3||!NavigationScreenSettings.validTexture(texture))return;
            if(mask!=0&&mask!=127&&(mask<2||mask>64||Integer.bitCount(mask)!=1))return;
            ContainerTokenSlot container=(ContainerTokenSlot)player.openContainer;if(container.canInteractWith(player))action.accept(container);
        });
    }
}
