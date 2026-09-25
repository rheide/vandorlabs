package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerProgrammableChair;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class MessageProgrammableChair implements IMessage {
    private BlockPos pos;
    private int style;
    public MessageProgrammableChair() { }
    public MessageProgrammableChair(BlockPos pos, int style) {
        this.pos = pos; this.style = style;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong()); style = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeInt(style);
    }
    public static final class Handler implements IMessageHandler<MessageProgrammableChair, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableChair msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.style < 0 || msg.style >= 5
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerProgrammableChair)
                        || !player.capabilities.isCreativeMode) return;
                ContainerProgrammableChair container =
                        (ContainerProgrammableChair) player.openContainer;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                if (raw != container.tile || !(raw instanceof TileEntityProgrammableChair)
                        || !container.canInteractWith(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_CHAIR) return;
                ((TileEntityProgrammableChair) raw).setStyle(msg.style);
            });
            return null;
        }
    }
}
