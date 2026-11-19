package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;
import java.util.*;
import com.vandorlabs.machines.SynthesisRecipePolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Committed RF is work. Digitize keeps the physical input and chip in their original slots until completion. */
public final class NativeSynthesisRuntime {
 public static final int COST=com.vandorlabs.machines.SynthesisRecipePolicy.REQUIRED_RF,RATE=com.vandorlabs.machines.SynthesisRecipePolicy.RF_PER_TICK,DYNAMIC=5;
 private static final int MAX_CHIP_BYTES=64*1024;
 private final NativeBuilderEntity owner;
 private int selected,building,work,received,worked,mode,workCost=COST;private boolean active,connected,visual;
 private long last=Long.MIN_VALUE,lastWorked=Long.MIN_VALUE,nextExport;private boolean working,exporting;
 private ItemStack digitizeBlock=ItemStack.EMPTY,scanChip=ItemStack.EMPTY,productionTemplate=ItemStack.EMPTY;
 private byte[] chipHash=new byte[0];
 private ItemStack admissionChip=ItemStack.EMPTY;private byte[] admissionHash=new byte[0];private boolean admissionAllowed,completing;
 static long admissionSerializations;
 private ItemStack cachedChip=ItemStack.EMPTY,cachedTemplate=ItemStack.EMPTY,cachedInput=ItemStack.EMPTY;private boolean inputEligible;
 NativeSynthesisRuntime(NativeBuilderEntity owner){this.owner=owner;if(owner.press())selected=building=1;}
 public int mode(){return mode;}public boolean digitizing(){return owner.synthesis()&&mode==1;}
 public boolean mode(int next){if(next<0||next>1||!owner.synthesis()||active||!owner.owned(0).isEmpty()||!owner.owned(1).isEmpty())return false;if(mode==next)return true;mode=next;owner.changed();return true;}
 public boolean working(){return working;}public void visualWorking(boolean value){working=value;}public boolean exporting(){return exporting;}
 private void working(boolean value){if(working!=value){working=value;owner.changed();}}
 private void spent(int rf){if(rf>0){lastWorked=owner.getLevel().getGameTime();working(true);}}
 public int selected(){return selected;}public int building(){return active||!owner.owned(1).isEmpty()?building:selected;}
 public float progress(){return active?work/(float)workCost:!digitizing()&&!owner.owned(1).isEmpty()?1:0;}
 public boolean active(){return active;}public boolean connected(){return connected;}void connection(boolean value){connected=value;}
 private int selectedCost(){return owner.synthesis()&&!digitizing()&&selected<DYNAMIC?SynthesisRecipePolicy.CORE_RF:COST;}
 private int maxDesign(){return owner.synthesis()?NativeBuilderProducts.RAMP:NativeBuilderProducts.parts(owner.design().id()).size()-1;}private int rate(){return com.vandorlabs.machines.SynthesisRecipePolicy.rfPerTick(owner.design().id());}

