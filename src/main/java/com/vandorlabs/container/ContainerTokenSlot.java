package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
/** Configuration only. The shared credential picker samples inventory without moving items. */
public class ContainerTokenSlot extends Container {
 public final TileEntityTokenSlot tile;
 public ContainerTokenSlot(InventoryPlayer player,TileEntityTokenSlot tile){this.tile=tile;}
 public boolean carriedMachine(){return false;}
 public void configureMachine(EntityPlayerMP actor,com.vandorlabs.navigation.NavigationScreenSettings settings,int[] surfaces,int mask,int behavior,com.vandorlabs.redstone.ChannelList channels,com.vandorlabs.drives.DriveSettings drive){if(tile instanceof com.vandorlabs.tiles.TileEntityConstructor){com.vandorlabs.tiles.TileEntityConstructor machine=(com.vandorlabs.tiles.TileEntityConstructor)tile;if(drive!=null)machine.configureDrive(drive);machine.configureMachine(settings,surfaces,false,mask,behavior,channels);}}
 @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player);}
 @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){return ItemStack.EMPTY;}
}
