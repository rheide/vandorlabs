package com.vandorlabs.network;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size activation edit; no inventory or unbounded NBT/string payloads. */
public final class MessageDisplayActivation implements IMessage {
    private BlockPos pos;private int mode,state,texture,playback,speed;private boolean valid,inverted;
    public MessageDisplayActivation(){}
    public MessageDisplayActivation(BlockPos pos,int mode,int state,int texture,int playback,int speed,boolean inverted){this.inverted=inverted;this.pos=pos;this.mode=mode;this.state=state;this.texture=texture;this.playback=playback;this.speed=speed;valid=DisplayActivation.valid(mode,state,texture,playback,speed);}
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeByte(mode);b.writeByte(state);b.writeInt(texture);b.writeByte(playback);b.writeByte(speed);b.writeBoolean(inverted);}
    @Override public void fromBytes(ByteBuf b){valid=false;if(b.readableBytes()!=17)return;pos=BlockPos.fromLong(b.readLong());mode=b.readUnsignedByte();state=b.readUnsignedByte();texture=b.readInt();playback=b.readByte();speed=b.readByte();int invert=b.readUnsignedByte();if(invert>1)return;inverted=invert==1;valid=DisplayActivation.valid(mode,state,texture,playback,speed);}
    public static final class Handler implements IMessageHandler<MessageDisplayActivation,IMessage>{
        @Override public IMessage onMessage(MessageDisplayActivation m,MessageContext context){net.minecraft.entity.player.EntityPlayerMP p=context.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
            if(!m.valid||!p.world.isBlockLoaded(m.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(p)||!(p.openContainer instanceof ContainerAnimatedScreenSelector))return;
            net.minecraft.tileentity.TileEntity raw=p.world.getTileEntity(m.pos);ContainerAnimatedScreenSelector container=(ContainerAnimatedScreenSelector)p.openContainer;
            if(!(raw instanceof TileEntityAnimatedScreenSelector)||container.getTileEntity()!=raw||!container.canInteractWith(p)||!TileEntityAnimatedScreenSelector.supportsActivation(raw.getBlockType()))return;
            TileEntityAnimatedScreenSelector origin=(TileEntityAnimatedScreenSelector)raw;
            if(!DisplayEditGroups.apply(origin,m.mode,m.state,m.texture,m.playback,m.speed,m.inverted,target->
                    p.world.isBlockLoaded(target.getPos(),false)&&p.world.getTileEntity(target.getPos())==target
                    &&!com.vandorlabs.gear.ArmTransactions.locked(p.world,target.getPos())
                    &&p.canPlayerEdit(target.getPos(),net.minecraft.util.EnumFacing.UP,p.getHeldItemMainhand())&&p.world.isBlockModifiable(p,target.getPos())))
                for(TileEntityAnimatedScreenSelector target:DisplayEditGroups.members(origin)){net.minecraft.block.state.IBlockState state=p.world.getBlockState(target.getPos());p.world.notifyBlockUpdate(target.getPos(),state,state,2);}
        });return null;}
    }
}
