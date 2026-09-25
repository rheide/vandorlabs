package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Updates the side texture layout while the slab configuration is open. */
public final class MessageProgrammableSlabSides implements IMessage {
    private BlockPos pos;
    private boolean tileSides;

    public MessageProgrammableSlabSides() { }
    public MessageProgrammableSlabSides(BlockPos pos, boolean tileSides) {
        this.pos = pos;
        this.tileSides = tileSides;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        tileSides = buf.readBoolean();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(tileSides);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableSlabSides, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableSlabSides msg,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || !player.world.isBlockLoaded(msg.pos)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_SLAB
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))
                    return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile != container.getTileEntity() || !container.canInteractWith(player))
                    return;
                ((TileEntityAnimatedScreenSelector) tile).setSlabTileSides(msg.tileSides);
            });
            return null;
        }
    }
}
