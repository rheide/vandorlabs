package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Diagonal Corner Glass size and slope from its dialog; the bevel's turn is kept. */
public final class MessageCornerGlassGeometry implements IMessage {
    private BlockPos pos;
    private int size;
    private boolean inverted,far;
    public MessageCornerGlassGeometry(){}
    public MessageCornerGlassGeometry(BlockPos pos,int size,boolean inverted,boolean far){this.pos=pos;this.size=size;this.inverted=inverted;this.far=far;}
    @Override public void fromBytes(ByteBuf bytes){
        pos=BlockPos.fromLong(bytes.readLong());size=bytes.readUnsignedByte();int flags=bytes.readUnsignedByte();
        if(size>2||(flags&~3)!=0||bytes.isReadable())throw new IllegalArgumentException("Invalid corner glass geometry");
        inverted=(flags&1)!=0;far=(flags&2)!=0;
    }
    @Override public void toBytes(ByteBuf bytes){bytes.writeLong(pos.toLong());bytes.writeByte(size);bytes.writeByte((inverted?1:0)|(far?2:0));}
    public static final class Handler implements IMessageHandler<MessageCornerGlassGeometry,IMessage> {
        @Override public IMessage onMessage(MessageCornerGlassGeometry msg,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->apply(player,msg));
            return null;
        }
    }
    /** Server thread: validate the open dialog and permissions, then apply size and slope. */
    public static void apply(EntityPlayerMP player,MessageCornerGlassGeometry msg){
        if(msg.pos==null||!player.world.isBlockLoaded(msg.pos)||!(player.openContainer instanceof ContainerProgrammableGlass))return;
        TileEntity te=player.world.getTileEntity(msg.pos);
        ContainerProgrammableGlass container=(ContainerProgrammableGlass)player.openContainer;
        if(!(te instanceof TileEntityProgrammableGlass)||container.tile!=te||!container.canInteractWith(player)
                ||!com.vandorlabs.items.ConfigurationAccess.canConfigure(player))return;
        IBlockState state=player.world.getBlockState(msg.pos);
        if(!(state.getBlock() instanceof BlockProgrammableCornerGlass)||!((BlockProgrammableCornerGlass)state.getBlock()).sloped())return;
        BlockProgrammableCornerGlass block=(BlockProgrammableCornerGlass)state.getBlock();
        int turn=block.getMetaFromState(state)&3;
        IBlockState next=block.getStateFromMeta(CornerGlassGeometry.diagonal(turn,msg.size,msg.inverted,msg.far).metadata());
        ((TileEntityProgrammableGlass)te).setCornerMode(msg.size);
        if(next!=state)player.world.setBlockState(msg.pos,next,3);
        player.world.notifyBlockUpdate(msg.pos,next,next,3);
    }
}
