package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.FaceTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Configures optional local-face overrides for the full block and slab. */
public final class MessageFaceTextures implements IMessage {
    private BlockPos pos;
    private boolean enabled;
    private int[] choices = new int[6];
    public MessageFaceTextures() { }
    public MessageFaceTextures(BlockPos pos, FaceTextures value) {
        this.pos = pos; enabled = value.enabled; choices = value.choices();
    }
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong()); enabled = buf.readBoolean();
        for (int i = 0; i < 6; i++) choices[i] = buf.readInt();
    }
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong()); buf.writeBoolean(enabled);
        for (int choice : choices) buf.writeInt(choice);
    }
    public static final class Handler implements IMessageHandler<MessageFaceTextures, IMessage> {
        public IMessage onMessage(MessageFaceTextures msg, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || !player.world.isBlockLoaded(msg.pos)
                        || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !(player.openContainer instanceof ContainerAnimatedScreenSelector)) return;
                net.minecraft.block.Block block = player.world.getBlockState(msg.pos).getBlock();
                if (block != ModBlocks.PROGRAMMABLE_BLOCK && block != ModBlocks.PROGRAMMABLE_SLAB && block != ModBlocks.PROGRAMMABLE_STAIRS) return;
                for (int choice : msg.choices)
                    if (choice < -1 || (choice!=-1 && !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice))) return;
                ContainerAnimatedScreenSelector container = (ContainerAnimatedScreenSelector) player.openContainer;
                TileEntity tile = player.world.getTileEntity(msg.pos);
                if (tile != container.getTileEntity() || !container.canInteractWith(player)) return;
                ((TileEntityAnimatedScreenSelector) tile).setFaceTextures(new FaceTextures(msg.enabled, msg.choices));
            });
            return null;
        }
    }
}
