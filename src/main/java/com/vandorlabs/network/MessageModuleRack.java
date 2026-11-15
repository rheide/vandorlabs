package com.vandorlabs.network;

import com.vandorlabs.container.ContainerModuleRack;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.ScreenHousingTextures;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Fixed-size configuration message; module inventories never enter the client request. */
public final class MessageModuleRack implements IMessage {
    private int window, housing;
    private BlockPos pos;
    private boolean emit;
    public MessageModuleRack() { }
    public MessageModuleRack(int window, BlockPos pos, int housing, boolean emit) {
        this.window = window; this.pos = pos; this.housing = housing; this.emit = emit;
    }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(window); buffer.writeLong(pos.toLong()); buffer.writeInt(housing); buffer.writeBoolean(emit); }
    @Override public void fromBytes(ByteBuf buffer) {
        if (buffer.readableBytes() != 17) throw new IllegalArgumentException("Malformed Rack configuration length");
        window = buffer.readInt(); pos = BlockPos.fromLong(buffer.readLong()); housing = buffer.readInt();
        int value = buffer.readUnsignedByte(); if (value > 1) throw new IllegalArgumentException("Malformed Rack output flag");
        emit = value != 0;
    }
    public static boolean apply(net.minecraft.entity.player.EntityPlayerMP player, MessageModuleRack message) {
        if (!player.getServerWorld().isCallingFromMinecraftThread() || message.pos == null
                || message.housing < -1 || message.housing >= 0 && !ScreenHousingTextures.validChoice(message.housing)
                || !(player.openContainer instanceof ContainerModuleRack)) return false;
        ContainerModuleRack container = (ContainerModuleRack)player.openContainer;
        if (container.windowId != message.window || !container.tile.getPos().equals(message.pos)
                || !container.canInteractWith(player) || !ConfigurationAccess.canEditDialog(player, player.world, message.pos)) return false;
        container.tile.setHousingTexture(message.housing); container.tile.setEmitsRedstone(message.emit); return true;
    }
    public static final class Handler implements IMessageHandler<MessageModuleRack,IMessage> {
        @Override public IMessage onMessage(MessageModuleRack message, MessageContext context) {
            net.minecraft.entity.player.EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message)); return null;
        }
    }
}
