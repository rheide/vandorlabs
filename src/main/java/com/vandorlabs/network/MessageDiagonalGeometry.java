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
public final class MessageDiagonalGeometry implements IMessage {
    private BlockPos pos;
    private int mode, fill;

    public MessageDiagonalGeometry() { }
    public MessageDiagonalGeometry(BlockPos pos, int mode, int fill) {
        this.pos = pos;
        this.mode = mode; this.fill = fill;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        mode = buf.readUnsignedByte(); fill = buf.readUnsignedByte();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(mode); buf.writeByte(fill);
    }

    public static final class Handler implements IMessageHandler<MessageDiagonalGeometry, IMessage> {
        @Override public IMessage onMessage(MessageDiagonalGeometry msg,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.mode > 2 || msg.fill > 3 || msg.pos == null || !player.world.isBlockLoaded(msg.pos)
                        || !ConfigurationAccess.canConfigure(player)
                        || !(player.world.getBlockState(msg.pos).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableWall)
                        || !((com.vandorlabs.blocks.BlockProgrammableWall)player.world.getBlockState(msg.pos).getBlock()).isDiagonalShape()
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))
                    return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile != container.getTileEntity()
                        || !container.canInteractWith(player)) return;
                com.vandorlabs.blocks.BlockProgrammableWall block = (com.vandorlabs.blocks.BlockProgrammableWall)player.world.getBlockState(msg.pos).getBlock();
                if (block.isPortholeShape() && (msg.mode == 2 || msg.fill != 0)) return;
                ((TileEntityAnimatedScreenSelector) tile).setDiagonalGeometry(msg.mode, msg.fill);
            });
            return null;
        }
    }
}
