package com.vandorlabs.network;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class MessageProgrammableTrigger implements IMessage {
    private ChannelList channels;
    private boolean invalidChannels;
    public MessageProgrammableTrigger withChannels(ChannelList channels) {
        if(channels==null)throw new IllegalArgumentException("Invalid channel list");
        this.channels=channels;this.channel=channels.first();invalidChannels=false;return this;
    }
    public ChannelList getRedstoneChannels() {
        return invalidChannels?null:channels!=null?channels:ChannelList.of(Math.max(0,channel));
    }

    private BlockPos pos;
    private int off, on, channel;

    public MessageProgrammableTrigger() { }
    public MessageProgrammableTrigger(BlockPos pos, int off, int on, int channel) {
        this.pos = pos; this.off = off; this.on = on; this.channel = channel;
    }
    @Override public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        off = buf.readInt(); on = buf.readInt(); channel = buf.readInt();

        channels=ChannelData.read(buf,channel);invalidChannels=channels==null;
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(off); buf.writeInt(on); buf.writeInt(channel);

        ChannelData.write(buf,channels!=null?channels:ChannelList.of(Math.max(0,channel)));
    }

    public static final class Handler
            implements IMessageHandler<MessageProgrammableTrigger, IMessage> {
        @Override public IMessage onMessage(MessageProgrammableTrigger msg,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || msg.getRedstoneChannels()==null || msg.channel < 0 || msg.off < 0 || msg.on < 0
                        || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(msg.off)
                        || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(msg.on)
                        || !player.world.isBlockLoaded(msg.pos)
                        || !ConfigurationAccess.canConfigure(player)
                        || player.world.getBlockState(msg.pos).getBlock()
                        != ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector))
                    return;
                TileEntity raw = player.world.getTileEntity(msg.pos);
                ContainerAnimatedScreenSelector container =
                        (ContainerAnimatedScreenSelector) player.openContainer;
                if (!(raw instanceof TileEntityProgrammableTrigger)
                        || container.getTileEntity() != raw
                        || !container.canInteractWith(player)) return;
                ((TileEntityProgrammableTrigger) raw).configure(msg.off, msg.on, msg.getRedstoneChannels());
            });
            return null;
        }
    }
}
