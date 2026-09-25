package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Applies a porthole glass shade while its configuration dialog is open. */
public final class MessageProgrammableWallShade implements IMessage {
    private BlockPos pos;
    private int shade;
    private boolean join;

    public MessageProgrammableWallShade() {}
    public MessageProgrammableWallShade(BlockPos pos, int shade, boolean join) {
        this.pos = pos;
        this.shade = shade;
        this.join = join;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        shade = buf.readInt();
        join = buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(shade);
        buf.writeBoolean(join);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableWallShade, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableWallShade msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.shade < 0 || msg.shade > 2
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                if (!(raw instanceof TileEntityAnimatedScreenSelector)
                        || raw != container.getTileEntity()
                        || !container.canInteractWith(player)
                        || !(player.world.getBlockState(msg.pos).getBlock()
                        instanceof BlockProgrammableWall)
                        || ((BlockProgrammableWall) player.world.getBlockState(msg.pos)
                        .getBlock()).getShape() != BlockProgrammableWall.Shape.PORTHOLE) return;
                ((TileEntityAnimatedScreenSelector) raw).setGlassShade(msg.shade);
                ((TileEntityAnimatedScreenSelector) raw).setJoinPortholes(msg.join);
            });
            return null;
        }
    }
}
