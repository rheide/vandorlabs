package com.vandorlabs.network;

import com.vandorlabs.container.ContainerProgrammableArmor;
import com.vandorlabs.items.ItemProgrammableArmor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Only the currently open, authorized armor dialog can change its bound stack. */
public final class MessageProgrammableArmor implements IMessage {
    private int window, choice;
    public MessageProgrammableArmor() { }
    public MessageProgrammableArmor(int window, int choice) { this.window = window; this.choice = choice; }
    @Override public void fromBytes(ByteBuf buffer) { window = buffer.readInt(); choice = buffer.readInt(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(window); buffer.writeInt(choice); }
    public static final class Handler implements IMessageHandler<MessageProgrammableArmor, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableArmor message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message.window, message.choice));
            return null;
        }
    }
    public static boolean apply(EntityPlayerMP player, int window, int choice) {
        if (!(player.openContainer instanceof ContainerProgrammableArmor)
                || player.openContainer.windowId != window || !ItemProgrammableArmor.validTexture(choice)) return false;
        ContainerProgrammableArmor container = (ContainerProgrammableArmor)player.openContainer;
        if (!container.canInteractWith(player)) return false;
        ItemProgrammableArmor.setTexture(container.armor, choice);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        // Window 0 only synchronizes hotbar slots while another container is open.
        // Address the inventory directly so offhand armor updates in its picker too.
        player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,
                container.hand == net.minecraft.util.EnumHand.MAIN_HAND ? player.inventory.currentItem : 40,
                container.armor));
        return true;
    }
}
