package com.vandorlabs.network;

import com.vandorlabs.container.ContainerTurntableController;
import com.vandorlabs.redstone.ChannelData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityTurntableController;
import com.vandorlabs.turntable.TurntableGeometry;
import com.vandorlabs.turntable.TurntableNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Dialog edits and scan/refresh requests for a Turntable Controller. */
public class MessageTurntableController implements IMessage {
    public static final int APPLY = 0;
    public static final int SCAN = 1;
    public static final int REQUEST_PAYLOAD = 2;

    private BlockPos pos = BlockPos.ORIGIN;
    private int action;
    private int trigger, height, direction, channel;
    private double speed;
    private ChannelList channels = ChannelList.EMPTY;
    private boolean invalidChannels;

    public MessageTurntableController() { }

    public static MessageTurntableController apply(BlockPos pos, int trigger, int height, double speed,
            int direction, ChannelList channels) {
        MessageTurntableController message = new MessageTurntableController();
        message.pos = pos;
        message.action = APPLY;
        message.trigger = trigger;
        message.height = height;
        message.speed = speed;
        message.direction = direction;
        message.channels = channels;
        message.channel = channels == null ? 0 : channels.first();
        message.invalidChannels = channels == null;
        return message;
    }

    public static MessageTurntableController scan(BlockPos pos) {
        MessageTurntableController message = new MessageTurntableController();
        message.pos = pos;
        message.action = SCAN;
        return message;
    }

    public static MessageTurntableController requestPayload(BlockPos pos) {
        MessageTurntableController message = new MessageTurntableController();
        message.pos = pos;
        message.action = REQUEST_PAYLOAD;
        return message;
    }

    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        action = buf.readUnsignedByte();
        trigger = buf.readInt();
        height = buf.readInt();
        speed = buf.readDouble();
        direction = buf.readInt();
        channel = buf.readInt();
        channels = ChannelData.read(buf, channel);
        invalidChannels = channels == null;
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(action);
        buf.writeInt(trigger);
        buf.writeInt(height);
        buf.writeDouble(speed);
        buf.writeInt(direction);
        buf.writeInt(channel);
        ChannelData.write(buf, channels == null ? ChannelList.of(Math.max(0, channel)) : channels);
    }

    public static class Handler implements IMessageHandler<MessageTurntableController, IMessage> {
        @Override public IMessage onMessage(MessageTurntableController message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || !player.world.isBlockLoaded(message.pos)) return;
                TileEntity raw = player.world.getTileEntity(message.pos);
                if (!(raw instanceof TileEntityTurntableController)) return;
                TileEntityTurntableController controller = (TileEntityTurntableController) raw;
                // Any nearby client may fetch the visual payload; only editors configure it.
                if (message.action == REQUEST_PAYLOAD) {
                    if (player.getDistanceSq(message.pos) <= (double) TurntableNetwork.RANGE * TurntableNetwork.RANGE)
                        TurntableNetwork.send(player, controller);
                    return;
                }
                if (!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player, player.world, message.pos))
                    return;
                if (!(player.openContainer instanceof ContainerTurntableController)
                        || ((ContainerTurntableController) player.openContainer).controller != controller
                        || !controller.usable(player)) return;
                if (message.invalidChannels) return;
                if (message.action == SCAN) {
                    controller.scan();
                    player.connection.sendPacket(controller.getUpdatePacket());
                    return;
                }
                if (message.trigger < 0 || message.trigger > 2
                        || message.direction < 0 || message.direction > 1) return;
                controller.trigger = message.trigger;
                controller.height = TurntableGeometry.clampHeight(message.height);
                controller.speed = TurntableGeometry.clampSpeed(message.speed);
                controller.rotationDirection = message.direction;
                controller.setRedstoneChannels(message.channels);
                controller.updatePower();
                player.connection.sendPacket(controller.getUpdatePacket());
            });
            return null;
        }
    }
}
