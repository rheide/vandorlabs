package com.vandorlabs.navigation;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** One native cassette bay in the low front recess; no four-slot housing. */
public final class NavigationModuleSlot {
    public static final double Y=2.0/16,HEIGHT=3.5/16,WIDTH=12.0/16,FRONT=.058;
    private NavigationModuleSlot(){}
    public static MachineMountSurface surface(TileEntityNavigationTable tile){
        if(!tile.hasModuleBay())return null;Vec3d center=MachineMountSurface.rotate(new Vec3d(.5,Y+HEIGHT/2,FRONT),tile.moduleFacing(),1,1).add(new Vec3d(tile.getPos()));return new MachineMountSurface(center,tile.moduleFacing(),WIDTH,HEIGHT);
    }
    public static boolean activate(TileEntityNavigationTable tile,EntityPlayer player,EnumHand hand){
        if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)return false;MachineMountSurface mount=surface(tile);if(mount==null||!mount.hit(player))return false;ItemStack held=player.getHeldItem(hand);if(!held.isEmpty()&&!NavigationModuleData.item(held))return false;
        return use(tile,player,hand);
    }
    public static boolean activate(TileEntityNavigationTable tile,EntityPlayer player,EnumHand hand,Vec3d start,Vec3d end){if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)return false;MachineMountSurface mount=surface(tile);if(mount==null||!mount.hit(start,end))return false;ItemStack held=player.getHeldItem(hand);if(!held.isEmpty()&&!NavigationModuleData.item(held))return false;return use(tile,player,hand);}
    private static boolean use(TileEntityNavigationTable tile,EntityPlayer player,EnumHand hand){ItemStack held=player.getHeldItem(hand);
        if(NavigationVehicle.physicalWorld(tile.getWorld()).isRemote)return true;
        if(player.isSpectator()||(NavigationVehicle.carried(tile)?!NavigationVehicle.edit(player,tile):!tile.nearDisplayMember(player)||!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,tile.getWorld(),tile.getPos()))||com.vandorlabs.gear.ArmTransactions.locked(tile.getWorld(),tile.getPos()))return true;
        TileEntityNavigationTable storage=tile.moduleStorage();if(storage==null)return true;
        if(held.isEmpty()){ItemStack removed=storage.takeModule();if(!removed.isEmpty()&&!player.inventory.addItemStackToInventory(removed))player.dropItem(removed,false);}
        else if(storage.storedModule().isEmpty()){ItemStack one=held.copy();one.setCount(1);storage.installModule(one);if(!player.capabilities.isCreativeMode)held.shrink(1);NavigationService.recordSources(tile.reader());}
        player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
    }
}
