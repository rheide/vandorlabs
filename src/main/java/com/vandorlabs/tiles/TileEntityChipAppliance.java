package com.vandorlabs.tiles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

/** Physical chip plus consumables (Printer) or one original document (Scanner). */
public final class TileEntityChipAppliance extends TileEntityTokenSlot {
    private final ItemStack[] inputs={ItemStack.EMPTY,ItemStack.EMPTY};
    private boolean scanner,busy;public long pressedUntil;public net.minecraft.world.storage.MapData mapPreview;
    public TileEntityChipAppliance(){super(true);}
    public TileEntityChipAppliance(boolean scanner){this();this.scanner=scanner;}
    public boolean scanner(){return world==null?scanner:world.getBlockState(pos).getBlock().getRegistryName().getResourcePath().equals("scanner");}
    @Override public boolean chip(){return true;}
    @Override public int getOutputLevel(){return 0;}
    @Override public int getSizeInventory(){return scanner()?2:3;}
    @Override public int getInventoryStackLimit(){return 64;}
    @Override public ItemStack getStackInSlot(int slot){return slot==0?super.getStackInSlot(0):slot>0&&slot<getSizeInventory()?inputs[slot-1]:ItemStack.EMPTY;}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){if(slot==0)return super.isItemValidForSlot(slot,stack);return !stack.isEmpty()&&slot>0&&slot<getSizeInventory()&&(scanner()?stack.getItem()==Items.FILLED_MAP||stack.getItem()==Items.WRITTEN_BOOK||stack.getItem()==Items.ENCHANTED_BOOK:slot==1?stack.getItem()==Items.DYE&&stack.getMetadata()==0:stack.getItem()==Items.PAPER);}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){if(slot==0){super.setInventorySlotContents(slot,stack);return;}if(slot<=0||slot>=getSizeInventory()||!stack.isEmpty()&&!isItemValidForSlot(slot,stack))return;inputs[slot-1]=stack.copy();if(!stack.isEmpty())inputs[slot-1].setCount(Math.min(scanner()?1:64,stack.getCount()));publish();}
    @Override public ItemStack decrStackSize(int slot,int count){if(slot==0)return super.decrStackSize(slot,count);if(slot<=0||slot>=getSizeInventory()||count<=0)return ItemStack.EMPTY;ItemStack out=inputs[slot-1].splitStack(count);publish();return out;}
    @Override public ItemStack removeStackFromSlot(int slot){return decrStackSize(slot,64);}
    @Override public void clear(){super.clear();for(int i=0;i<inputs.length;i++)inputs[i]=ItemStack.EMPTY;publish();}
    public void publish(){markDirty();if(world!=null&&!world.isRemote&&world.isBlockLoaded(pos))world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),2);}
    public void activate(EntityPlayer actor){
        if(busy||!authoritative()||!isUsableByPlayer(actor)||actor.getDistanceSqToCenter(pos)>64||com.vandorlabs.gear.ArmTransactions.locked(world,pos)||com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;
        busy=true;try{pressedUntil=world.getTotalWorldTime()+8;publish();com.vandorlabs.appliance.ChipApplianceService.activate(this,actor);}finally{busy=false;}
    }
    @Override public String getName(){return "tile.vandorlabs."+(scanner()?"scanner":"printer")+".name";}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setBoolean("Scanner",scanner());tag.setLong("AppliancePressedUntil",pressedUntil);for(int i=0;i<2;i++)if(!inputs[i].isEmpty())tag.setTag("ApplianceInput"+i,inputs[i].writeToNBT(new NBTTagCompound()));return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);scanner=tag.getBoolean("Scanner");mapPreview=null;if(tag.hasKey("ApplianceMapPreview",10)){mapPreview=new net.minecraft.world.storage.MapData("map_"+tag.getInteger("ApplianceMapId"));mapPreview.readFromNBT(tag.getCompoundTag("ApplianceMapPreview"));}pressedUntil=tag.getLong("AppliancePressedUntil");for(int i=0;i<2;i++)inputs[i]=tag.hasKey("ApplianceInput"+i,10)?new ItemStack(tag.getCompoundTag("ApplianceInput"+i)):ItemStack.EMPTY;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=super.getUpdateTag();if(scanner()&&world!=null&&getStackInSlot(1).getItem()==Items.FILLED_MAP){int id=getStackInSlot(1).getMetadata();net.minecraft.world.storage.MapData map=(net.minecraft.world.storage.MapData)world.loadData(net.minecraft.world.storage.MapData.class,"map_"+id);if(map!=null){tag.setInteger("ApplianceMapId",id);tag.setTag("ApplianceMapPreview",map.writeToNBT(new NBTTagCompound()));}}for(int i=0;i<2;i++)if(tag.hasKey("ApplianceInput"+i,10)){ItemStack icon=new ItemStack(tag.getCompoundTag("ApplianceInput"+i));icon.setTagCompound(null);tag.setTag("ApplianceInput"+i,icon.writeToNBT(new NBTTagCompound()));}return tag;}
}
