package com.vandorlabs.network;

import com.vandorlabs.container.ContainerProgrammableTrapdoor;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessageProgrammableTrapdoor implements IMessage {
    private BlockPos pos;private int texture,position,channel,trigger;private boolean sliding,inverted,cover,tileTexture=true;
    private net.minecraft.util.EnumFacing facing=net.minecraft.util.EnumFacing.NORTH;
    public MessageProgrammableTrapdoor(){}
    public MessageProgrammableTrapdoor(BlockPos pos,int texture,int position,boolean sliding,int trigger,int channel) {
        this(pos,texture,position,sliding,trigger,channel,false);
    }
    public MessageProgrammableTrapdoor(BlockPos pos,int texture,int position,boolean sliding,int trigger,int channel,boolean inverted) {
        this.pos=pos;this.texture=texture;this.position=position;this.sliding=sliding;this.trigger=trigger;this.channel=channel;this.inverted=inverted;
    }
    public MessageProgrammableTrapdoor(BlockPos pos,int texture,int position,boolean sliding,int trigger,int channel,boolean inverted,boolean cover){this(pos,texture,position,sliding,trigger,channel,inverted);this.cover=cover;}
    public MessageProgrammableTrapdoor(BlockPos pos,int texture,int position,boolean sliding,int trigger,int channel,boolean inverted,boolean cover,boolean tileTexture,net.minecraft.util.EnumFacing facing){this(pos,texture,position,sliding,trigger,channel,inverted,cover);this.tileTexture=tileTexture;this.facing=facing;}
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(texture);b.writeInt(position);b.writeBoolean(sliding);b.writeInt(trigger);b.writeInt(channel);b.writeBoolean(inverted);b.writeBoolean(cover);b.writeBoolean(tileTexture);b.writeByte(facing.getHorizontalIndex());}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());texture=b.readInt();position=b.readInt();sliding=b.readBoolean();trigger=b.readInt();channel=b.readInt();inverted=b.readBoolean();cover=b.readBoolean();tileTexture=b.readBoolean();facing=net.minecraft.util.EnumFacing.getHorizontal(b.readUnsignedByte());}
    public static final class Handler implements IMessageHandler<MessageProgrammableTrapdoor,IMessage> {
        @Override public IMessage onMessage(MessageProgrammableTrapdoor m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(m.pos==null || !TileEntityProgrammableTrapdoor.valid(m.texture,m.position,m.trigger,m.channel)
                        || !ConfigurationAccess.canConfigure(player) || !player.world.isBlockLoaded(m.pos)
                        || !(player.openContainer instanceof ContainerProgrammableTrapdoor))return;
                TileEntity raw=player.world.getTileEntity(m.pos);
                ContainerProgrammableTrapdoor container=(ContainerProgrammableTrapdoor)player.openContainer;
                if(!(raw instanceof TileEntityProgrammableTrapdoor) || container.member!=raw || !container.canInteractWith(player))return;
                java.util.List<TileEntityProgrammableTrapdoor> leaves=((TileEntityProgrammableTrapdoor)raw).group();
                for(TileEntityProgrammableTrapdoor leaf:leaves)if(!leaf.usable(player))return;
                ((TileEntityProgrammableTrapdoor)raw).configureGroup(m.texture,m.position,m.sliding,m.trigger,m.channel,m.inverted,m.cover,m.tileTexture,m.facing);
            });return null;
        }
    }
}
