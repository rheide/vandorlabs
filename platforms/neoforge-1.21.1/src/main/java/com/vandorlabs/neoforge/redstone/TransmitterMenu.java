package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.neoforge.NativeHeldToolMenu;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.*;

/** Transmitter settings reuse the same held-object/session and read-only preview as other tools. */
public final class TransmitterMenu extends NativeHeldToolMenu {
    public TransmitterMenu(int id,Inventory inventory,InteractionHand hand){super(VandorLabsBootstrap.TRANSMITTER_MENU.get(),id,inventory,hand,NativeTransmitterItem::valid);}
    public boolean configure(Player player,ChannelList channels){if(!stillValid(player))return false;NativeTransmitterItem.configure(heldStack(),channels);synchronizeTool(player);return true;}
}
