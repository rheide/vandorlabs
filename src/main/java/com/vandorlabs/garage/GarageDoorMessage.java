package com.vandorlabs.garage;

import com.vandorlabs.redstone.*;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed settings followed by the bounded shared channel-list encoding. */
public final class GarageDoorMessage implements IMessage {
    private int window,operation,ticks,texture=-1;private boolean local,opens;private ChannelList channels;
    public GarageDoorMessage(){}
    public GarageDoorMessage(int window,int operation,int ticks,boolean local,boolean opens,ChannelList channels){this.window=window;this.operation=operation;this.ticks=ticks;this.local=local;this.opens=opens;this.channels=channels;}
    public GarageDoorMessage texture(int choice){this.texture=choice;this.operation=3;return this;}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(operation);b.writeByte(ticks);b.writeByte((local?1:0)|(opens?2:0));ChannelData.write(b,channels);if(operation==3)b.writeInt(texture);}
    @Override public void fromBytes(ByteBuf b){if(b.readableBytes()<8)throw new IllegalArgumentException("Truncated garage settings");window=b.readInt();operation=b.readUnsignedByte();ticks=b.readUnsignedByte();int flags=b.readUnsignedByte();if(operation>3||ticks<5||ticks>30||flags>3)throw new IllegalArgumentException("Invalid garage settings");local=(flags&1)!=0;opens=(flags&2)!=0;channels=ChannelData.readField(b,0);if(operation==3){if(b.readableBytes()!=4)throw new IllegalArgumentException("Invalid sectional texture field");texture=b.readInt();if(!GarageDoorTile.validTexture(texture))throw new IllegalArgumentException("Invalid sectional texture");}if(channels==null||b.isReadable())throw new IllegalArgumentException("Invalid garage channels");}
    public static final class Handler implements IMessageHandler<GarageDoorMessage,IMessage>{
        @Override public IMessage onMessage(GarageDoorMessage m,MessageContext context){net.minecraft.entity.player.EntityPlayerMP actor=context.getServerHandler().player;actor.getServerWorld().addScheduledTask(()->{
            if(!(actor.openContainer instanceof GarageDoorContainer)||actor.openContainer.windowId!=m.window)return;GarageDoorContainer container=(GarageDoorContainer)actor.openContainer;if(!container.canInteractWith(actor))return;
            try{if(m.operation==0)container.tile.configure(actor,m.channels,m.ticks,m.local,m.opens);else if(m.operation==3)container.tile.configureTexture(actor,m.texture);else container.tile.operate(actor,m.operation==1);}catch(IllegalArgumentException denied){actor.sendStatusMessage(new net.minecraft.util.text.TextComponentString(denied.getMessage()),true);}
        });return null;}
    }
}
