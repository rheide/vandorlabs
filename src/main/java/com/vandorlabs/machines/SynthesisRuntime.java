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

/** A recipe consumes one machine-specific matter input; committed RF is work, never an internal battery. */
public final class SynthesisRuntime {
    public static final String[] DESIGNS={"synthesis_tower","assembly_press","enclosed_gantry"};
    public static final int RF_PER_TICK=SynthesisRecipePolicy.RF_PER_TICK,REQUIRED_RF=SynthesisRecipePolicy.REQUIRED_RF;
    private final TileEntityConstructor owner;
    // An explicitly saved empty ID belongs to a non-builder Core. Recovery can
    // decode it before its block is restored, when machine() has no native context.
    private String loadedSelected,loadedBuilding;private boolean unknownRecipe;
    private ItemStack productionTemplate=ItemStack.EMPTY;
    private boolean digitizing;private ItemStack digitizeBlock=ItemStack.EMPTY,cachedBlock=ItemStack.EMPTY,scanChip=ItemStack.EMPTY;private byte[] digitizeChipHash=new byte[0];private NBTTagCompound failedData;
    public boolean digitizing(){return digitizing;}
    public boolean mode(boolean scan){if(!installed()||!owner.synthesis()||active||!inventory.getStackInSlot(0).isEmpty()||!inventory.getStackInSlot(1).isEmpty()||failedData!=null)return false;digitizing=scan;owner.synthesisChanged();return true;}
    public ItemStack scanningBlock(){return failedData!=null?ItemStack.EMPTY:active?digitizeBlock:inventory.getStackInSlot(0);}
    private net.minecraft.item.ItemStack cachedChip=ItemStack.EMPTY;private String cachedMachine="";private java.util.List<String> cachedRecipes=java.util.Collections.emptyList();
    private int selected,building,work,workCost=REQUIRED_RF;private boolean active;private long transferTick=Long.MIN_VALUE;private int received,worked;
    public boolean connected;
    private boolean working,exporting;private long lastWorked=Long.MIN_VALUE,nextExport;
    public boolean working(){return working;}public void visualWorking(boolean value){working=value;}public boolean exporting(){return exporting;}
    private void working(boolean value){if(working!=value){working=value;owner.synthesisChanged();}}
    private void spent(int rf){if(rf>0){lastWorked=owner.getWorld().getTotalWorldTime();working(true);}}
    public SynthesisRuntime(TileEntityConstructor owner){this.owner=owner;}
    public final ItemStackHandler inventory=new ItemStackHandler(6){
        @Override public boolean isItemValid(int slot,ItemStack stack){return failedData==null&&(slot==0?input(stack):slot>=2&&ModuleBatteryData.valid(stack));}
        @Override public int getSlotLimit(int slot){return slot>=2||slot==0&&digitizing?1:64;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return isItemValid(slot,stack)?super.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int count,boolean simulate){return failedData!=null||slot==1&&exporting?ItemStack.EMPTY:super.extractItem(slot,count,simulate);}
        @Override protected void onContentsChanged(int slot){owner.synthesisSlotChanged(slot);}
    };
    public static boolean matter(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));}
    public static ItemStack result(int design){return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+DESIGNS[Math.max(0,Math.min(2,design))]+"_core")));}
    public String machine(){return owner.core()==null?"synthesis_tower":owner.core().design;}
    public java.util.List<String> recipes(){String machine=machine();ItemStack chip=owner.getStackInSlot(0);if(!machine.equals(cachedMachine)||!ItemStack.areItemStacksEqual(chip,cachedChip)){cachedMachine=machine;cachedChip=chip.copy();cachedBlock=BlockRecipeData.read(chip);cachedRecipes=BuilderRecipeCatalog.available(machine,com.vandorlabs.programmer.CatalogChipData.designs(chip,machine));}return cachedRecipes;}
    private void resolveSavedRecipes(){if(owner.core()==null)return;java.util.List<String> ids=known();if(loadedSelected!=null&&!loadedSelected.isEmpty()){int index=ids.indexOf(loadedSelected);if(index<0){unknownRecipe=true;return;}selected=index;loadedSelected=null;}if(loadedBuilding!=null&&!loadedBuilding.isEmpty()){int index=ids.indexOf(loadedBuilding);if(index<0){unknownRecipe=true;return;}building=index;loadedBuilding=null;}}
    public boolean input(ItemStack stack){if(digitizing)return BlockRecipeData.eligible(stack);return !stack.isEmpty()&&stack.getItem()==Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+BuilderRecipeCatalog.input(machine())));}
    private java.util.List<String> known(){return BuilderRecipeCatalog.known(machine());}
    public String recipe(int index){java.util.List<String> all=known();return all.isEmpty()?"":all.get(index>=0&&index<all.size()?index:0);}
    public int index(String id){return known().indexOf(id);}
    public ItemStack output(int index){
        if(index<0||index>=known().size())return ItemStack.EMPTY;
        String id=recipe(index);
        if(!BlockRecipeData.ID.equals(id))return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+id)));
        if(active&&!digitizing&&index==building&&!productionTemplate.isEmpty())return productionTemplate.copy();
        recipes();
        return cachedBlock.copy();
    }
    private boolean permitted(){return selected>=0&&selected<known().size()&&recipes().contains(recipe(selected))&&!output(selected).isEmpty();}
    public int selected(){resolveSavedRecipes();return selected;}public int building(){resolveSavedRecipes();return active||!inventory.getStackInSlot(1).isEmpty()?building:selected;}public boolean active(){return active;}public float progress(){return active?(float)work/workCost:inventory.getStackInSlot(1).isEmpty()?0:1;}
    public void select(int design){resolveSavedRecipes();if(design>=0&&design<known().size()&&recipes().contains(recipe(design))&&selected!=design){selected=design;loadedSelected=null;owner.synthesisChanged();}}
    private boolean outputFits(){if(digitizing)return active&&BlockRecipeData.canDigitizeChip(owner.getStackInSlot(0))&&!digitizeBlock.isEmpty()&&ItemStack.areItemStacksEqual(digitizeBlock,inventory.getStackInSlot(0))&&ItemStack.areItemStacksEqual(scanChip,owner.getStackInSlot(0));if(unknownRecipe||building<0||building>=known().size())return false;
        if(active&&BlockRecipeData.ID.equals(recipe(building))&&productionTemplate.isEmpty()){
            recipes();
            if(cachedBlock.isEmpty())return false;
            productionTemplate=cachedBlock.copy();
            owner.synthesisChanged();
        }
        ItemStack output=inventory.getStackInSlot(1),result=output(building);return !result.isEmpty()&&(output.isEmpty()||ItemStack.areItemsEqual(output,result)&&ItemStack.areItemStackTagsEqual(output,result)&&output.getCount()<output.getMaxStackSize());}
    private void start(){if(failedData!=null)return;if(digitizing){if(!owner.synthesisEnabled()||active||!inventory.getStackInSlot(1).isEmpty()||inventory.getStackInSlot(0).getCount()!=1||!BlockRecipeData.eligible(inventory.getStackInSlot(0))||!BlockRecipeData.canDigitizeChip(owner.getStackInSlot(0)))return;digitizeBlock=inventory.getStackInSlot(0).copy();digitizeChipHash=chipHash(owner.getStackInSlot(0));scanChip=owner.getStackInSlot(0).copy();active=true;work=0;workCost=REQUIRED_RF;lastWorked=Long.MIN_VALUE;owner.synthesisChanged();return;}resolveSavedRecipes();if(unknownRecipe||!owner.synthesisEnabled()||active||!inventory.getStackInSlot(1).isEmpty()||!input(inventory.getStackInSlot(0))||!permitted())return;building=selected;loadedBuilding=null;workCost=selectedCost();productionTemplate=BlockRecipeData.ID.equals(recipe(building))?cachedBlock.copy():ItemStack.EMPTY;inventory.extractItem(0,1,false);active=true;work=0;lastWorked=Long.MIN_VALUE;owner.synthesisChanged();}
    private int selectedCost(){return digitizing?REQUIRED_RF:SynthesisRecipePolicy.requiredRf(machine(),recipe(selected));}
    private int rate(){return SynthesisRecipePolicy.rfPerTick(machine());}
    private void resetBudget(){long tick=owner.getWorld().getTotalWorldTime();if(tick!=transferTick){transferTick=tick;received=0;worked=0;}}
    private boolean installed(){return owner.getWorld()!=null&&!owner.getWorld().isRemote&&owner.getWorld().isBlockLoaded(owner.getPos())&&owner.getWorld().getTileEntity(owner.getPos())==owner&&owner.formed()&&!com.vandorlabs.gear.ArmTransactions.locked(owner.getWorld(),owner.getPos());}
    private int accept(int amount,boolean simulate){if(!installed()||!owner.builder())return 0;resetBudget();int available=Math.max(0,Math.min(amount,rate()-received));if(!simulate)start();boolean canStart=owner.synthesisEnabled()&&!active&&inventory.getStackInSlot(1).isEmpty()&&input(inventory.getStackInSlot(0))&&(digitizing?BlockRecipeData.canDigitizeChip(owner.getStackInSlot(0)):permitted());int recipe=(owner.synthesisEnabled()&&active&&outputFits()||simulate&&canStart)?Math.min(Math.min(available,rate()-worked),active?workCost-work:selectedCost()):0;int charge=owner.receiveBatteryEnergy(available-recipe,true);if(!simulate){received+=recipe+charge;worked+=recipe;work+=recipe;spent(recipe);owner.receiveBatteryEnergy(charge,false);if(recipe+charge>0)owner.synthesisChanged();finish();}return recipe+charge;}
    private void finish(){if(active&&work>=workCost&&outputFits()){if(digitizing){ItemStack written=owner.getStackInSlot(0).copy();BlockRecipeData.write(written,digitizeBlock);ItemStack input=inventory.extractItem(0,1,false);owner.setInventorySlotContents(0,written);if(!ItemStack.areItemStacksEqual(written,owner.getStackInSlot(0))){inventory.setStackInSlot(0,input);return;}active=false;work=0;digitizeBlock=ItemStack.EMPTY;scanChip=ItemStack.EMPTY;digitizeChipHash=new byte[0];working(false);owner.synthesisChanged();return;}ItemStack out=inventory.getStackInSlot(1);if(out.isEmpty())inventory.setStackInSlot(1,output(building));else{out.grow(1);inventory.setStackInSlot(1,out);}active=false;work=0;productionTemplate=ItemStack.EMPTY;working(false);nextExport=0;owner.synthesisChanged();}}
    public final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int max,boolean simulate){return accept(Math.max(0,max),simulate);}public int extractEnergy(int max,boolean simulate){return 0;}public int getEnergyStored(){return work+owner.batteryEnergy();}public int getMaxEnergyStored(){return (active?workCost:selectedCost())+owner.batteryCapacity();}public boolean canExtract(){return false;}public boolean canReceive(){return true;}};
    public void tick(){if(!installed()){working(false);return;}if(digitizing&&active&&!outputFits()){active=false;work=0;digitizeBlock=ItemStack.EMPTY;scanChip=ItemStack.EMPTY;digitizeChipHash=new byte[0];working(false);owner.synthesisChanged();}export();start();resetBudget();if(owner.synthesisEnabled()&&active&&outputFits()){int used=owner.extractBatteryEnergy(Math.min(rate()-worked,workCost-work),false);if(used>0){worked+=used;work+=used;spent(used);owner.synthesisChanged();finish();}}working(active&&owner.synthesisEnabled()&&lastWorked>=owner.getWorld().getTotalWorldTime()-1);if(owner.getWorld().getTotalWorldTime()%5==0)owner.refreshSynthesisConnections();}

    /** Ordinary inventory insertion: the target owns the accepted count; retain its remainder once. */
    private void export(){
        if(digitizing)return;long now=owner.getWorld().getTotalWorldTime();if(exporting||now<nextExport||!owner.synthesisReady()||inventory.getStackInSlot(1).isEmpty())return;
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
    public void write(NBTTagCompound tag){if(failedData!=null){tag.setTag("Synthesis",failedData.copy());return;}NBTTagCompound data=new NBTTagCompound();if(digitizing){data.setInteger("Mode",1);if(active){data.setTag("DigitizeBlock",digitizeBlock.writeToNBT(new NBTTagCompound()));data.setByteArray("DigitizeChipHash",digitizeChipHash);}}if(active&&!productionTemplate.isEmpty())data.setTag("ProductionTemplate",productionTemplate.writeToNBT(new NBTTagCompound()));data.setTag("Inventory",inventory.serializeNBT());data.setInteger("Selected",selected);data.setString("SelectedRecipe",loadedSelected==null?recipe(selected):loadedSelected);data.setInteger("Building",building);data.setString("BuildingRecipe",loadedBuilding==null?recipe(building):loadedBuilding);data.setInteger("Work",work);if(active&&workCost!=REQUIRED_RF)data.setInteger("WorkCost",workCost);data.setBoolean("Active",active);data.setBoolean("Connected",connected);tag.setTag("Synthesis",data);}
    public void read(NBTTagCompound tag){working=false;lastWorked=Long.MIN_VALUE;nextExport=0;NBTTagCompound data=tag.getCompoundTag("Synthesis");failedData=null;digitizing=data.getInteger("Mode")==1;digitizeBlock=data.hasKey("DigitizeBlock",10)?new ItemStack(data.getCompoundTag("DigitizeBlock")):ItemStack.EMPTY;digitizeChipHash=data.getByteArray("DigitizeChipHash");NBTTagCompound items=data.getCompoundTag("Inventory").copy();items.setInteger("Size",6);inventory.deserializeNBT(items);selected=Math.max(0,Math.min(255,data.getInteger("Selected")));building=Math.max(0,Math.min(255,data.getInteger("Building")));workCost=!digitizing&&data.getInteger("WorkCost")==SynthesisRecipePolicy.CORE_RF?SynthesisRecipePolicy.CORE_RF:REQUIRED_RF;work=Math.max(0,Math.min(workCost,data.getInteger("Work")));active=data.getBoolean("Active");connected=data.getBoolean("Connected");loadedSelected=data.hasKey("SelectedRecipe",8)?BuilderRecipeCatalog.canonical(data.getString("SelectedRecipe")):null;loadedBuilding=data.hasKey("BuildingRecipe",8)?BuilderRecipeCatalog.canonical(data.getString("BuildingRecipe")):null;unknownRecipe=false;cachedMachine="";
        ItemStack input=inventory.getStackInSlot(0);
        if(digitizing&&!input.isEmpty()&&!BlockRecipeData.eligible(input)){failedData=data.copy();active=false;unknownRecipe=true;}
        productionTemplate=ItemStack.EMPTY;
        if(active&&!digitizing&&BlockRecipeData.ID.equals(loadedBuilding)){
            // Earlier builds stored the in-progress design only on the inserted chip.
            productionTemplate=data.hasKey("ProductionTemplate")?new ItemStack(data.getCompoundTag("ProductionTemplate")):BlockRecipeData.read(owner.getStackInSlot(0));
            if(data.hasKey("ProductionTemplate")&&(productionTemplate.getCount()!=1||!BlockRecipeData.eligible(productionTemplate))){failedData=data.copy();active=false;unknownRecipe=true;}
        }
        scanChip=digitizing&&active&&java.util.Arrays.equals(digitizeChipHash,chipHash(owner.getStackInSlot(0)))?owner.getStackInSlot(0).copy():ItemStack.EMPTY;if(data.hasKey("Mode")&&(!data.hasKey("Mode",3)||data.getInteger("Mode")<0||data.getInteger("Mode")>1)||digitizing&&active&&(!BlockRecipeData.eligible(digitizeBlock)||digitizeChipHash.length!=32)){failedData=data.copy();active=false;unknownRecipe=true;}}
    public void drop(){for(int slot=0;slot<inventory.getSlots();slot++){ItemStack stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,stack);inventory.setStackInSlot(slot,ItemStack.EMPTY);}if(active&&!digitizing){net.minecraft.inventory.InventoryHelper.spawnItemStack(owner.getWorld(),owner.getPos().getX()+.5,owner.getPos().getY()+.5,owner.getPos().getZ()+.5,new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+BuilderRecipeCatalog.input(machine())))));}active=false;work=0;digitizeBlock=ItemStack.EMPTY;scanChip=ItemStack.EMPTY;digitizeChipHash=new byte[0];working(false);if(failedData!=null){failedData=failedData.copy();failedData.setTag("Inventory",inventory.serializeNBT());failedData.setBoolean("Active",false);failedData.setInteger("Work",0);}}
    private static byte[] chipHash(ItemStack stack){if(!com.vandorlabs.programmer.ChipJournal.isChip(stack))return new byte[0];try{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");fingerprint(stack.writeToNBT(new NBTTagCompound()),digest);return digest.digest();}catch(java.security.NoSuchAlgorithmException invalid){throw new IllegalStateException(invalid);}}
    private static void fingerprint(net.minecraft.nbt.NBTBase value,java.security.MessageDigest digest){
        digest.update(value.getId());
        if(value instanceof NBTTagCompound){NBTTagCompound compound=(NBTTagCompound)value;java.util.List<String> keys=new java.util.ArrayList<>(compound.getKeySet());java.util.Collections.sort(keys);for(String key:keys){hashText(key,digest);fingerprint(compound.getTag(key),digest);}digest.update((byte)0);}
        else if(value instanceof net.minecraft.nbt.NBTTagList){net.minecraft.nbt.NBTTagList list=(net.minecraft.nbt.NBTTagList)value;hashText(Integer.toString(list.tagCount()),digest);for(int i=0;i<list.tagCount();i++)fingerprint(list.get(i),digest);}
        else if(value instanceof net.minecraft.nbt.NBTTagByteArray){byte[] bytes=((net.minecraft.nbt.NBTTagByteArray)value).getByteArray();hashText(Integer.toString(bytes.length),digest);digest.update(bytes);}
        else hashText(value.toString(),digest);
    }
    private static void hashText(String value,java.security.MessageDigest digest){byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update((byte)(bytes.length>>>24));digest.update((byte)(bytes.length>>>16));digest.update((byte)(bytes.length>>>8));digest.update((byte)bytes.length);digest.update(bytes);}
    public IItemHandler port(boolean input){return new IItemHandler(){public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){if(slot!=0)return ItemStack.EMPTY;return inventory.getStackInSlot(input?0:1);}public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0&&input&&installed()?inventory.insertItem(0,stack,simulate):stack;}public ItemStack extractItem(int slot,int amount,boolean simulate){return slot!=0||input||!installed()?ItemStack.EMPTY:inventory.extractItem(1,amount,simulate);}public int getSlotLimit(int slot){return 64;}};}
}
