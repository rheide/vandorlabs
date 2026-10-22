package com.vandorlabs.network;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
public final class MessageStairGeometry implements IMessage {
    private BlockPos pos;private int tread,roll;
    public MessageStairGeometry(){}
    public MessageStairGeometry(BlockPos pos,int tread,int roll){this.pos=pos;this.tread=tread;this.roll=roll;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(tread);b.writeByte(roll);}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());tread=b.readUnsignedByte();roll=b.readUnsignedByte();}
    public static final class Handler implements IMessageHandler<MessageStairGeometry,IMessage>{
        public IMessage onMessage(MessageStairGeometry m,MessageContext context){
            net.minecraft.entity.player.EntityPlayerMP p=context.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
                if(m.pos==null||!p.world.isBlockLoaded(m.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(p)||!(p.openContainer instanceof ContainerAnimatedScreenSelector)||!(p.world.getBlockState(m.pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs))return;
                ContainerAnimatedScreenSelector c=(ContainerAnimatedScreenSelector)p.openContainer;
                if(c.getTileEntity()!=p.world.getTileEntity(m.pos)||!c.canInteractWith(p))return;
                c.getTileEntity().setStairGeometry(m.tread,m.roll);
            });return null;
        }
    }
}
