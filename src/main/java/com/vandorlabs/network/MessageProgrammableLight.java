package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Applies the artwork and brightness chosen in the light menu. */
public final class MessageProgrammableLight implements IMessage {
    private BlockPos pos;
    private int texture;
    private int level;
    private boolean join;
    private int channel;
    private int housing;

    public MessageProgrammableLight() { }
    public MessageProgrammableLight(BlockPos pos, int texture, int level,
            boolean join, int channel) {
        this(pos, texture, level, join, channel, 0);
    }

    public MessageProgrammableLight(BlockPos pos, int texture, int level,
            boolean join, int channel, int housing) {
        this.pos = pos;
        this.texture = texture;
        this.level = level;
        this.join = join;
        this.channel = channel;
        this.housing = housing;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        texture = buf.readInt();
        level = buf.readInt();
        join = buf.readBoolean();
        channel = buf.readInt();
        housing = buf.readInt();
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(texture);
        buf.writeInt(level);
        buf.writeBoolean(join);
        buf.writeInt(channel);
        buf.writeInt(housing);
    }

    public static final class Handler implements IMessageHandler<MessageProgrammableLight, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableLight msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.texture < 0
                        || msg.texture >= com.vandorlabs.tiles.ProgrammableLightTextures.IDS.length
                        || msg.housing < 0
                        || msg.housing >= com.vandorlabs.tiles.ScreenHousingTextures.IDS.length
                        || msg.level < 0 || msg.level > 15 || msg.channel < 0
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return;
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (!(tile instanceof TileEntityProgrammableLight)
                        || tile != container.getTileEntity()
                        || !container.canInteractWith(player)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_LIGHT) return;
                ((TileEntityProgrammableLight) tile).configure(
                        msg.texture, msg.level, msg.join, msg.channel, msg.housing);
            });
            return null;
        }
    }
}
