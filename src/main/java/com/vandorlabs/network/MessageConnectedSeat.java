package com.vandorlabs.network;

import com.vandorlabs.tiles.TileEntityConnectedSeat;
import com.vandorlabs.container.ContainerProgrammableChair;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageConnectedSeat implements IMessage {
    private BlockPos pos;private boolean join;private int height;
    public MessageConnectedSeat(){}
    public MessageConnectedSeat(BlockPos pos,boolean join,int height){this.pos=pos;this.join=join;this.height=height;}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());join=b.readBoolean();height=b.readByte();}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeBoolean(join);b.writeByte(height);}
    public static final class Handler implements IMessageHandler<MessageConnectedSeat,IMessage>{
        public IMessage onMessage(MessageConnectedSeat msg,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(msg.pos==null||msg.height<0||msg.height>2||!player.world.isBlockLoaded(msg.pos)
                        ||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        ||!(player.openContainer instanceof ContainerProgrammableChair))return;
                ContainerProgrammableChair c=(ContainerProgrammableChair)player.openContainer;
                if(!(c.tile instanceof TileEntityConnectedSeat)||player.world.getTileEntity(msg.pos)!=c.tile
                        ||!c.canInteractWith(player)||!player.canPlayerEdit(msg.pos,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand())
                        ||!player.world.isBlockModifiable(player,msg.pos))return;
                TileEntityConnectedSeat tile=(TileEntityConnectedSeat)c.tile;tile.setJoin(msg.join);tile.setHeight(msg.height);
            });return null;
        }
    }
}
