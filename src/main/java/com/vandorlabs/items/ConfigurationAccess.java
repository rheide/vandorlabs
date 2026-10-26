package com.vandorlabs.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

/** Shared server-side permission for configuration packets. */
public final class ConfigurationAccess {
    private ConfigurationAccess() { }

    /** Public block dialogs still require the normal reach and edit permissions. */
    public static boolean canEditDialog(EntityPlayer player,net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos){
        return !com.vandorlabs.gear.ArmTransactions.locked(world,pos)&&player!=null&&!player.isDead&&!player.isSpectator()&&player.world==world
                && world.isBlockLoaded(pos)&&(player.getDistanceSq(pos)<=64||MovingDeviceConfiguration.nearOwnedPart(player,world.getTileEntity(pos)))
                && player.canPlayerEdit(pos,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand())
                && world.isBlockModifiable(player,pos);
    }

    public static boolean canConfigure(EntityPlayer player) {
        return player.capabilities.isCreativeMode
                || player.getHeldItem(EnumHand.MAIN_HAND).getItem() == ModItems.CONFIGURIZER;
    }
}
