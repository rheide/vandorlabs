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

    public MessageDuplifierOptions() { }
    public MessageDuplifierOptions(long mask) { this.mask = mask; }

    @Override public void fromBytes(ByteBuf buf) { mask = buf.readLong(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeLong(mask); }

    public static final class Handler implements IMessageHandler<MessageDuplifierOptions, IMessage> {
        @Override public IMessage onMessage(MessageDuplifierOptions message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.mask < 0 || (message.mask & ~DuplifierApplyOptions.ALL) != 0
                        || !(player.openContainer instanceof ContainerDuplifier)
                        || !player.openContainer.canInteractWith(player)) return;
                ItemStack tool = player.getHeldItemMainhand();
                DuplifierApplyOptions.setMask(tool, message.mask);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
