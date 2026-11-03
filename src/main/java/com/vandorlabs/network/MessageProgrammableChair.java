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
    private int height;
    private int angle=-1;
    public MessageProgrammableChair() { }
    public MessageProgrammableChair(BlockPos pos, int style, int height) {
        this.pos = pos; this.style = style; this.height = height;
    }
    public MessageProgrammableChair(BlockPos pos,int style,int height,int angle){this(pos,style,height);this.angle=angle;}
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong()); style = buf.readInt();
        height = buf.readInt();
        if(buf.readableBytes()!=0&&buf.readableBytes()!=1)throw new IllegalArgumentException("Invalid chair angle extension");
        angle=buf.isReadable()?buf.readUnsignedByte():-1;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeInt(style); buf.writeInt(height);if(angle>=0)buf.writeByte(angle);
    }
    public static final class Handler implements IMessageHandler<MessageProgrammableChair, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableChair msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.style < 0 || msg.style >= 5
                        || msg.height < 0 || msg.height > 2 || msg.angle< -1 || msg.angle>7
                        || !player.world.isBlockLoaded(msg.pos)
                        || !(player.openContainer instanceof ContainerProgrammableChair)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) return;
                ContainerProgrammableChair container =
                        (ContainerProgrammableChair) player.openContainer;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                if (raw != container.tile || !(raw instanceof TileEntityProgrammableChair)
                        || !container.canInteractWith(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_CHAIR) return;
                ((TileEntityProgrammableChair) raw).setStyle(msg.style);
                ((TileEntityProgrammableChair) raw).setHeight(msg.height);
                if(msg.angle>=0)((TileEntityProgrammableChair)raw).setAngle(msg.angle);
            });
            return null;
        }
    }
}
