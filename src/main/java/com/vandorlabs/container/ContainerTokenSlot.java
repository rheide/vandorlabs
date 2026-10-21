package com.vandorlabs.container;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
/** Non-consuming credential design filters; physical items are inserted on the block. */
public final class ContainerTokenSlot extends Container {
 public final TileEntityTokenSlot tile;
 public ContainerTokenSlot(InventoryPlayer player,TileEntityTokenSlot tile){
  this.tile=tile;InventoryBasic filters=new InventoryBasic("Accepted designs",false,7);
  for(int i=0;i<7;i++){filters.setInventorySlotContents(i,new ItemStack(tile.chip()?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.DATA_CARD,1,i));addSlotToContainer(new Slot(filters,i,12+i*40,102){@Override public boolean isItemValid(ItemStack item){return false;}@Override public boolean canTakeStack(EntityPlayer player){return false;}});}
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,73+col*18,130+row*18));
  for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,73+col*18,188));
 }
 @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player);}
 @Override public ItemStack slotClick(int id,int button,ClickType type,EntityPlayer player){
  if(!canInteractWith(player))return ItemStack.EMPTY;
  if(id>=0&&id<7){if(type!=ClickType.PICKUP)return ItemStack.EMPTY;ItemStack carried=player.inventory.getItemStack();int mask=tile.effectiveAllowed();if(!carried.isEmpty()){if(!tile.accepts(carried))return ItemStack.EMPTY;mask|=1<<carried.getMetadata();}else mask^=1<<id;tile.configure(false,mask,tile.behavior);return ItemStack.EMPTY;}
  return super.slotClick(id,button,type,player);
 }
 @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){return ItemStack.EMPTY;}
}
