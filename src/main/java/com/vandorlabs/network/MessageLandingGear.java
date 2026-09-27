package com.vandorlabs.network;

import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.container.ContainerRedstoneChannel;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageLandingGear implements IMessage {
    private BlockPos pos;private int mode,channel,pixels;
    public MessageLandingGear(){}
    public MessageLandingGear(BlockPos pos,int mode,int channel,int pixels){this.pos=pos;this.mode=mode;this.channel=channel;this.pixels=pixels;}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());mode=b.readByte();channel=b.readInt();pixels=b.readByte();}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(mode);b.writeInt(channel);b.writeByte(pixels);}
    public static final class Handler implements IMessageHandler<MessageLandingGear,IMessage>{
        public IMessage onMessage(MessageLandingGear msg,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(msg.pos==null||msg.mode<0||msg.mode>2||msg.channel<0||msg.pixels<0||msg.pixels>64
                        ||!player.world.isBlockLoaded(msg.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        ||!(player.openContainer instanceof ContainerRedstoneChannel))return;
                ContainerRedstoneChannel c=(ContainerRedstoneChannel)player.openContainer;
                if(!(c.member instanceof TileEntityLandingGear)||player.world.getTileEntity(msg.pos)!=c.member||!c.canInteractWith(player))return;
                if(!((TileEntityLandingGear)c.member).configure(msg.mode,msg.channel,msg.pixels))
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Landing gear: extension is blocked."),true);
            });return null;
        }
    }
}
