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
    private int layout;

    public MessageProgrammableSlabSides() { }
    public MessageProgrammableSlabSides(BlockPos pos, boolean tileSides) {
        this.pos = pos;
        this.tileSides = tileSides;
        this.layout=tileSides?1:0;
    }
    public MessageProgrammableSlabSides(BlockPos pos,int layout){this.pos=pos;this.layout=layout;this.tileSides=layout==1;}
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        tileSides = buf.readBoolean();
        layout=buf.isReadable()?buf.readUnsignedByte():tileSides?1:0;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(tileSides);
        buf.writeByte(layout);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableSlabSides, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableSlabSides msg,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || !player.world.isBlockLoaded(msg.pos)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))
                    return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile != container.getTileEntity() || !container.canInteractWith(player))
                    return;
                if(!(tile instanceof TileEntityAnimatedScreenSelector) || !com.vandorlabs.render.TextureLayoutMode.valid(msg.layout))return;
                if(tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableBlock || tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableWall || tile.getBlockType()==ModBlocks.PROGRAMMABLE_SLAB || tile.getBlockType()==ModBlocks.PROGRAMMABLE_STAIRS)
                    ((TileEntityAnimatedScreenSelector) tile).setMainTextureLayout(msg.layout);
                else ((TileEntityAnimatedScreenSelector) tile).setSurfaceTextureLayout(msg.layout);
            });
            return null;
        }
    }
}
