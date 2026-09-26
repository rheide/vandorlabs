package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Changes the diagonal span while its creative configuration is open. */
public final class MessageProgrammableDiagonalWidth implements IMessage {
    private BlockPos pos;
    private boolean fullWidth;

    public MessageProgrammableDiagonalWidth() { }
    public MessageProgrammableDiagonalWidth(BlockPos pos, boolean fullWidth) {
        this.pos = pos;
        this.fullWidth = fullWidth;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        fullWidth = buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(fullWidth);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableDiagonalWidth, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableDiagonalWidth msg,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || !player.world.isBlockLoaded(msg.pos)
                        || !ConfigurationAccess.canConfigure(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_DIAGONAL_WALL
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))
                    return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile != container.getTileEntity()
                        || !container.canInteractWith(player)) return;
                ((TileEntityAnimatedScreenSelector) tile).setDiagonalFullWidth(msg.fullWidth);
            });
            return null;
        }
    }
}
