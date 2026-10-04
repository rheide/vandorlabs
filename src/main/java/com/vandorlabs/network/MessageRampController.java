package com.vandorlabs.network;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.container.ContainerRampController;
import com.vandorlabs.tiles.TileEntityRampController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRampController implements IMessage {
    private ChannelList channels;
    private boolean invalidChannels;
    public MessageRampController withChannels(ChannelList channels) {
        if(channels==null)throw new IllegalArgumentException("Invalid channel list");
        this.channels=channels;this.channel=channels.first();invalidChannels=false;return this;
    }
    public ChannelList getRedstoneChannels() {
        return invalidChannels?null:channels!=null?channels:ChannelList.of(Math.max(0,channel));
    }

    private BlockPos pos;
    private int treadPixels,startOffset,drop,segments,direction,channel,travelAxis,speed;
    private int startHalfSteps,endHalfSteps;
    private boolean top,powerOn,slow,elevator,extendSegments;
    private boolean matchTextures=true;
    public MessageRampController() { }
    public MessageRampController(BlockPos pos,int drop,int segments,boolean top,boolean powerOn,boolean slow,boolean elevator,
            net.minecraft.util.EnumFacing direction) {
        this(pos,drop,segments,top,powerOn,slow,elevator,direction,0);
    }
    public MessageRampController(BlockPos pos,int drop,int segments,boolean top,boolean powerOn,boolean slow,boolean elevator,
            net.minecraft.util.EnumFacing direction,int channel) {
        this.treadPixels=segments==8?2:8;
        this.pos=pos; this.drop=drop; this.segments=segments;
        this.top=top; this.powerOn=powerOn; this.slow=slow; this.elevator=elevator;
        this.speed=slow?2:1;
        this.direction=direction.getHorizontalIndex();
        this.channel=channel;
        this.endHalfSteps=(top?-drop:drop)*2;
    }
    public MessageRampController(BlockPos pos,int start,int end,int pixels,boolean powerOn,
            boolean slow,boolean elevator,net.minecraft.util.EnumFacing direction,int channel) {
        this(pos,Math.abs(end),com.vandorlabs.ramp.ControllerPlatform.treadCount(pixels),end<0,powerOn,slow,elevator,direction,channel);
        startOffset=start; treadPixels=pixels;
        startHalfSteps=start*2; endHalfSteps=end*2;
    }
    public MessageRampController(BlockPos pos,int start,int end,int pixels,boolean powerOn,
            boolean slow,boolean elevator,net.minecraft.util.EnumFacing direction,int channel,
            int travelAxis,boolean extendSegments) {
        this(pos,start,end,pixels,powerOn,slow,elevator,direction,channel);
        this.travelAxis=travelAxis; this.extendSegments=extendSegments;
    }
    public MessageRampController(BlockPos pos,int start,int end,int pixels,boolean powerOn,
            boolean slow,boolean elevator,net.minecraft.util.EnumFacing direction,int channel,
            int travelAxis,boolean extendSegments,int speed) {
        this(pos,start,end,pixels,powerOn,slow,elevator,direction,channel,travelAxis,extendSegments);
        this.speed=speed;
    }
    public MessageRampController(BlockPos pos,int start,int end,int pixels,boolean powerOn,
            boolean slow,boolean elevator,net.minecraft.util.EnumFacing direction,int channel,
            int travelAxis,boolean extendSegments,int speed,boolean matchTextures) {
        this(pos,start,end,pixels,powerOn,slow,elevator,direction,channel,travelAxis,extendSegments,speed);
        this.matchTextures=matchTextures;
    }
    public static MessageRampController halfOffsets(BlockPos pos,int startHalf,int endHalf,
            int pixels,boolean powerOn,boolean slow,boolean elevator,
            net.minecraft.util.EnumFacing direction,int channel,int travelAxis,
            boolean extendSegments,int speed,boolean matchTextures) {
        MessageRampController packet=new MessageRampController(pos,startHalf/2,endHalf/2,
                pixels,powerOn,slow,elevator,direction,channel,travelAxis,
                extendSegments,speed,matchTextures);
        packet.startHalfSteps=startHalf;
        packet.endHalfSteps=endHalf;
        return packet;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos=BlockPos.fromLong(buf.readLong()); drop=buf.readInt(); segments=buf.readInt();
        top=buf.readBoolean(); powerOn=buf.readBoolean(); slow=buf.readBoolean(); elevator=buf.readBoolean();
        direction=buf.readInt();
        channel=buf.readableBytes()>=4?buf.readInt():0;
        startOffset=buf.readableBytes()>=4?buf.readInt():0;
        treadPixels=buf.readableBytes()>=4?buf.readInt():(segments==8?2:8);
        travelAxis=buf.readableBytes()>=4?buf.readInt():0;
        extendSegments=buf.readableBytes()>=1 && buf.readBoolean();
        speed=buf.readableBytes()>=4?buf.readInt():(slow?2:1);
        matchTextures=!buf.isReadable()||buf.readBoolean();
        startHalfSteps=buf.readableBytes()>=4?buf.readInt():startOffset*2;
        endHalfSteps=buf.readableBytes()>=4?buf.readInt():(top?-drop:drop)*2;

        channels=ChannelData.read(buf,channel);invalidChannels=channels==null;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeInt(drop); buf.writeInt(segments);
        buf.writeBoolean(top); buf.writeBoolean(powerOn); buf.writeBoolean(slow); buf.writeBoolean(elevator);
        buf.writeInt(direction);
        buf.writeInt(channel); buf.writeInt(startOffset); buf.writeInt(treadPixels);
        buf.writeInt(travelAxis); buf.writeBoolean(extendSegments); buf.writeInt(speed); buf.writeBoolean(matchTextures);
        buf.writeInt(startHalfSteps); buf.writeInt(endHalfSteps);

        ChannelData.write(buf,channels!=null?channels:ChannelList.of(Math.max(0,channel)));
    }
    public static class Handler implements IMessageHandler<MessageRampController,IMessage> {
        @Override public IMessage onMessage(MessageRampController message,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if (message.pos==null || message.getRedstoneChannels()==null || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !player.world.isBlockLoaded(message.pos)) return;
                TileEntity raw=player.world.getTileEntity(message.pos);
                if (!(raw instanceof TileEntityRampController) || !(player.openContainer instanceof ContainerRampController)) return;
                TileEntityRampController te=(TileEntityRampController)raw;
                if (((ContainerRampController)player.openContainer).controller!=te || !te.usable(player)) return;
                if (message.direction<0 || message.direction>3 || message.channel<0) return;
                te.configureHalfOffsets(player,message.startHalfSteps,message.endHalfSteps,message.treadPixels,message.powerOn,message.slow,message.elevator,
                        net.minecraft.util.EnumFacing.getHorizontal(message.direction),message.travelAxis,message.extendSegments,message.speed,message.matchTextures);
                te.setRedstoneChannels(message.getRedstoneChannels());
                player.connection.sendPacket(te.getUpdatePacket());
            });
            return null;
        }
    }
}
