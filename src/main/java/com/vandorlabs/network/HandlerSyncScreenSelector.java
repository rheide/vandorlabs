package com.vandorlabs.network;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: apply selector GUI edits to the tile entity. */
public class HandlerSyncScreenSelector implements IMessageHandler<MessageSyncScreenSelector, IMessage> {

    @Override
    public IMessage onMessage(MessageSyncScreenSelector message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        if (player == null || player.world == null || message.getPos() == null
                || message.getSelectedScreen() == null) {
            return null;
        }

        player.getServerWorld().addScheduledTask(() -> {
            if (!player.world.isBlockLoaded(message.getPos())) {
                return;
            }
            TileEntity raw = player.world.getTileEntity(message.getPos());
            if (!(raw instanceof TileEntityAnimatedScreenSelector)) {
                return;
            }
            TileEntityAnimatedScreenSelector te = (TileEntityAnimatedScreenSelector) raw;
            // The opener must still be in range, and the screen id must be a
            // real display block (never trust client strings for rendering).
            if (!te.isUsableByPlayer(player)) {
                return;
            }
            if (!ModBlocks.DISPLAY_SCREEN_IDS.contains(message.getSelectedScreen())) {
                return;
            }
            if (message.getRedstoneChannel() < 0) return;
            te.setSelectedScreen(message.getSelectedScreen());
            te.setRedstoneEnabled(message.isRedstoneEnabled());
            te.setDisplayMode(message.getDisplayMode());
            te.setFramed(message.isFramed());
            te.setAnimationSpeedIndex(message.getAnimationSpeedIndex());
            te.setInputPanel(message.getInputPanel());
            te.setSecondaryInputPanel(message.getSecondaryInputPanel());
            te.setRedstoneChannel(message.getRedstoneChannel());
            te.setHousingTexture(message.getHousingTexture());
            if (player.world.getBlockState(message.getPos()).getBlock()
                    == ModBlocks.PROGRAMMABLE_INPUT) {
                te.setSmallInput(message.isSmallInput());
            }
            player.world.notifyBlockUpdate(message.getPos(),
                    te.getWorld().getBlockState(message.getPos()),
                    te.getWorld().getBlockState(message.getPos()), 3);
        });

        return null;
    }
}
