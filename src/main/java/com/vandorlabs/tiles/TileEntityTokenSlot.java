package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTokenSlot;
import com.vandorlabs.items.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.*;

/** One physical token, with a non-consuming list of allowed designs. */
public final class TileEntityTokenSlot extends TileEntityRedstoneChannel implements IInventory {
    private ItemStack token=ItemStack.EMPTY;
    private boolean chip;
    public boolean any=true;
    public int allowed=127,behavior;
    private long pulseUntil;
    public TileEntityTokenSlot(){}
    public TileEntityTokenSlot(boolean chip){this.chip=chip;}
    public boolean chip(){return world!=null&&world.getBlockState(pos).getBlock() instanceof BlockTokenSlot?((BlockTokenSlot)world.getBlockState(pos).getBlock()).chip:chip;}
    public boolean accepts(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==(chip()?ModItems.DATA_CHIP:ModItems.DATA_CARD)&&stack.getMetadata()>=0&&stack.getMetadata()<7;}
    public boolean matches(){return accepts(token)&&(any||(allowed&(1<<token.getMetadata()))!=0);}
    @Override public boolean isChannelLatch(){return false;}
    @Override public int getOutputLevel(){return matches()&&(behavior==0||world!=null&&world.getTotalWorldTime()<pulseUntil)?15:0;}
    @Override public boolean hasLocalRedstoneSignal(){return getOutputLevel()>0;}
    @Override public boolean hasLocalRedstoneSignal(int channel){return hasLocalRedstoneSignal();}
    @Override public int localSignalLevel(int channel){return getOutputLevel();}
    // Generic switch or copy callbacks cannot manufacture a token signal.
    @Override public void setLocalOn(boolean ignored){refresh();}
    public void refresh(){
        if(world==null||world.isRemote)return;
        boolean powered=getOutputLevel()>0;super.setLocalOn(powered);
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockTokenSlot)((BlockTokenSlot)state.getBlock()).applyLinkedState(world,pos,state,powered);
    }
    public boolean expire(long now){if(behavior!=1||pulseUntil<=0||now<pulseUntil)return false;pulseUntil=0;changed(false);return true;}
    public void scheduledTick(){
        if(world==null||world.isRemote)return;long now=world.getTotalWorldTime();expire(now);refresh();
        // Minecraft coalesces scheduled updates at a position. An earlier pulse
        // may fire before a replacement token's deadline, so rearm the remainder.
        if(behavior==1&&pulseUntil>now)world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),(int)Math.min(20,pulseUntil-now));
    }
    private void changed(boolean inserted){
        if(world!=null&&!world.isRemote){
            if(inserted&&matches()&&behavior==1){pulseUntil=world.getTotalWorldTime()+20;world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),20);}
            if(token.isEmpty())pulseUntil=0;
            refresh();world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);
        }
        markDirty();
    }
    public void configure(boolean any,int allowed,int behavior){
        this.any=any;this.allowed=allowed&127;this.behavior=behavior==1?1:0;pulseUntil=0;changed(false);
    }
    @Override public void onLoad(){super.onLoad();if(world!=null&&!world.isRemote){pulseUntil=Math.min(pulseUntil,world.getTotalWorldTime()+20);scheduledTick();}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("ChipSlot",chip());tag.setBoolean("AnyToken",any);tag.setInteger("AllowedTokens",allowed);tag.setInteger("TokenBehavior",behavior);tag.setLong("TokenPulseUntil",pulseUntil);if(!token.isEmpty())tag.setTag("InsertedToken",token.writeToNBT(new NBTTagCompound()));return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);chip=tag.getBoolean("ChipSlot");any=!tag.hasKey("AnyToken")||tag.getBoolean("AnyToken");allowed=tag.hasKey("AllowedTokens")?tag.getInteger("AllowedTokens")&127:127;behavior=tag.getInteger("TokenBehavior")==1?1:0;pulseUntil=tag.getLong("TokenPulseUntil");token=tag.hasKey("InsertedToken",10)?new ItemStack(tag.getCompoundTag("InsertedToken")):ItemStack.EMPTY;if(!token.isEmpty())token.setCount(1);}
    @Override public int getSizeInventory(){return 1;}
    @Override public boolean isEmpty(){return token.isEmpty();}
    @Override public ItemStack getStackInSlot(int slot){return slot==0?token:ItemStack.EMPTY;}
    @Override public ItemStack decrStackSize(int slot,int count){if(slot!=0||count<=0)return ItemStack.EMPTY;ItemStack result=token;token=ItemStack.EMPTY;changed(false);return result;}
    @Override public ItemStack removeStackFromSlot(int slot){return decrStackSize(slot,1);}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){if(slot!=0||!stack.isEmpty()&&!accepts(stack))return;boolean inserted=!stack.isEmpty()&&(token.isEmpty()||!ItemStack.areItemStacksEqual(token,stack));token=stack.copy();if(!token.isEmpty())token.setCount(1);changed(inserted);}
    @Override public int getInventoryStackLimit(){return 1;}
    @Override public boolean isUsableByPlayer(EntityPlayer player){return world!=null&&!isInvalid()&&world.getTileEntity(pos)==this&&player.world==world&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos);}
    @Override public void openInventory(EntityPlayer player){}
    @Override public void closeInventory(EntityPlayer player){}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return slot==0&&accepts(stack);}
    @Override public int getField(int id){return id==0?allowed:id==1?any?1:0:behavior;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 3;}
    @Override public void clear(){token=ItemStack.EMPTY;changed(false);}
    @Override public String getName(){return "tile.vandorlabs."+(chip()?"data_chip_slot":"data_card_slot")+".name";}
    @Override public boolean hasCustomName(){return false;}
    @Override public ITextComponent getDisplayName(){return new TextComponentTranslation(getName());}
}
