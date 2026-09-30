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
    private BlockPos pos;private int texture,position,channel,trigger;private boolean sliding;
    public MessageProgrammableTrapdoor(){}
    public MessageProgrammableTrapdoor(BlockPos pos,int texture,int position,boolean sliding,int trigger,int channel) {
        this.pos=pos;this.texture=texture;this.position=position;this.sliding=sliding;this.trigger=trigger;this.channel=channel;
    }
    @Override public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeInt(texture);b.writeInt(position);b.writeBoolean(sliding);b.writeInt(trigger);b.writeInt(channel);}
    @Override public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());texture=b.readInt();position=b.readInt();sliding=b.readBoolean();trigger=b.readInt();channel=b.readInt();}
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
                for(TileEntityProgrammableTrapdoor leaf:leaves)leaf.configure(m.texture,m.position,m.sliding,m.trigger,m.channel);
            });return null;
        }
    }
}
