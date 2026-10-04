package com.vandorlabs.network;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded row configuration; clicks use vanilla's server-side block interaction. */
public final class MessageRedstoneScreen implements IMessage {
    private BlockPos pos;
    private int housing;
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private boolean valid;
    public MessageRedstoneScreen(){}
    public MessageRedstoneScreen(BlockPos pos,List<String> labels,List<ChannelList> channels,int housing){
        this.pos=pos;this.labels.addAll(labels);this.channels.addAll(channels);this.housing=housing;
    }
    public void toBytes(ByteBuf b){
        b.writeLong(pos.toLong());b.writeInt(housing);b.writeByte(labels.size());
        for(int i=0;i<labels.size();i++){
            byte[] label=labels.get(i).getBytes(StandardCharsets.UTF_8);b.writeByte(label.length);b.writeBytes(label);
            ChannelList list=channels.get(i);b.writeByte(list.size());for(int j=0;j<list.size();j++)b.writeInt(list.get(j));
        }
    }
    public void fromBytes(ByteBuf b){
        valid=false;labels.clear();channels.clear();
        try {
            pos=BlockPos.fromLong(b.readLong());housing=b.readInt();int count=b.readUnsignedByte();
            if(count>TileEntityRedstoneScreen.MAX_ROWS)return;
            for(int i=0;i<count;i++){
                int bytes=b.readUnsignedByte();if(bytes>TileEntityRedstoneScreen.MAX_LABEL*4)return;
                String label=b.readCharSequence(bytes,StandardCharsets.UTF_8).toString();
                if(!TileEntityRedstoneScreen.validLabel(label))return;
                int size=b.readUnsignedByte();if(size>ChannelList.MAX_CHANNELS)return;
                int[] values=new int[size];for(int j=0;j<size;j++)values[j]=b.readInt();
                labels.add(label);channels.add(ChannelList.of(values));
            }
            valid=!b.isReadable();
        }catch(IndexOutOfBoundsException | IllegalArgumentException malformed){valid=false;}
    }
    public static final class Handler implements IMessageHandler<MessageRedstoneScreen,IMessage>{
        public IMessage onMessage(MessageRedstoneScreen m,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!m.valid || !ConfigurationAccess.canConfigure(player) || !player.world.isBlockLoaded(m.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))return;
                ContainerAnimatedScreenSelector container=(ContainerAnimatedScreenSelector)player.openContainer;
                if(!(container.getTileEntity() instanceof TileEntityRedstoneScreen) || player.world.getTileEntity(m.pos)!=container.getTileEntity()
                        || !container.canInteractWith(player))return;
                ((TileEntityRedstoneScreen)container.getTileEntity()).configureRows(m.labels,m.channels,m.housing);
            });return null;
        }
    }
}
