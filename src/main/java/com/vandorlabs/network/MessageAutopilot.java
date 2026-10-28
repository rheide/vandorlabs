package com.vandorlabs.network;

import com.vandorlabs.tiles.*;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.redstone.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Configuration requires the exact open container, edit permission and reach. */
public final class MessageAutopilot implements IMessage {
    private BlockPos pos;private NBTTagCompound settings;
    public MessageAutopilot(){}
    public MessageAutopilot(BlockPos pos,NBTTagCompound settings){this.pos=pos;this.settings=settings;}
    public void fromBytes(ByteBuf b){pos=BlockPos.fromLong(b.readLong());settings=ByteBufUtils.readTag(b);}
    public void toBytes(ByteBuf b){b.writeLong(pos.toLong());ByteBufUtils.writeTag(b,settings);}
    public static final class Handler implements IMessageHandler<MessageAutopilot,IMessage>{
        public IMessage onMessage(MessageAutopilot m,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(m.settings==null||m.pos==null||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,player.world,m.pos)||!(player.openContainer instanceof ContainerRedstoneChannel))return;
            ContainerRedstoneChannel c=(ContainerRedstoneChannel)player.openContainer;if(!(c.member instanceof TileEntityAutopilot)||player.world.getTileEntity(m.pos)!=c.member||!c.canInteractWith(player))return;
            TileEntityAutopilot tile=(TileEntityAutopilot)c.member;NBTTagCompound n=m.settings;ChannelList channels=ChannelData.read(n,0);
            if(tile instanceof TileEntityWaypoint){int delay=n.getInteger("WaypointDelay"),stop=n.getInteger("WaypointStop"),depart=n.getInteger("WaypointDepart"),height=n.getInteger("WaypointHeight");if(delay<0||delay>72000||stop<0||stop>2||depart<1||depart>2||height<0||height>16)return;TileEntityWaypoint w=(TileEntityWaypoint)tile;w.autoDepart=n.getBoolean("WaypointAuto");w.directSignal=n.getBoolean("WaypointDirect");w.delay=delay;w.stopMode=stop;w.departMode=depart;w.hoverHeight=height;}
            else{int mode=n.getInteger("AutopilotTrigger"),meta=n.getInteger("RouteMarkerMeta");String marker=n.getString("RouteMarker");if(marker.length()>128||!marker.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))return;net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName(marker);if(mode<0||mode>2||meta<0||meta>15||block==null||block==net.minecraft.init.Blocks.AIR)return;tile.trigger=mode;tile.marker=marker;tile.markerMeta=meta;tile.setEnabled(false);}
            WorldActionAuthorization.authorize(tile,player);tile.setRedstoneChannels(tile instanceof com.vandorlabs.tiles.TileEntityWaypoint?com.vandorlabs.redstone.TransmitterSlots.channels(tile):channels);tile.changed();
        });return null;}
    }
}
