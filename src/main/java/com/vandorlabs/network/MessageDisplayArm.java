package com.vandorlabs.network;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size geometry edit; the server owns placement, permission and validation. */
public final class MessageDisplayArm implements IMessage {
    private BlockPos pos;
    private int facing;
    private DisplayArmSettings settings;
    public MessageDisplayArm() { }
    public MessageDisplayArm(BlockPos pos, EnumFacing facing, DisplayArmSettings settings) {
        this.pos = pos;
        this.facing = facing.getIndex();
        this.settings = settings;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        if (buffer.readableBytes() != 29) throw new IllegalArgumentException("Invalid display arm packet length");
        pos = BlockPos.fromLong(buffer.readLong());
        facing = buffer.readUnsignedByte();
        int half = buffer.readUnsignedByte(), input = buffer.readUnsignedByte();
        int right = buffer.readInt(), height = buffer.readInt(), forward = buffer.readInt();
        int yaw = buffer.readShort(), pitch = buffer.readShort(), roll = buffer.readShort();
        if (facing < 2 || facing > 5 || half > 1 || input > 1
                || !DisplayArmSettings.valid(half != 0, input != 0, right, height, forward, yaw, pitch, roll))
            throw new IllegalArgumentException("Invalid display arm settings");
        settings = new DisplayArmSettings(half != 0, input != 0, right, height, forward, yaw, pitch, roll);
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(pos.toLong());
        buffer.writeByte(facing);
        buffer.writeBoolean(settings.half);
        buffer.writeBoolean(settings.input);
        buffer.writeInt(settings.right);
        buffer.writeInt(settings.height);
        buffer.writeInt(settings.forward);
        buffer.writeShort(settings.yaw);
        buffer.writeShort(settings.pitch);
        buffer.writeShort(settings.roll);
    }
    public static boolean canApply(net.minecraft.entity.player.EntityPlayer player, TileEntityDisplayArm tile) {
        if (player == null || tile == null || tile.getWorld() != player.world
                || !ConfigurationAccess.canConfigure(player)
                || !ConfigurationAccess.canEditDialog(player, player.world, tile.getPos())
                || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return false;
        ContainerAnimatedScreenSelector container = (ContainerAnimatedScreenSelector) player.openContainer;
        return container.getTileEntity() == tile && container.canInteractWith(player);
    }
    public static final class Handler implements IMessageHandler<MessageDisplayArm, IMessage> {
        @Override public IMessage onMessage(MessageDisplayArm message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || message.settings == null || message.facing < 2 || message.facing > 5
                        || !player.world.isBlockLoaded(message.pos)) return;
                TileEntity raw = player.world.getTileEntity(message.pos);
                if (!(raw instanceof TileEntityDisplayArm) || !canApply(player, (TileEntityDisplayArm)raw)) return;
                TileEntityDisplayArm tile = (TileEntityDisplayArm) raw;
                player.world.setBlockState(message.pos, player.world.getBlockState(message.pos)
                        .withProperty(BlockAnimatedScreenSelector.FACING, EnumFacing.getFront(message.facing)), 3);
                tile.setArmSettings(message.settings);
            });
            return null;
        }
    }
}
