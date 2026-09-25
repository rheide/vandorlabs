package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.container.ContainerProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class MessageProgrammableGlass implements IMessage {
    private BlockPos pos;
    private int size, shade;
    public MessageProgrammableGlass() {}
    public MessageProgrammableGlass(BlockPos pos, int size, int shade) {
        this.pos = pos; this.size = size; this.shade = shade;
    }
    @Override public void fromBytes(ByteBuf b) {
        pos = BlockPos.fromLong(b.readLong()); size = b.readInt(); shade = b.readInt();
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(pos.toLong()); b.writeInt(size); b.writeInt(shade);
    }
    public static final class Handler implements IMessageHandler<MessageProgrammableGlass, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableGlass msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.size < 0 || msg.size > 2
                        || msg.shade < 0 || msg.shade > 2
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerProgrammableGlass)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                ContainerProgrammableGlass container = (ContainerProgrammableGlass) player.openContainer;
                if (!(te instanceof TileEntityProgrammableGlass) || container.tile != te
                        || !container.canInteractWith(player)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) return;
                IBlockState state = player.world.getBlockState(msg.pos);
                if (!(state.getBlock() instanceof BlockProgrammableGlass)) return;
                if (state.getValue(BlockProgrammableGlass.DEPTH) == 0) {
                    IBlockState changed = state.withProperty(BlockProgrammableGlass.SIZE, msg.size);
                    if (changed != state) player.world.setBlockState(msg.pos, changed, 3);
                }
                TileEntity updated = player.world.getTileEntity(msg.pos);
                if (updated instanceof TileEntityProgrammableGlass) {
                    ((TileEntityProgrammableGlass) updated).setSize(msg.size);
                    ((TileEntityProgrammableGlass) updated).setShade(msg.shade);
                }
            });
            return null;
        }
    }
}
