package com.vandorlabs.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

/** Shared server-side permission for configuration packets. */
public final class ConfigurationAccess {
    private ConfigurationAccess() { }

    public static boolean canConfigure(EntityPlayer player) {
        return player.capabilities.isCreativeMode
                || player.getHeldItem(EnumHand.MAIN_HAND).getItem() == ModItems.CONFIGURIZER;
    }
}
