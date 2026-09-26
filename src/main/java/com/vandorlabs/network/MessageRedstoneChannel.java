package com.vandorlabs.network;

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
    private BlockPos pos;
    private int channel;
    private boolean updateParticles;
    private boolean particles;
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
        this.particles = particles;
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
        particles = buf.readableBytes() > 0 && buf.readBoolean();
        updateJoin = buf.readableBytes() > 0 && buf.readBoolean();
        join = buf.readableBytes() > 0 && buf.readBoolean();
        updateSide = buf.readableBytes() > 0 && buf.readBoolean();
        sideTexture = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        updateShape = buf.readableBytes() > 0 && buf.readBoolean();
        shape = buf.readableBytes() >= 4 ? buf.readInt() : 0;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(channel);
        buf.writeBoolean(updateParticles);
        buf.writeBoolean(particles);
        buf.writeBoolean(updateJoin);
        buf.writeBoolean(join);
        buf.writeBoolean(updateSide);
        buf.writeInt(sideTexture);
        buf.writeBoolean(updateShape);
        buf.writeInt(shape);
    }

    public static class Handler implements IMessageHandler<MessageRedstoneChannel, IMessage> {
        @Override public IMessage onMessage(MessageRedstoneChannel message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || message.channel < 0 || !player.world.isBlockLoaded(message.pos)
                        || (message.updateSide && (message.sideTexture < 0
                        || message.sideTexture >= com.vandorlabs.tiles.ScreenHousingTextures.IDS.length))
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
                    ((BlockConnectedPropulsionLight) block).configureAssembly(player.world,
                            message.pos, message.channel, message.updateParticles,
                            message.particles, message.updateJoin, message.join,
                            message.updateSide, message.sideTexture);
                } else {
                    ((RedstoneChannelMember) tile).setRedstoneChannel(message.channel);
                    if (message.updateParticles && tile instanceof TileEntityRedstoneLight
                            && block instanceof BlockPropulsionLight)
                        ((TileEntityRedstoneLight) tile)
                                .setParticleStreamSelected(message.particles);
                    if (message.updateSide && tile instanceof TileEntityRedstoneLight
                            && block instanceof BlockPropulsionLight)
                        ((TileEntityRedstoneLight) tile).setSideTexture(message.sideTexture);
                }
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
