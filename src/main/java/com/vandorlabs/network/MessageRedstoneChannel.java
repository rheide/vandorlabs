package com.vandorlabs.network;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockConnectedPropulsionLight;
import com.vandorlabs.tiles.TileEntityRedstoneLight;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageRedstoneChannel implements IMessage {
    private ChannelList channels;
    private boolean invalidChannels;
    public MessageRedstoneChannel withChannels(ChannelList channels) {
        if(channels==null)throw new IllegalArgumentException("Invalid channel list");
        this.channels=channels;this.channel=channels.first();invalidChannels=false;return this;
    }
    public ChannelList getRedstoneChannels() {
        return invalidChannels?null:channels!=null?channels:ChannelList.of(Math.max(0,channel));
    }

    private boolean controlMount;private int baseHeight,baseTilt,tiltDirection;
    public MessageRedstoneChannel withControlMount(boolean enabled,int height,int tilt,int direction){controlMount=enabled;baseHeight=height;baseTilt=tilt;tiltDirection=direction;return this;}
    private boolean controlLimits;private int lowLimit=5,highLimit=15;
    public MessageRedstoneChannel withControlLimits(boolean enabled,int low,int high){controlLimits=enabled;lowLimit=low;highLimit=high;return this;}
    private boolean signalBrightness;private int threshold=8;
    public MessageRedstoneChannel withSignalBrightness(boolean enabled,int threshold){this.signalBrightness=enabled;this.threshold=threshold;return this;}
    private BlockPos pos;
    private int channel;
    private boolean updateParticles;
    private int particleLevel;
    public MessageRedstoneChannel withParticleLevel(int level){if(level<0 || level>3)throw new IllegalArgumentException("Particle level");particleLevel=level;return this;}
    public int getParticleLevel(){return particleLevel;}
    private boolean updateJoin;
    private boolean join;
    private boolean updateSide;
    private int sideTexture;
    private boolean updateShape;
    private int shape;

    public MessageRedstoneChannel() { }
    public MessageRedstoneChannel(BlockPos pos, int channel) { this.pos = pos; this.channel = channel; }
    public MessageRedstoneChannel(BlockPos pos, int channel, boolean updateParticles,
            boolean particles) {
        this(pos, channel, updateParticles, particles, false, true);
    }
    public MessageRedstoneChannel(BlockPos pos, int channel, boolean updateParticles,
            boolean particles, boolean updateJoin, boolean join) {
        this(pos, channel, updateParticles, particles, updateJoin, join, false, 0);
    }
    public MessageRedstoneChannel(BlockPos pos, int channel, boolean updateParticles,
            boolean particles, boolean updateJoin, boolean join,
            boolean updateSide, int sideTexture) {
        this(pos, channel, updateParticles, particles, updateJoin, join,
                updateSide, sideTexture, false, 0);
    }
    public MessageRedstoneChannel(BlockPos pos, int channel, boolean updateParticles,
            boolean particles, boolean updateJoin, boolean join,
            boolean updateSide, int sideTexture, boolean updateShape, int shape) {
        this.pos = pos;
        this.channel = channel;
        this.updateParticles = updateParticles;
        this.particleLevel = particles?1:0;
        this.updateJoin = updateJoin;
        this.join = join;
        this.updateSide = updateSide;
        this.sideTexture = sideTexture;
        this.updateShape = updateShape;
        this.shape = shape;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        channel = buf.readInt();
        updateParticles = buf.readableBytes() > 0 && buf.readBoolean();
        particleLevel = buf.readableBytes() > 0 ? buf.readUnsignedByte() : 0;
        updateJoin = buf.readableBytes() > 0 && buf.readBoolean();
        join = buf.readableBytes() > 0 && buf.readBoolean();
        updateSide = buf.readableBytes() > 0 && buf.readBoolean();
        sideTexture = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        updateShape = buf.readableBytes() > 0 && buf.readBoolean();
        shape = buf.readableBytes() >= 4 ? buf.readInt() : 0;

        signalBrightness=buf.readBoolean();threshold=buf.readInt();
        controlLimits=buf.readBoolean();lowLimit=buf.readInt();highLimit=buf.readInt();
        controlMount=buf.readBoolean();baseHeight=buf.readInt();baseTilt=buf.readInt();tiltDirection=buf.readInt();
        channels=ChannelData.read(buf,channel);invalidChannels=channels==null || particleLevel>3;
        invalidChannels|=controlMount && !com.vandorlabs.blocks.SignalControlMount.valid(baseHeight,baseTilt,tiltDirection);
        invalidChannels|=threshold<0 || threshold>15 || controlLimits && !com.vandorlabs.tiles.TileEntitySignalControl.validLimits(lowLimit,highLimit);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(channel);
        buf.writeBoolean(updateParticles);
        buf.writeByte(particleLevel);
        buf.writeBoolean(updateJoin);
        buf.writeBoolean(join);
        buf.writeBoolean(updateSide);
        buf.writeInt(sideTexture);
        buf.writeBoolean(updateShape);
        buf.writeInt(shape);

        buf.writeBoolean(signalBrightness);buf.writeInt(threshold);
        buf.writeBoolean(controlLimits);buf.writeInt(lowLimit);buf.writeInt(highLimit);
        buf.writeBoolean(controlMount);buf.writeInt(baseHeight);buf.writeInt(baseTilt);buf.writeInt(tiltDirection);
        ChannelData.write(buf,channels!=null?channels:ChannelList.of(Math.max(0,channel)));
    }

    public static class Handler implements IMessageHandler<MessageRedstoneChannel, IMessage> {
        @Override public IMessage onMessage(MessageRedstoneChannel message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || message.getRedstoneChannels()==null || message.channel < 0 || !player.world.isBlockLoaded(message.pos)
                        || (message.updateSide && (message.sideTexture < 0
                        || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(message.sideTexture)))
                        || (message.updateShape && (message.shape < 0 || message.shape > 2
                        || !player.capabilities.isCreativeMode))) return;
                TileEntity tile = player.world.getTileEntity(message.pos);
                if (!(tile instanceof RedstoneChannelMember)
                        || !(player.openContainer instanceof ContainerRedstoneChannel)) return;
                ContainerRedstoneChannel container = (ContainerRedstoneChannel) player.openContainer;
                if (container.member != tile || !container.canInteractWith(player)) return;
                net.minecraft.block.Block block = player.world.getBlockState(message.pos)
                        .getBlock();
                if (block instanceof BlockConnectedPropulsionLight) {
                    ((BlockConnectedPropulsionLight)block).configureSignalAssembly(player.world,message.pos,message.signalBrightness,message.threshold);
                    ((BlockConnectedPropulsionLight) block).configureAssembly(player.world,
                            message.pos, message.getRedstoneChannels(), message.updateParticles,
                            message.particleLevel, message.updateJoin, message.join,
                            message.updateSide, message.sideTexture);
                } else {
                    if(message.controlLimits && tile instanceof com.vandorlabs.tiles.TileEntitySignalControl)((com.vandorlabs.tiles.TileEntitySignalControl)tile).configureLimits(message.lowLimit,message.highLimit);
                    if(message.controlMount && tile instanceof com.vandorlabs.tiles.TileEntitySignalControl)((com.vandorlabs.tiles.TileEntitySignalControl)tile).configureMount(message.baseHeight,message.baseTilt,message.tiltDirection);
                    ((RedstoneChannelMember) tile).setRedstoneChannels(message.getRedstoneChannels());
                    if (message.updateParticles && tile instanceof TileEntityRedstoneLight
                            && block instanceof BlockPropulsionLight)
                        ((TileEntityRedstoneLight) tile)
                                .setParticleLevel(message.particleLevel);
                    if (message.updateSide && tile instanceof TileEntityRedstoneLight
                            && block instanceof BlockPropulsionLight)
                        ((TileEntityRedstoneLight) tile).setSideTexture(message.sideTexture);
                }
                if(tile instanceof TileEntityRedstoneLight && block instanceof BlockPropulsionLight)((TileEntityRedstoneLight)tile).configureSignalBrightness(message.signalBrightness,message.threshold);
                if (message.updateShape && block instanceof BlockPropulsionLight
                        && !((BlockPropulsionLight) block).familyId().isEmpty()) {
                    BlockPropulsionLight.configureShape(player.world, message.pos,
                            message.shape);
                    if (message.updateJoin && message.shape == 0) {
                        TileEntity changed = player.world.getTileEntity(message.pos);
                        if (changed instanceof TileEntityRedstoneLight)
                            ((TileEntityRedstoneLight) changed).setJoin(message.join);
                    }
                }
            });
            return null;
        }
    }
}
