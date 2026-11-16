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
    public static final int RF_PER_TICK=SynthesisRecipePolicy.RF_PER_TICK,REQUIRED_RF=SynthesisRecipePolicy.REQUIRED_RF;
    private final TileEntityConstructor owner;
    private String loadedSelected="",loadedBuilding="";private boolean unknownRecipe;
    private net.minecraft.item.ItemStack cachedChip=ItemStack.EMPTY;private String cachedMachine="";private java.util.List<String> cachedRecipes=java.util.Collections.emptyList();
    private int selected,building,work;private boolean active;private long transferTick=Long.MIN_VALUE;private int received,worked;
    public boolean connected;
    private boolean working,exporting;private long lastWorked=Long.MIN_VALUE,nextExport;
    public boolean working(){return working;}public void visualWorking(boolean value){working=value;}public boolean exporting(){return exporting;}
    private void working(boolean value){if(working!=value){working=value;owner.synthesisChanged();}}
    private void spent(int rf){if(rf>0){lastWorked=owner.getWorld().getTotalWorldTime();working(true);}}
    public SynthesisRuntime(TileEntityConstructor owner){this.owner=owner;}
    public final ItemStackHandler inventory=new ItemStackHandler(6){
        @Override public boolean isItemValid(int slot,ItemStack stack){return slot==0?input(stack):slot>=2&&ModuleBatteryData.valid(stack);}
        @Override public int getSlotLimit(int slot){return slot>=2?1:64;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?super.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int count,boolean simulate){return slot==1&&exporting?ItemStack.EMPTY:super.extractItem(slot,count,simulate);}
        @Override protected void onContentsChanged(int slot){owner.synthesisSlotChanged(slot);}
    };
    public static boolean matter(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));}
    public static ItemStack result(int design){return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+DESIGNS[Math.max(0,Math.min(2,design))]+"_core")));}
    public String machine(){return owner.core()==null?"synthesis_tower":owner.core().design;}
    public java.util.List<String> recipes(){String machine=machine();ItemStack chip=owner.getStackInSlot(0);if(!machine.equals(cachedMachine)||!ItemStack.areItemStacksEqual(chip,cachedChip)){cachedMachine=machine;cachedChip=chip.copy();cachedRecipes=BuilderRecipeCatalog.available(machine,com.vandorlabs.programmer.CatalogChipData.designs(chip,machine));}return cachedRecipes;}
    private void resolveSavedRecipes(){if(owner.core()==null)return;java.util.List<String> ids=known();if(!loadedSelected.isEmpty()){int index=ids.indexOf(loadedSelected);if(index<0){unknownRecipe=true;return;}selected=index;loadedSelected="";}if(!loadedBuilding.isEmpty()){int index=ids.indexOf(loadedBuilding);if(index<0){unknownRecipe=true;return;}building=index;loadedBuilding="";}}
    public boolean input(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+BuilderRecipeCatalog.input(machine())));}
    private java.util.List<String> known(){return BuilderRecipeCatalog.known(machine());}
    public String recipe(int index){java.util.List<String> all=known();return all.isEmpty()?"":all.get(index>=0&&index<all.size()?index:0);}
    public int index(String id){return known().indexOf(id);}
    public ItemStack output(int index){if(index<0||index>=known().size())return ItemStack.EMPTY;return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+recipe(index))));}
    private boolean permitted(){return selected>=0&&selected<known().size()&&recipes().contains(recipe(selected));}
    public int selected(){resolveSavedRecipes();return selected;}public int building(){resolveSavedRecipes();return active||!inventory.getStackInSlot(1).isEmpty()?building:selected;}public boolean active(){return active;}public float progress(){return active?(float)work/REQUIRED_RF:inventory.getStackInSlot(1).isEmpty()?0:1;}
    public void select(int design){if(design>=0&&design<known().size()&&recipes().contains(recipe(design))&&selected!=design){selected=design;owner.synthesisChanged();}}
    private boolean outputFits(){if(unknownRecipe||building<0||building>=known().size())return false;ItemStack output=inventory.getStackInSlot(1),result=output(building);return output.isEmpty()||ItemStack.areItemsEqual(output,result)&&ItemStack.areItemStackTagsEqual(output,result)&&output.getCount()<output.getMaxStackSize();}
    private void start(){resolveSavedRecipes();if(unknownRecipe||!owner.synthesisEnabled()||active||!inventory.getStackInSlot(1).isEmpty()||!input(inventory.getStackInSlot(0))||!permitted())return;building=selected;inventory.extractItem(0,1,false);active=true;work=0;lastWorked=Long.MIN_VALUE;owner.synthesisChanged();}
    private void resetBudget(){long tick=owner.getWorld().getTotalWorldTime();if(tick!=transferTick){transferTick=tick;received=0;worked=0;}}
    private boolean installed(){return owner.getWorld()!=null&&!owner.getWorld().isRemote&&owner.getWorld().isBlockLoaded(owner.getPos())&&owner.getWorld().getTileEntity(owner.getPos())==owner&&owner.formed()&&!com.vandorlabs.gear.ArmTransactions.locked(owner.getWorld(),owner.getPos());}
    private int accept(int amount,boolean simulate){if(!installed()||!owner.builder())return 0;resetBudget();int available=Math.max(0,Math.min(amount,RF_PER_TICK-received));if(!simulate)start();boolean canStart=owner.synthesisEnabled()&&!active&&inventory.getStackInSlot(1).isEmpty()&&input(inventory.getStackInSlot(0))&&permitted();int recipe=(owner.synthesisEnabled()&&active&&outputFits()||simulate&&canStart)?Math.min(Math.min(available,RF_PER_TICK-worked),REQUIRED_RF-(active?work:0)):0;int charge=owner.receiveBatteryEnergy(available-recipe,true);if(!simulate){received+=recipe+charge;worked+=recipe;work+=recipe;spent(recipe);owner.receiveBatteryEnergy(charge,false);if(recipe+charge>0)owner.synthesisChanged();finish();}return recipe+charge;}
    private void finish(){if(active&&work>=REQUIRED_RF&&outputFits()){ItemStack out=inventory.getStackInSlot(1);if(out.isEmpty())inventory.setStackInSlot(1,output(building));else{out.grow(1);inventory.setStackInSlot(1,out);}active=false;work=0;working(false);nextExport=0;owner.synthesisChanged();}}
    public final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int max,boolean simulate){return accept(Math.max(0,max),simulate);}public int extractEnergy(int max,boolean simulate){return 0;}public int getEnergyStored(){return work+owner.batteryEnergy();}public int getMaxEnergyStored(){return REQUIRED_RF+owner.batteryCapacity();}public boolean canExtract(){return false;}public boolean canReceive(){return true;}};
    public void tick(){if(!installed()){working(false);return;}export();start();resetBudget();if(owner.synthesisEnabled()&&active&&outputFits()){int used=owner.extractBatteryEnergy(Math.min(RF_PER_TICK-worked,REQUIRED_RF-work),false);if(used>0){worked+=used;work+=used;spent(used);owner.synthesisChanged();finish();}}working(active&&owner.synthesisEnabled()&&lastWorked>=owner.getWorld().getTotalWorldTime()-1);if(owner.getWorld().getTotalWorldTime()%5==0)owner.refreshSynthesisConnections();}

    /** Ordinary inventory insertion: the target owns the accepted count; retain its remainder once. */
    private void export(){
        long now=owner.getWorld().getTotalWorldTime();if(exporting||now<nextExport||!owner.synthesisReady()||inventory.getStackInSlot(1).isEmpty())return;
        nextExport=now+8;exporting=true;
        try{for(EnumFacing side:new EnumFacing[]{owner.facing().rotateYCCW(),EnumFacing.DOWN}){
            if(!installed()||inventory.getStackInSlot(1).isEmpty())break;
            BlockPos at=owner.synthesisItemPort().offset(side);if(!owner.getWorld().isBlockLoaded(at)||com.vandorlabs.gear.ArmTransactions.locked(owner.getWorld(),at))continue;
            net.minecraft.tileentity.TileEntity tile=owner.getWorld().getTileEntity(at);if(tile==null)continue;
            IItemHandler target=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side.getOpposite());if(target==null)continue;
            ItemStack before=inventory.getStackInSlot(1);ItemStack remainder=ItemHandlerHelper.insertItemStacked(target,before.copy(),false);
            inventory.setStackInSlot(1,remainder);if(remainder.getCount()!=before.getCount())nextExport=now+1;
        }}finally{exporting=false;}
    }
    public void write(NBTTagCompound tag){NBTTagCompound data=new NBTTagCompound();data.setTag("Inventory",inventory.serializeNBT());data.setInteger("Selected",selected);data.setString("SelectedRecipe",loadedSelected.isEmpty()?recipe(selected):loadedSelected);data.setInteger("Building",building);data.setString("BuildingRecipe",loadedBuilding.isEmpty()?recipe(building):loadedBuilding);data.setInteger("Work",work);data.setBoolean("Active",active);data.setBoolean("Connected",connected);tag.setTag("Synthesis",data);}
    public void read(NBTTagCompound tag){working=false;lastWorked=Long.MIN_VALUE;nextExport=0;NBTTagCompound data=tag.getCompoundTag("Synthesis");NBTTagCompound items=data.getCompoundTag("Inventory").copy();items.setInteger("Size",6);inventory.deserializeNBT(items);selected=Math.max(0,Math.min(255,data.getInteger("Selected")));building=Math.max(0,Math.min(255,data.getInteger("Building")));work=Math.max(0,Math.min(REQUIRED_RF,data.getInteger("Work")));active=data.getBoolean("Active");connected=data.getBoolean("Connected");loadedSelected=data.getString("SelectedRecipe");loadedBuilding=data.getString("BuildingRecipe");unknownRecipe=false;cachedMachine="";}
    public void drop(){for(int slot=0;slot<inventory.getSlots();slot++){ItemStack stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,stack);inventory.setStackInSlot(slot,ItemStack.EMPTY);}if(active){net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+BuilderRecipeCatalog.input(machine())))));active=false;work=0;}}
    public IItemHandler port(boolean input){return new IItemHandler(){public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){if(slot!=0)return ItemStack.EMPTY;return inventory.getStackInSlot(input?0:1);}public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0&&input&&installed()?inventory.insertItem(0,stack,simulate):stack;}public ItemStack extractItem(int slot,int amount,boolean simulate){return slot!=0||input||!installed()?ItemStack.EMPTY:inventory.extractItem(1,amount,simulate);}public int getSlotLimit(int slot){return 64;}};}
}
