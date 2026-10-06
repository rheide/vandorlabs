package com.vandorlabs.network;

import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.RedstoneScreenContents;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded row configuration; clicks use vanilla's server-side block interaction. */
public final class MessageRedstoneScreen implements IMessage {
    private BlockPos pos;
    private int housing,slot;
    private String title;
    private final List<String> labels=new ArrayList<>();
    private final List<ChannelList> channels=new ArrayList<>();
    private boolean valid;
    private final List<Boolean> sliders=new ArrayList<>();
    private final List<Integer> mins=new ArrayList<>(),maxs=new ArrayList<>();
    public MessageRedstoneScreen withSliders(List<Boolean> sliders,List<Integer> mins,List<Integer> maxs){this.sliders.addAll(sliders);this.mins.addAll(mins);this.maxs.addAll(maxs);return this;}

    public MessageRedstoneScreen(){}
    public MessageRedstoneScreen(BlockPos pos,List<String> labels,List<ChannelList> channels,int housing){
        this(pos,RedstoneScreenContents.DEFAULT_TITLE,labels,channels,housing);
    }
    public MessageRedstoneScreen(BlockPos pos,String title,List<String> labels,List<ChannelList> channels,int housing){
        this(pos,0,title,labels,channels,housing);
    }
    public MessageRedstoneScreen(BlockPos pos,int slot,String title,List<String> labels,List<ChannelList> channels,int housing){
        this.slot=slot;this.title=title;this.pos=pos;this.labels.addAll(labels);this.channels.addAll(channels);this.housing=housing;
    }
    public void toBytes(ByteBuf b){
        b.writeLong(pos.toLong());b.writeInt(housing);b.writeByte(labels.size());
        for(int i=0;i<labels.size();i++){
            byte[] label=labels.get(i).getBytes(StandardCharsets.UTF_8);b.writeByte(label.length);b.writeBytes(label);
            ChannelList list=channels.get(i);b.writeByte(list.size());for(int j=0;j<list.size();j++)b.writeInt(list.get(j));
        }
        for(int i=0;i<labels.size();i++){b.writeBoolean(sliders.size()>i && sliders.get(i));b.writeByte(mins.size()>i?mins.get(i):0);b.writeByte(maxs.size()>i?maxs.get(i):15);}
        byte[] heading=title.getBytes(StandardCharsets.UTF_8);b.writeByte(heading.length);b.writeBytes(heading);b.writeByte(slot);
    }
    public void fromBytes(ByteBuf b){
        valid=false;labels.clear();channels.clear();sliders.clear();mins.clear();maxs.clear();
        try {
            pos=BlockPos.fromLong(b.readLong());housing=b.readInt();int count=b.readUnsignedByte();
            if(count>RedstoneScreenContents.MAX_ROWS)return;
            for(int i=0;i<count;i++){
                int bytes=b.readUnsignedByte();if(bytes>RedstoneScreenContents.MAX_LABEL*4 || b.readableBytes()<bytes)return;
                String label=b.readCharSequence(bytes,StandardCharsets.UTF_8).toString();
                if(!RedstoneScreenContents.validLabel(label))return;
                int size=b.readUnsignedByte();if(size>ChannelList.MAX_CHANNELS)return;
                int[] values=new int[size];for(int j=0;j<size;j++)values[j]=b.readInt();
                labels.add(label);channels.add(ChannelList.of(values));
            }
            for(int i=0;i<count;i++){
                boolean slider=b.readBoolean();int min=b.readUnsignedByte(),max=b.readUnsignedByte();
                if(min>max || max>15 || slider && labels.get(i).length()>RedstoneScreenContents.SLIDER_LABEL)return;
                sliders.add(slider);mins.add(min);maxs.add(max);
            }
            int bytes=b.readUnsignedByte();if(bytes>RedstoneScreenContents.MAX_TITLE*4 || b.readableBytes()<bytes)return;
            title=b.readCharSequence(bytes,StandardCharsets.UTF_8).toString();
            slot=b.readUnsignedByte();valid=slot<=1 && RedstoneScreenContents.validTitle(title) && !b.isReadable();
        }catch(IndexOutOfBoundsException | IllegalArgumentException malformed){valid=false;}
    }
    public static final class Handler implements IMessageHandler<MessageRedstoneScreen,IMessage>{
        public IMessage onMessage(MessageRedstoneScreen m,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if(!m.valid || !ConfigurationAccess.canConfigure(player) || !player.world.isBlockLoaded(m.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))return;
                ContainerAnimatedScreenSelector container=(ContainerAnimatedScreenSelector)player.openContainer;
                if(container.redstoneSlot!=m.slot || !com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(container.getTileEntity().getBlockType(),m.slot) || player.world.getTileEntity(m.pos)!=container.getTileEntity()
                        || !container.canInteractWith(player))return;
                RedstoneScreenContents contents=container.getTileEntity().redstoneScreen(m.slot);
                net.minecraft.nbt.NBTTagList rows=new net.minecraft.nbt.NBTTagList();
                for(int i=0;i<m.labels.size();i++){
                    net.minecraft.nbt.NBTTagCompound row=new net.minecraft.nbt.NBTTagCompound();row.setString("Label",m.labels.get(i));com.vandorlabs.redstone.ChannelData.write(row,m.channels.get(i));
                    row.setBoolean("Slider",m.sliders.get(i));row.setInteger("Min",m.mins.get(i));row.setInteger("Max",m.maxs.get(i));rows.appendTag(row);
                }
                if(contents.applyRowConfiguration(m.title,rows))container.getTileEntity().setHousingTexture(m.housing);
            });return null;
        }
    }
}
