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
    private boolean connected,ignoreDirectionality,undo;
    public static MessageDuplifierOptions undo(){MessageDuplifierOptions message=new MessageDuplifierOptions();message.undo=true;message.valid=true;return message;}

    public MessageDuplifierOptions() { }
    public MessageDuplifierOptions(long mask, boolean connected) {
        this(DuplifierApplyOptions.legacyWords(mask),connected);
    }
    public MessageDuplifierOptions(long[] mask,boolean connected){this(mask,connected,false);}
    public MessageDuplifierOptions(long[] mask,boolean connected,boolean ignoreDirectionality){this.mask=mask.clone();this.connected=connected;this.ignoreDirectionality=ignoreDirectionality;this.valid=DuplifierApplyOptions.valid(mask);}

    @Override public void fromBytes(ByteBuf buf) {
        valid=false;undo=false;ignoreDirectionality=false;mask=null;
        try{
            long low=buf.readLong();connected=buf.readBoolean();
            if(!buf.isReadable())mask=DuplifierApplyOptions.legacyWords(low);
            else{
                int count=buf.readUnsignedByte();
                if(count==0&&low==0&&!connected&&!buf.isReadable()){undo=true;valid=true;return;}
                if(count<1 || count>(DuplifierApplyOptions.OPTIONS.length+63)/64)return;
                int bytes=(count-1)*8;
                if(buf.readableBytes()!=bytes && buf.readableBytes()!=bytes+1)return;
                mask=new long[count];mask[0]=low;
                for(int i=1;i<count;i++)mask[i]=buf.readLong();
                if(buf.isReadable()){int flag=buf.readUnsignedByte();if(flag>1)return;ignoreDirectionality=flag==1;}
            }
            valid=DuplifierApplyOptions.valid(mask);
        }catch(Exception malformed){valid=false;}
    }
    @Override public void toBytes(ByteBuf buf) {if(undo){buf.writeLong(0);buf.writeBoolean(false);buf.writeByte(0);return;}buf.writeLong(mask[0]);buf.writeBoolean(connected);buf.writeByte(mask.length);for(int i=1;i<mask.length;i++)buf.writeLong(mask[i]);buf.writeBoolean(ignoreDirectionality);}

    public static final class Handler implements IMessageHandler<MessageDuplifierOptions, IMessage> {
        @Override public IMessage onMessage(MessageDuplifierOptions message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!message.valid
                        || !(player.openContainer instanceof ContainerDuplifier)
                        || !player.openContainer.canInteractWith(player)) return;
                if(message.undo){boolean restored=com.vandorlabs.items.DuplifierUndo.undo(player);player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(restored?"Duplifier: previous settings restored":"Cannot undo: no previous operation, unavailable blocks or settings changed"),true);return;}
                ItemStack tool = player.getHeldItemMainhand();
                DuplifierApplyOptions.setMask(tool, message.mask);
                DuplifierApplyOptions.setConnected(tool, message.connected);
                DuplifierApplyOptions.setIgnoreDirectionality(tool,message.ignoreDirectionality);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
