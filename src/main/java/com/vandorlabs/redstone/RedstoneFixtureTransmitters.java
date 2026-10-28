package com.vandorlabs.redstone;

import com.vandorlabs.items.*;
import net.minecraft.item.ItemStack;

/** Explicit installed hardware for legacy live fixtures that previously used unlimited channels. */
public final class RedstoneFixtureTransmitters {
    private RedstoneFixtureTransmitters(){}
    public static void equip(RedstoneChannelMember source){equip(source,source.getRedstoneChannels());}
    public static void equip(RedstoneChannelMember source,ChannelList channels){ItemStack module=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,3);ItemRedstoneTransmitter.configure(module,channels);TransmitterSlots.set(source,module);}
}