 private boolean dynamic(int index){return owner.synthesis()&&index==DYNAMIC;}
 public ItemStack dynamicTemplate(){var chip=owner.owned(3);if(!ItemStack.matches(chip,cachedChip)){cachedChip=NativeCredentialItems.ownedCopy(chip);cachedTemplate=NativeBlockRecipeData.read(chip);}return cachedTemplate.copy();}
 public boolean dynamicAvailable(){return !dynamicTemplate().isEmpty();}
 public ItemStack previewTemplate(){if(!owner.owned(1).isEmpty()&&dynamic(building))return owner.owned(1).copyWithCount(1);if(active&&!digitizing()&&dynamic(building))return productionTemplate.copy();return dynamic(selected)?dynamicTemplate():ItemStack.EMPTY;}
 public ItemStack previewInput(){if(!digitizing())return ItemStack.EMPTY;var item=!digitizeBlock.isEmpty()?digitizeBlock:owner.owned(0);return item.isEmpty()?ItemStack.EMPTY:item.copyWithCount(1);}
 public ItemStack product(int index){return owner.synthesis()?(dynamic(index)?active&&dynamic(building)&&!productionTemplate.isEmpty()?productionTemplate.copy():dynamicTemplate():result(index)):NativeBuilderProducts.partResult(owner.design().id(),index);}
 private ItemStack catalogSource=ItemStack.EMPTY;
 private int catalogChoices;
 private int catalogChoices() {
  var chip=owner.owned(3);
  if(!com.vandorlabs.neoforge.credentials.NativeChipTypes.type(chip).equals("catalog")){
   catalogSource=ItemStack.EMPTY;
   catalogChoices=0;
   return 0;
  }
  if(!ItemStack.matches(chip,catalogSource)){
   catalogSource=NativeCredentialItems.ownedCopy(chip);
   catalogChoices=0;
   var tag=com.vandorlabs.neoforge.credentials.NativeChipTypes.data(chip);
   var catalog=tag.getCompound("Catalog");
   if(catalog.getInt("Version")==com.vandorlabs.credentials.CatalogChipPolicy.VERSION){
    var ids=catalog.getCompound("Designs").getList(owner.design().id(),Tag.TAG_STRING);
    for(var entry:ids){
     String id=com.vandorlabs.machines.BuilderRecipeCatalog.canonical(entry.getAsString());
     if(owner.synthesis()){
      for(int i=3;i<DYNAMIC;i++)if(id.equals(NativeBuilderProducts.IDS[i]+"_core"))catalogChoices|=1<<i;
      if(id.equals(NativeBuilderProducts.RAMP_BLUEPRINT))catalogChoices|=1<<NativeBuilderProducts.RAMP;
     }else{
      int index=NativeBuilderProducts.PRESS.indexOf(id);
      if(index>=0)catalogChoices|=1<<index;
     }
    }
   }
  }
  return catalogChoices;
 }
 public int pressChoices() {
  if(!owner.press())return 0;
  int enabled=catalogChoices();
  for(int i=0;i<=maxDesign();i++){
   if(!NativeBuilderProducts.pressPartAvailable(i))enabled &= ~(1<<i);
   else if(NativeBuilderProducts.partDefault(owner.design().id(),i))enabled |= 1<<i;
  }
  return enabled;
 }
 public int choices(){
  if(owner.press())return pressChoices();
  return owner.synthesis()&&NativeBuilderProducts.rampAvailable()?catalogChoices():0;
 }
 private boolean towerRamp(int index){return owner.synthesis()&&index==NativeBuilderProducts.RAMP;}
 private boolean permitted(){
  if(owner.press())return (pressChoices()&(1<<selected))!=0;
  if(owner.gantry())return NativeBuilderProducts.partDefault(owner.design().id(),selected);
  if(dynamic(selected))return dynamicAvailable();
  if(towerRamp(selected))return (choices()&(1<<selected))!=0;
  return selected<3||(catalogChoices()&(1<<selected))!=0;
 }
 public void select(int index){
  if(index<0||index>maxDesign()||dynamic(index)&&!dynamicAvailable()||(owner.press()||towerRamp(index))&&(choices()&(1<<index))==0)return;
  selected=index;owner.changed();
 }
 public static ItemStack result(int index){return NativeBuilderProducts.result(index);}
 private void budget(){long now=owner.getLevel().getGameTime();if(now!=last){last=now;received=worked=0;}}
 private boolean eligibleInput(){var current=owner.owned(0);if(!ItemStack.matches(current,cachedInput)){cachedInput=NativeCredentialItems.ownedCopy(current);inputEligible=NativeBlockRecipeData.eligible(current);}return inputEligible;}
 // Rejected stacks share immutable component values: never duplicate an oversized payload.
 // ItemStack.copy snapshots the component map, so legal component replacement still invalidates it.
 private boolean chipAdmitted(){var chip=owner.owned(3);if(!ItemStack.matches(chip,admissionChip)){admissionChip=chip.copy();admissionAllowed=false;admissionHash=new byte[0];if(NativeBlockRecipeData.canDigitizeChip(chip)){admissionSerializations++;var encoded=chip.save(owner.getLevel().registryAccess());if(encoded.sizeInBytes()<=MAX_CHIP_BYTES){admissionChip=NativeCredentialItems.ownedCopy(chip);admissionHash=hash(encoded);admissionAllowed=true;}}}return admissionAllowed;}
 void inventoryChanged(){if(!admissionChip.isEmpty()&&!ItemStack.matches(admissionChip,owner.owned(3))){admissionChip=ItemStack.EMPTY;admissionHash=new byte[0];admissionAllowed=false;}if(active&&digitizing()&&!completing&&!visual&&!fits())cancel();}
 private boolean queued(){if(active||!owner.owned(1).isEmpty())return false;return digitizing()?owner.owned(0).getCount()==1&&eligibleInput()&&chipAdmitted():owner.canPlaceItem(0,owner.owned(0))&&permitted();}
 private boolean fits(){
  if(digitizing())return active&&ItemStack.matches(digitizeBlock,owner.owned(0))&&ItemStack.matches(scanChip,owner.owned(3));
  if(!owner.owned(1).isEmpty())return false;
  if(owner.press())return NativeBuilderProducts.pressPartAvailable(building);
  return !towerRamp(building)||NativeBuilderProducts.rampAvailable();
 }
 private void start(){if(!owner.enabled()||!queued())return;if(digitizing()){digitizeBlock=NativeCredentialItems.ownedCopy(owner.owned(0));scanChip=NativeCredentialItems.ownedCopy(owner.owned(3));chipHash=admissionHash.clone();}else{building=selected;if(dynamic(building)){productionTemplate=dynamicTemplate();if(productionTemplate.isEmpty())return;}owner.owned(0).shrink(1);}workCost=selectedCost();active=true;work=0;lastWorked=Long.MIN_VALUE;owner.changed();}
 private void cancel(){active=false;work=0;digitizeBlock=scanChip=ItemStack.EMPTY;chipHash=new byte[0];working(false);owner.changed();}
 private void finish(){if(!active||work<workCost||!fits())return;completing=true;try{if(digitizing()){var written=NativeCredentialItems.ownedCopy(owner.owned(3));NativeBlockRecipeData.write(written,digitizeBlock,owner.getLevel().registryAccess());owner.owned(0).shrink(1);owner.own(3,written);digitizeBlock=scanChip=ItemStack.EMPTY;chipHash=new byte[0];}else{owner.own(1,product(building));productionTemplate=ItemStack.EMPTY;}active=false;work=0;working(false);nextExport=0;owner.changed();}finally{completing=false;}}
 private int receive(int amount,boolean simulate){if(!owner.available()||!owner.formed()||!owner.production())return 0;budget();if(!simulate)start();int available=Math.max(0,Math.min(amount,rate()-received));int recipe=owner.enabled()&&(active&&fits()||simulate&&queued())?Math.min(Math.min(available,rate()-worked),(active?workCost-work:selectedCost())):0;int charge=owner.receiveBatteryEnergy(available-recipe,true);if(!simulate){received+=recipe+charge;worked+=recipe;work+=recipe;spent(recipe);owner.receiveBatteryEnergy(charge,false);if(recipe+charge>0)owner.changed();finish();}return recipe+charge;}
 public final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int amount,boolean simulate){return receive(amount,simulate);}public int extractEnergy(int amount,boolean simulate){return 0;}public int getEnergyStored(){return work+owner.batteryEnergy();}public int getMaxEnergyStored(){return (active?workCost:selectedCost())+owner.batteryCapacity();}public boolean canReceive(){return true;}public boolean canExtract(){return false;}};
 public void tick(){if(!owner.available()||!owner.production()||!owner.formed()){working(false);return;}if(digitizing()&&active&&!fits())cancel();budget();export();start();if(active&&owner.enabled()&&fits()){int used=owner.extractBatteryEnergy(Math.min(rate()-worked,(active?workCost-work:selectedCost())),false);worked+=used;work+=used;spent(used);if(used>0)owner.changed();finish();}working(active&&owner.enabled()&&lastWorked>=owner.getLevel().getGameTime()-1);if(owner.getLevel().getGameTime()%5==0)owner.refreshConnections();}
 private void export(){
  if(digitizing())return;long now=owner.getLevel().getGameTime();if(exporting||now<nextExport||!owner.synthesisReady()||owner.owned(1).isEmpty())return;nextExport=now+8;exporting=true;
  try{for(var side:new net.minecraft.core.Direction[]{owner.facing().getCounterClockWise(),net.minecraft.core.Direction.DOWN}){if(!owner.available()||owner.owned(1).isEmpty())break;var at=owner.itemPort().relative(side);if(!NativeBuilderFormation.loaded(owner.getLevel(),at)||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(owner.getLevel(),at))continue;var target=owner.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,at,side.getOpposite());if(target==null)continue;var before=owner.owned(1);var remainder=net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(target,before.copy(),false);owner.own(1,remainder);if(remainder.getCount()!=before.getCount())nextExport=now+1;}}finally{exporting=false;}
 }
 public void saveVisual(CompoundTag tag){tag.putInt("Selected",selected);tag.putInt("Building",building);tag.putInt("Work",work);if(active&&workCost!=COST)tag.putInt("WorkCost",workCost);tag.putBoolean("Active",active);tag.putBoolean("Connected",connected);if(mode!=0)tag.putInt("Mode",mode);}
 public void save(CompoundTag tag,HolderLookup.Provider r){saveVisual(tag);if(active&&digitizing()){tag.put("DigitizeBlock",digitizeBlock.save(r));tag.putByteArray("DigitizeChipHash",chipHash);}if(active&&!digitizing()&&dynamic(building))tag.put("ProductionTemplate",productionTemplate.save(r));}
 public void readVisual(CompoundTag tag){readScalars(tag,Set.of("Selected","Building","Work","Active","Connected","Mode","WorkCost"));visual=true;digitizeBlock=scanChip=productionTemplate=ItemStack.EMPTY;chipHash=new byte[0];}
 private void readScalars(CompoundTag tag,Set<String> fields){working=false;lastWorked=Long.MIN_VALUE;nextExport=0;if(!fields.containsAll(tag.getAllKeys()))throw new IllegalArgumentException("Future builder work snapshot");for(var key:List.of("Selected","Building","Work","Mode","WorkCost"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Builder work integer type");for(var key:List.of("Active","Connected"))if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_BYTE)||tag.getByte(key)<0||tag.getByte(key)>1))throw new IllegalArgumentException("Builder work boolean type");int a=tag.getInt("Selected"),b=tag.getInt("Building"),w=tag.getInt("Work"),m=tag.getInt("Mode");boolean on=tag.getBoolean("Active");int cost=tag.contains("WorkCost")?tag.getInt("WorkCost"):COST;
 if(cost!=COST&&(cost!=SynthesisRecipePolicy.CORE_RF||!on||!owner.synthesis()||m!=0||b>=DYNAMIC))throw new IllegalArgumentException("Builder work cost");
 if(a<0||a>maxDesign()||b<0||b>maxDesign()||w<0||w>cost||!on&&w!=0||m<0||m>1||m==1&&!owner.synthesis())throw new IllegalArgumentException("Builder work snapshot");selected=a;building=b;work=w;workCost=cost;active=on;connected=tag.getBoolean("Connected");mode=m;}
 public void read(CompoundTag tag){read(tag,owner.getLevel()==null?null:owner.getLevel().registryAccess());}
 public void read(CompoundTag tag,HolderLookup.Provider r){readScalars(tag,Set.of("Selected","Building","Work","Active","Connected","Mode","WorkCost","DigitizeBlock","DigitizeChipHash","ProductionTemplate"));visual=false;digitizeBlock=scanChip=productionTemplate=ItemStack.EMPTY;chipHash=new byte[0];if(active&&digitizing()){if(r==null||!tag.contains("DigitizeBlock",Tag.TAG_COMPOUND)||!tag.contains("DigitizeChipHash",Tag.TAG_BYTE_ARRAY)||tag.getByteArray("DigitizeChipHash").length!=32)throw new IllegalArgumentException("Digitize snapshot identity");digitizeBlock=template(tag.getCompound("DigitizeBlock"),r);chipHash=tag.getByteArray("DigitizeChipHash").clone();}else if(tag.contains("DigitizeBlock")||tag.contains("DigitizeChipHash"))throw new IllegalArgumentException("Unexpected Digitize ownership");if(active&&!digitizing()&&dynamic(building)){if(r==null||!tag.contains("ProductionTemplate",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing production template");productionTemplate=template(tag.getCompound("ProductionTemplate"),r);}else if(tag.contains("ProductionTemplate"))throw new IllegalArgumentException("Unexpected production template");}
 static ItemStack template(CompoundTag tag,HolderLookup.Provider r){if(tag.sizeInBytes()>NativeBlockRecipeData.MAX_BYTES)throw new IllegalArgumentException("Builder template bound");var stack=ItemStack.parse(r,tag).orElseThrow(()->new IllegalArgumentException("Builder template item"));if(stack.getCount()!=1||!NativeBlockRecipeData.eligible(stack)||!tag.equals(stack.save(r)))throw new IllegalArgumentException("Builder template data");return stack;}
 void restoreInventory(HolderLookup.Provider r){if(active&&digitizing()){var chip=owner.owned(3);if(!ItemStack.matches(digitizeBlock,owner.owned(0))||!NativeCredentialItems.accepts(chip,true))throw new IllegalArgumentException("Digitize saved input identity");var encoded=chip.save(r);if(encoded.sizeInBytes()>MAX_CHIP_BYTES||!Arrays.equals(chipHash,hash(encoded)))throw new IllegalArgumentException("Digitize saved chip identity");if(NativeBlockRecipeData.canDigitizeChip(chip))scanChip=NativeCredentialItems.ownedCopy(chip);else cancel();}}
 private static byte[] hash(Tag value){try{var digest=java.security.MessageDigest.getInstance("SHA-256");fingerprint(value,digest);return digest.digest();}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static void fingerprint(Tag value,java.security.MessageDigest digest){digest.update(value.getId());if(value instanceof CompoundTag compound){var keys=new ArrayList<>(compound.getAllKeys());Collections.sort(keys);for(String key:keys){text(key,digest);fingerprint(compound.get(key),digest);}digest.update((byte)0);}else if(value instanceof ListTag list){text(Integer.toString(list.size()),digest);for(var element:list)fingerprint(element,digest);}else if(value instanceof ByteArrayTag bytes){var data=bytes.getAsByteArray();text(Integer.toString(data.length),digest);digest.update(data);}else text(value.toString(),digest);}
 private static void text(String text,java.security.MessageDigest digest){byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update((byte)(bytes.length>>>24));digest.update((byte)(bytes.length>>>16));digest.update((byte)(bytes.length>>>8));digest.update((byte)bytes.length);digest.update(bytes);}
}
