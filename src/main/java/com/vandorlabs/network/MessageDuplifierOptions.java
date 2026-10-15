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
    private long[] mask;private boolean valid;
    private boolean connected;

    public MessageDuplifierOptions() { }
    public MessageDuplifierOptions(long mask, boolean connected) {
        this(DuplifierApplyOptions.legacyWords(mask),connected);
    }
    public MessageDuplifierOptions(long[] mask,boolean connected){this.mask=mask.clone();this.connected=connected;this.valid=DuplifierApplyOptions.valid(mask);}

    @Override public void fromBytes(ByteBuf buf) {try{long low=buf.readLong();connected=buf.readBoolean();if(!buf.isReadable())mask=DuplifierApplyOptions.legacyWords(low);else{int count=buf.readUnsignedByte();if(count<1 || count>(DuplifierApplyOptions.OPTIONS.length+63)/64 || buf.readableBytes()!=(count-1)*8)return;mask=new long[count];mask[0]=low;for(int i=1;i<count;i++)mask[i]=buf.readLong();}valid=DuplifierApplyOptions.valid(mask);}catch(Exception malformed){valid=false;}}
    @Override public void toBytes(ByteBuf buf) {buf.writeLong(mask[0]);buf.writeBoolean(connected);buf.writeByte(mask.length);for(int i=1;i<mask.length;i++)buf.writeLong(mask[i]);}

    public static final class Handler implements IMessageHandler<MessageDuplifierOptions, IMessage> {
        @Override public IMessage onMessage(MessageDuplifierOptions message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!message.valid
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
