package com.vandorlabs.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import com.vandorlabs.container.*;
import com.vandorlabs.redstone.ChannelList;

/** Open/install/edit requests; item movement itself uses vanilla window-click validation. */
public final class MessageTransmitterSlot implements IMessage {
    private int action,selector,window;
    private BlockPos pos=BlockPos.ORIGIN;
    private String text="";
    public MessageTransmitterSlot(){}
    public static MessageTransmitterSlot open(BlockPos pos,int selector){MessageTransmitterSlot m=new MessageTransmitterSlot();m.pos=pos;m.selector=selector;return m;}
    public static MessageTransmitterSlot edit(int window){MessageTransmitterSlot m=new MessageTransmitterSlot();m.action=1;m.window=window;return m;}
    public static MessageTransmitterSlot save(int window,ChannelList channels){MessageTransmitterSlot m=new MessageTransmitterSlot();m.action=2;m.window=window;m.text=channels.toString();return m;}
    @Override public void toBytes(ByteBuf b){b.writeByte(action);b.writeLong(pos.toLong());b.writeInt(selector);b.writeInt(window);ByteBufUtils.writeUTF8String(b,text);}
    @Override public void fromBytes(ByteBuf b){action=b.readUnsignedByte();pos=BlockPos.fromLong(b.readLong());selector=b.readInt();window=b.readInt();int bytes=ByteBufUtils.readVarInt(b,2);if(bytes<0||bytes>ChannelList.MAX_TEXT_LENGTH*3||bytes>b.readableBytes())throw new IllegalArgumentException("Transmitter channel text too long");text=b.toString(b.readerIndex(),bytes,java.nio.charset.StandardCharsets.UTF_8);b.skipBytes(bytes);if(text.length()>ChannelList.MAX_TEXT_LENGTH||b.isReadable()||action>2)throw new IllegalArgumentException("Malformed transmitter request");}
    private static net.minecraft.tileentity.TileEntity openedTile(net.minecraft.inventory.Container container){
        if(container instanceof ContainerRedstoneChannel)return ((ContainerRedstoneChannel)container).member.channelTile();
        if(container instanceof ContainerAnimatedScreenSelector)return ((ContainerAnimatedScreenSelector)container).getTileEntity();
        if(container instanceof ContainerCockpit)return ((ContainerCockpit)container).tile;
        if(container instanceof ContainerTokenSlot)return ((ContainerTokenSlot)container).tile;
        if(container instanceof ContainerRampController)return ((ContainerRampController)container).controller;
        if(container instanceof ContainerLargeCanopy)return ((ContainerLargeCanopy)container).tile;
        if(container instanceof ContainerTurntable)return ((ContainerTurntable)container).tile;
        if(container instanceof com.vandorlabs.plate.PlateContainer)return ((com.vandorlabs.plate.PlateContainer)container).tile;
        if(container instanceof ContainerModularGear)return ((ContainerModularGear)container).currentTile();
        if(container instanceof com.vandorlabs.coupler.ContainerCoupler)return ((com.vandorlabs.coupler.ContainerCoupler)container).tile;
        if(container instanceof ContainerTransmitterSlot)return ((ContainerTransmitterSlot)container).member.channelTile();
        return null;
    }
    public static final class Handler implements IMessageHandler<MessageTransmitterSlot,IMessage>{public IMessage onMessage(MessageTransmitterSlot m,MessageContext c){net.minecraft.entity.player.EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
        if(m.action==0){if(m.selector<0||m.selector>255||!p.openContainer.canInteractWith(p))return;net.minecraft.tileentity.TileEntity tile=openedTile(p.openContainer);if(tile==null||!tile.getPos().equals(m.pos))return;com.vandorlabs.redstone.RedstoneChannelMember member=ContainerTransmitterSlot.resolve(tile,m.selector);if(member==null||!ContainerTransmitterSlot.usable(p,member))return;ContainerTransmitterSlot.open(p,member,m.selector,false);}
        else if(m.action==1&&p.openContainer.windowId==m.window&&p.openContainer instanceof ContainerTransmitterSlot&&p.openContainer.canInteractWith(p)){ContainerTransmitterSlot old=(ContainerTransmitterSlot)p.openContainer;if(!com.vandorlabs.items.ItemRedstoneTransmitter.valid(com.vandorlabs.redstone.TransmitterSlots.get(old.member)))return;ContainerTransmitterSlot.open(p,old.member,old.selector,true);}
        else if(m.action==2&&p.openContainer.windowId==m.window&&p.openContainer instanceof ContainerTransmitterItem){ChannelList list=ChannelList.parse(m.text);if(list!=null)((ContainerTransmitterItem)p.openContainer).configure(p,list);}
    });return null;}}
}
