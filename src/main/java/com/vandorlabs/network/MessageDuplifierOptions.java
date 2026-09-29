package com.vandorlabs.network;

import com.vandorlabs.container.ContainerDuplifier;
import com.vandorlabs.items.DuplifierApplyOptions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class MessageDuplifierOptions implements IMessage {
    private long mask;
    private boolean connected;

    public MessageDuplifierOptions() { }
    public MessageDuplifierOptions(long mask, boolean connected) {
        this.mask = mask;
        this.connected = connected;
    }

    @Override public void fromBytes(ByteBuf buf) { mask = buf.readLong(); connected = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(mask); buf.writeBoolean(connected); }

    public static final class Handler implements IMessageHandler<MessageDuplifierOptions, IMessage> {
        @Override public IMessage onMessage(MessageDuplifierOptions message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.mask < 0 || (message.mask & ~DuplifierApplyOptions.ALL) != 0
                        || !(player.openContainer instanceof ContainerDuplifier)
                        || !player.openContainer.canInteractWith(player)) return;
                ItemStack tool = player.getHeldItemMainhand();
                DuplifierApplyOptions.setMask(tool, message.mask);
                DuplifierApplyOptions.setConnected(tool, message.connected);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
