package com.vandorlabs.network;

import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.redstone.*;
import com.vandorlabs.container.ContainerRedstoneChannel;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class MessagePilotModes implements IMessage {
    private BlockPos pos;private ChannelList[] channels;private boolean open;
    public MessagePilotModes(){}
    public MessagePilotModes(BlockPos pos,ChannelList[] channels){this.pos=pos;this.channels=channels;open=channels==null;}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());b.writeBoolean(open);if(!open)for(ChannelList list:channels)ChannelData.write(b,list);}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());open=b.readBoolean();if(!open){channels=new ChannelList[3];for(int i=0;i<3;i++){if(!b.isReadable())return;int count=b.readUnsignedByte();if(count>ChannelList.MAX_CHANNELS||b.readableBytes()<count*4)return;int[] values=new int[count];for(int n=0;n<count;n++)values[n]=b.readInt();try{channels[i]=ChannelList.of(values);}catch(IllegalArgumentException invalid){return;}}if(b.isReadable())channels=null;}}
    public static final class Handler implements IMessageHandler<MessagePilotModes,IMessage>{
        public IMessage onMessage(MessagePilotModes m,MessageContext context){net.minecraft.entity.player.EntityPlayerMP p=context.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
            if(m.pos==null||!p.world.isBlockLoaded(m.pos)||!com.vandorlabs.items.ConfigurationAccess.canConfigure(p)||!(p.openContainer instanceof ContainerRedstoneChannel))return;
            ContainerRedstoneChannel c=(ContainerRedstoneChannel)p.openContainer;if(!(c.member instanceof TileEntityPilotSeat)||p.world.getTileEntity(m.pos)!=c.member||!c.canInteractWith(p))return;
            if(m.open){p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_PILOT_MODES,p.world,m.pos.getX(),m.pos.getY(),m.pos.getZ());return;}
            if(m.channels==null)return;for(ChannelList list:m.channels)if(list==null)return;
            for(int i=0;i<3;i++)((TileEntityPilotSeat)c.member).setModeChannels(i,m.channels[i]);
        });return null;}
    }
}
