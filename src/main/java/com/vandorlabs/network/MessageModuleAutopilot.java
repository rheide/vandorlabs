package com.vandorlabs.network;

import com.vandorlabs.container.ContainerModuleRack;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.rack.AutopilotModuleSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.WorldActionAuthorization;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Bounded typed module settings, authenticated against its exact installed slot and open Rack. */
public final class MessageModuleAutopilot implements IMessage {
    private int window,slot;
    private long sequence;
    private BlockPos pos;
    private AutopilotModuleSettings settings;
    public MessageModuleAutopilot() { }
    public MessageModuleAutopilot(int window,BlockPos pos,int slot,long sequence,AutopilotModuleSettings settings) {
        this.window=window;this.pos=pos;this.slot=slot;this.sequence=sequence;this.settings=settings;
    }
    @Override public void toBytes(ByteBuf out) {
        out.writeInt(window);out.writeLong(pos.toLong());out.writeByte(slot);out.writeLong(sequence);
        out.writeByte(settings.channels.size());for(int i=0;i<settings.channels.size();i++)out.writeInt(settings.channels.get(i));
        out.writeByte(settings.activation);byte[] marker=settings.marker.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(marker.length);out.writeBytes(marker);out.writeByte(settings.markerMetadata);
    }
    @Override public void fromBytes(ByteBuf in) {
        if(in.readableBytes()<25||in.readableBytes()>409)throw new IllegalArgumentException("Malformed module configuration length");
        window=in.readInt();pos=BlockPos.fromLong(in.readLong());slot=in.readUnsignedByte();sequence=in.readLong();
        int count=in.readUnsignedByte();
        if(slot>=4||sequence<0||count>ChannelList.MAX_CHANNELS||in.readableBytes()<count*4+3)throw new IllegalArgumentException("Malformed module slot/channels");
        int[] channels=new int[count];for(int i=0;i<count;i++)channels[i]=in.readInt();
        int activation=in.readUnsignedByte(),length=in.readUnsignedByte();
        if(length<1||length>128||in.readableBytes()!=length+1)throw new IllegalArgumentException("Malformed module marker length");
        byte[] marker=new byte[length];in.readBytes(marker);
        settings=new AutopilotModuleSettings(ChannelList.of(channels),activation,new String(marker,StandardCharsets.US_ASCII),in.readUnsignedByte());
    }
    public static boolean apply(net.minecraft.entity.player.EntityPlayerMP player,MessageModuleAutopilot message) {
        if(!player.getServerWorld().isCallingFromMinecraftThread()||message.pos==null||message.settings==null
                ||!(player.openContainer instanceof ContainerModuleRack))return false;
        ContainerModuleRack menu=(ContainerModuleRack)player.openContainer;
        if(menu.windowId!=message.window||!menu.tile.getPos().equals(message.pos)||!menu.canInteractWith(player)
                ||!ConfigurationAccess.canEditDialog(player,player.world,message.pos))return false;
        net.minecraft.block.Block marker=net.minecraft.block.Block.getBlockFromName(message.settings.marker);
        if(marker==null||marker==net.minecraft.init.Blocks.AIR)return false;
        if(!menu.tile.configureModule(message.slot,message.sequence,message.settings))return false;
        WorldActionAuthorization.authorize(menu.tile,player);return true;
    }
    public static final class Handler implements IMessageHandler<MessageModuleAutopilot,IMessage> {
        @Override public IMessage onMessage(MessageModuleAutopilot message,MessageContext context) {
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->apply(player,message));return null;
        }
    }
}
