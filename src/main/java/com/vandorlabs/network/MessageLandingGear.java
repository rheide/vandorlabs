package com.vandorlabs.network;

import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.container.ContainerRedstoneChannel;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageLandingGear implements IMessage {
    private BlockPos pos;private int mode,channel,pixels;private int size=-1;
    public MessageLandingGear(){}
    public MessageLandingGear(BlockPos pos,int pixels){this(pos,-1,0,pixels);}
    public MessageLandingGear(BlockPos pos,int mode,int channel,int pixels){this.pos=pos;this.mode=mode;this.channel=channel;this.pixels=pixels;}
    public MessageLandingGear(BlockPos pos,int mode,int channel,int pixels,int size){this(pos,mode,channel,pixels);this.size=size;}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());mode=b.readByte();channel=b.readInt();pixels=b.readByte();size=b.isReadable()?b.readByte():-1;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(mode);b.writeInt(channel);b.writeByte(pixels);b.writeByte(size);}
    public static final class Handler implements IMessageHandler<MessageLandingGear,IMessage>{
        public IMessage onMessage(MessageLandingGear msg,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(msg.pos==null||msg.size< -1||msg.size>2||msg.mode< -1||msg.mode>2||msg.channel<0||msg.pixels<0||msg.pixels>64||msg.pixels%8!=0
                        ||!player.world.isBlockLoaded(msg.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        ||!(player.openContainer instanceof ContainerRedstoneChannel))return;
                ContainerRedstoneChannel c=(ContainerRedstoneChannel)player.openContainer;
                if(!(c.member instanceof TileEntityLandingGear)||player.world.getTileEntity(msg.pos)!=c.member||!c.canInteractWith(player))return;
                TileEntityLandingGear tile=(TileEntityLandingGear)c.member;
                if(!tile.configure(msg.mode<0?tile.getMode():msg.mode,msg.mode<0?tile.getRedstoneChannel():msg.channel,msg.pixels,msg.size<0?tile.getSize():msg.size)){
                    tile.sync();
                    player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Landing gear: extension is blocked."),true);
                }
            });return null;
        }
    }
}
