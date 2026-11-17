package com.vandorlabs.container;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.items.ConfigurationAccess;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Real server-owned machine slots; output cannot accept client inventory. */
public final class ContainerSynthesisTower extends Container {
    public final TileEntityConstructor tile;
    public final int[] values=new int[7];
    private final WindowPropertyDeltas<IContainerListener> deltas=new WindowPropertyDeltas<>(
            (listener,id,value)->listener.sendWindowProperty(this,id,value));
    public ContainerSynthesisTower(InventoryPlayer player,TileEntityConstructor tile){
        this.tile=tile;
        // Window slot packets must never mutate the world-render snapshot or its insertion sequences.
        net.minecraftforge.items.IItemHandler slots=player.player.world.isRemote?new net.minecraftforge.items.ItemStackHandler(6):tile.synthesisInventory();
        IInventory chip=player.player.world.isRemote?new InventoryBasic("",false,1):tile;
        addSlotToContainer(new SlotItemHandler(slots,0,28,190));
        addSlotToContainer(new SlotItemHandler(slots,1,118,190){@Override public boolean isItemValid(ItemStack stack){return false;}});
        addSlotToContainer(new SlotItemHandler(slots,2,208,190));
        addSlotToContainer(new Slot(chip,0,298,190));
        for(int bay=1;bay<4;bay++)addSlotToContainer(new SlotItemHandler(slots,2+bay,208+bay*18,190));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,100+col*18,224+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,100+col*18,282));
        com.vandorlabs.ui.SynthesisDialog.Layout layout=new com.vandorlabs.ui.SynthesisDialog.Layout(false);for(int i=0;i<inventorySlots.size();i++){inventorySlots.get(i).xPos=layout.slotX(i);inventorySlots.get(i).yPos=layout.slotY(i);}
        sample();
    }
    private void sample(){values[0]=tile.selectedDesign();values[1]=Math.round(tile.buildProgress()*200);values[2]=tile.batteryEnergy();values[3]=tile.batteryCapacity();values[4]=tile.externalPowerConnected()?1:0;values[5]=tile.buildDesign();values[6]=tile.digitizing()?1:0;}
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();sample();for(IContainerListener listener:listeners)deltas.sendChanges(listener,values);}
    @Override public void removeListener(IContainerListener listener){super.removeListener(listener);deltas.removeListener(listener);}
    @Override public void updateProgressBar(int id,int data){WindowPropertyDeltas.receive(values,id,data);}
    @Override public boolean enchantItem(EntityPlayer player,int mode){if(mode<0||mode>1||player.openContainer!=this||!canInteractWith(player))return false;boolean changed=tile.setDigitizing(mode==1);if(!changed&&!player.world.isRemote)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Wait for processing and remove the input/output before changing mode."),true);return changed;}
    @Override public boolean canInteractWith(EntityPlayer player){return tile.builder()&&tile.formed()&&!tile.isInvalid()&&tile.getWorld()==player.world&&player.world.isBlockLoaded(tile.getPos())&&player.world.getTileEntity(tile.getPos())==tile&&tile.isUsableByPlayer(player)&&ConfigurationAccess.canEditDialog(player,player.world,tile.getPos());}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        if(index<0||index>=inventorySlots.size()||!canInteractWith(player))return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),before=stack.copy();
        if(index<7){if(!mergeItemStack(stack,7,43,true))return ItemStack.EMPTY;}
        else{
            boolean moved=false;
            for(int target:new int[]{0,2,4,5,6,3})if(inventorySlots.get(target).isItemValid(stack)&&mergeItemStack(stack,target,target+1,false)){moved=true;break;}
            if(!moved){if(index<34){if(!mergeItemStack(stack,34,43,false))return ItemStack.EMPTY;}else if(!mergeItemStack(stack,7,34,false))return ItemStack.EMPTY;}
        }
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return before;
    }
}
