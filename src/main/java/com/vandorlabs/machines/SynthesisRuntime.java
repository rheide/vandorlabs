package com.vandorlabs.machines;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.rack.ModuleBatteryData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.*;
import net.minecraftforge.energy.IEnergyStorage;

/** A powered recipe consumes one matter block; committed RF is work, never an internal battery. */
public final class SynthesisRuntime {
    public static final String[] DESIGNS={"synthesis_tower","assembly_press","enclosed_gantry"};
    public static final int RF_PER_TICK=40,REQUIRED_RF=8000;
    private final TileEntityConstructor owner;
    private int selected,building,work;private boolean active;private long transferTick=Long.MIN_VALUE;private int received,worked;
    public boolean connected;
    public SynthesisRuntime(TileEntityConstructor owner){this.owner=owner;}
    public final ItemStackHandler inventory=new ItemStackHandler(3){
        @Override public boolean isItemValid(int slot,ItemStack stack){return slot==0?matter(stack):slot==2&&ModuleBatteryData.isBattery(stack);}
        @Override public int getSlotLimit(int slot){return slot==2?1:64;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?super.insertItem(slot,stack,simulate):stack;}
        @Override protected void onContentsChanged(int slot){owner.synthesisChanged();}
    };
    public static boolean matter(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));}
    public static ItemStack result(int design){return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+DESIGNS[Math.max(0,Math.min(2,design))]+"_core")));}
    public int selected(){return selected;}public int building(){return active||!inventory.getStackInSlot(1).isEmpty()?building:selected;}public boolean active(){return active;}public float progress(){return active?(float)work/REQUIRED_RF:inventory.getStackInSlot(1).isEmpty()?0:1;}
    public void select(int design){if(design>=0&&design<DESIGNS.length&&selected!=design){selected=design;owner.synthesisChanged();}}
    private boolean outputFits(){ItemStack output=inventory.getStackInSlot(1),result=result(building);return output.isEmpty()||ItemStack.areItemsEqual(output,result)&&ItemStack.areItemStackTagsEqual(output,result)&&output.getCount()<output.getMaxStackSize();}
    private void start(){if(!owner.synthesisEnabled()||active||!inventory.getStackInSlot(1).isEmpty()||!matter(inventory.getStackInSlot(0)))return;building=selected;inventory.extractItem(0,1,false);active=true;work=0;owner.synthesisChanged();}
    private void resetBudget(){long tick=owner.getWorld().getTotalWorldTime();if(tick!=transferTick){transferTick=tick;received=0;worked=0;}}
    private boolean installed(){return owner.getWorld()!=null&&!owner.getWorld().isRemote&&owner.getWorld().isBlockLoaded(owner.getPos())&&owner.getWorld().getTileEntity(owner.getPos())==owner&&owner.formed()&&!com.vandorlabs.gear.ArmTransactions.locked(owner.getWorld(),owner.getPos());}
    private int accept(int amount,boolean simulate){if(!installed()||!owner.synthesis())return 0;resetBudget();int available=Math.max(0,Math.min(amount,RF_PER_TICK-received));if(!simulate)start();boolean canStart=owner.synthesisEnabled()&&!active&&inventory.getStackInSlot(1).isEmpty()&&matter(inventory.getStackInSlot(0));int recipe=(owner.synthesisEnabled()&&active&&outputFits()||simulate&&canStart)?Math.min(Math.min(available,RF_PER_TICK-worked),REQUIRED_RF-(active?work:0)):0;int charge=ModuleBatteryData.receive(inventory.getStackInSlot(2),available-recipe,true);if(!simulate){received+=recipe+charge;worked+=recipe;work+=recipe;ModuleBatteryData.receive(inventory.getStackInSlot(2),charge,false);if(recipe+charge>0)owner.synthesisChanged();finish();}return recipe+charge;}
    private void finish(){if(active&&work>=REQUIRED_RF&&outputFits()){ItemStack out=inventory.getStackInSlot(1);if(out.isEmpty())inventory.setStackInSlot(1,result(building));else{out.grow(1);inventory.setStackInSlot(1,out);}active=false;work=0;owner.synthesisChanged();}}
    public final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int max,boolean simulate){return accept(Math.max(0,max),simulate);}public int extractEnergy(int max,boolean simulate){return 0;}public int getEnergyStored(){return work+ModuleBatteryData.energy(inventory.getStackInSlot(2));}public int getMaxEnergyStored(){return REQUIRED_RF+(ModuleBatteryData.isBattery(inventory.getStackInSlot(2))?ModuleBatteryData.CAPACITY:0);}public boolean canExtract(){return false;}public boolean canReceive(){return true;}};
    public void tick(){if(!installed())return;start();resetBudget();if(owner.synthesisEnabled()&&active&&outputFits()){int used=ModuleBatteryData.extract(inventory.getStackInSlot(2),Math.min(RF_PER_TICK-worked,REQUIRED_RF-work),false);if(used>0){worked+=used;work+=used;owner.synthesisChanged();finish();}}if(owner.getWorld().getTotalWorldTime()%20==0){boolean found=false;BlockPos port=owner.synthesisPowerPort();for(EnumFacing side:new EnumFacing[]{owner.facing().getOpposite(),EnumFacing.DOWN}){BlockPos at=port.offset(side);if(!owner.getWorld().isBlockLoaded(at))continue;net.minecraft.tileentity.TileEntity tile=owner.getWorld().getTileEntity(at);if(tile!=null&&tile.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side.getOpposite())){IEnergyStorage storage=tile.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side.getOpposite());found|=storage!=null&&storage.canExtract();}}if(connected!=found){connected=found;owner.synthesisChanged();}}}
    public void write(NBTTagCompound tag){NBTTagCompound data=new NBTTagCompound();data.setTag("Inventory",inventory.serializeNBT());data.setInteger("Selected",selected);data.setInteger("Building",building);data.setInteger("Work",work);data.setBoolean("Active",active);data.setBoolean("Connected",connected);tag.setTag("Synthesis",data);}
    public void read(NBTTagCompound tag){NBTTagCompound data=tag.getCompoundTag("Synthesis");NBTTagCompound items=data.getCompoundTag("Inventory").copy();items.setInteger("Size",3);inventory.deserializeNBT(items);selected=Math.max(0,Math.min(2,data.getInteger("Selected")));building=Math.max(0,Math.min(2,data.getInteger("Building")));work=Math.max(0,Math.min(REQUIRED_RF,data.getInteger("Work")));active=data.getBoolean("Active");connected=data.getBoolean("Connected");}
    public void drop(){for(int slot=0;slot<3;slot++){ItemStack stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,stack);inventory.setStackInSlot(slot,ItemStack.EMPTY);}if(active){net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"))));active=false;work=0;}}
    public IItemHandler port(boolean input){return new IItemHandler(){public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){if(slot!=0)return ItemStack.EMPTY;return inventory.getStackInSlot(input?0:1);}public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0&&input&&installed()?inventory.insertItem(0,stack,simulate):stack;}public ItemStack extractItem(int slot,int amount,boolean simulate){return slot!=0||input||!installed()?ItemStack.EMPTY:inventory.extractItem(1,amount,simulate);}public int getSlotLimit(int slot){return 64;}};}
}
