package com.vandorlabs.neoforge.builders;
import com.vandorlabs.neoforge.rack.NativeBatteryModule;import net.minecraft.nbt.*;import net.minecraft.world.item.ItemStack;import net.neoforged.neoforge.energy.IEnergyStorage;
public final class NativeSynthesisRuntime {
 public static final int COST=com.vandorlabs.machines.SynthesisRecipePolicy.REQUIRED_RF,RATE=com.vandorlabs.machines.SynthesisRecipePolicy.RF_PER_TICK;
 private final NativeBuilderEntity owner;private int selected,building,work,received,worked;private boolean active,connected;private long last=Long.MIN_VALUE;
 private boolean working,exporting;private long lastWorked=Long.MIN_VALUE,nextExport;
 public boolean working(){return working;}public void visualWorking(boolean value){working=value;}public boolean exporting(){return exporting;}
 private void working(boolean value){if(working!=value){working=value;owner.changed();}}
 private void spent(int rf){if(rf>0){lastWorked=owner.getLevel().getGameTime();working(true);}}
 NativeSynthesisRuntime(NativeBuilderEntity owner){this.owner=owner;}
 public int selected(){return selected;}public int building(){return active||!owner.getItem(1).isEmpty()?building:selected;}public float progress(){return active?work/(float)COST:owner.getItem(1).isEmpty()?0:1;}public boolean active(){return active;}public boolean connected(){return connected;}void connection(boolean value){connected=value;}
 public void select(int index){if(index<0||index>2)return;selected=index;owner.changed();}
 public static ItemStack result(int index){return new ItemStack(NativeBuilderBindings.CORES.get(NativeBuilderDesigns.at(index).id()));}
 private void budget(){long now=owner.getLevel().getGameTime();if(now!=last){last=now;received=worked=0;}}
 private boolean queued(){return !active&&owner.getItem(1).isEmpty()&&owner.canPlaceItem(0,owner.getItem(0));}
 private void start(){if(owner.enabled()&&queued()){building=selected;owner.owned(0).shrink(1);active=true;work=0;lastWorked=Long.MIN_VALUE;owner.changed();}}
 private void finish(){if(active&&work==COST&&owner.getItem(1).isEmpty()){owner.own(1,result(building));active=false;work=0;working(false);nextExport=0;owner.changed();}}
 private int receive(int amount,boolean simulate){if(!owner.available()||!owner.formed()||!owner.synthesis())return 0;budget();if(!simulate)start();int available=Math.max(0,Math.min(amount,RATE-received));int recipe=owner.enabled()&&(active||simulate&&queued())&&owner.getItem(1).isEmpty()?Math.min(Math.min(available,RATE-worked),COST-work):0;int charge=owner.receiveBatteryEnergy(available-recipe,true);if(!simulate){received+=recipe+charge;worked+=recipe;work+=recipe;spent(recipe);owner.receiveBatteryEnergy(charge,false);if(recipe+charge>0)owner.changed();finish();}return recipe+charge;}
 public final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int amount,boolean simulate){return receive(amount,simulate);}public int extractEnergy(int amount,boolean simulate){return 0;}public int getEnergyStored(){return work+owner.batteryEnergy();}public int getMaxEnergyStored(){return COST+owner.batteryCapacity();}public boolean canReceive(){return true;}public boolean canExtract(){return false;}};
 public void tick(){if(!owner.available()||!owner.synthesis()||!owner.formed()){working(false);return;}budget();export();start();if(active&&owner.enabled()&&owner.getItem(1).isEmpty()){int used=owner.extractBatteryEnergy(Math.min(RATE-worked,COST-work),false);worked+=used;work+=used;spent(used);if(used>0)owner.changed();finish();}working(active&&owner.enabled()&&lastWorked>=owner.getLevel().getGameTime()-1);if(owner.getLevel().getGameTime()%5==0)owner.refreshConnections();}
 private void export(){
  long now=owner.getLevel().getGameTime();if(exporting||now<nextExport||!owner.synthesisReady()||owner.getItem(1).isEmpty())return;
  nextExport=now+8;exporting=true;
  try{for(var side:new net.minecraft.core.Direction[]{owner.facing().getCounterClockWise(),net.minecraft.core.Direction.DOWN}){
   if(!owner.available()||owner.getItem(1).isEmpty())break;
   var at=owner.itemPort().relative(side);if(!NativeBuilderFormation.loaded(owner.getLevel(),at)||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(owner.getLevel(),at))continue;
   var target=owner.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,at,side.getOpposite());if(target==null)continue;
   var before=owner.getItem(1);var remainder=net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(target,before.copy(),false);
   owner.own(1,remainder);if(remainder.getCount()!=before.getCount())nextExport=now+1;
  }}finally{exporting=false;}
 }
 public void save(CompoundTag tag){tag.putInt("Selected",selected);tag.putInt("Building",building);tag.putInt("Work",work);tag.putBoolean("Active",active);tag.putBoolean("Connected",connected);}
 public void read(CompoundTag tag){working=false;lastWorked=Long.MIN_VALUE;nextExport=0;if(!tag.getAllKeys().stream().allMatch(k->java.util.Set.of("Selected","Building","Work","Active","Connected").contains(k)))throw new IllegalArgumentException("Future builder work snapshot");for(var k:java.util.List.of("Selected","Building","Work"))if(tag.contains(k)&&!tag.contains(k,Tag.TAG_INT))throw new IllegalArgumentException("Builder work integer type");for(var k:java.util.List.of("Active","Connected"))if(tag.contains(k)&&(!tag.contains(k,Tag.TAG_BYTE)||tag.getByte(k)<0||tag.getByte(k)>1))throw new IllegalArgumentException("Builder work boolean type");int nextSelected=tag.getInt("Selected"),nextBuilding=tag.getInt("Building"),nextWork=tag.getInt("Work");boolean nextActive=tag.getBoolean("Active");if(nextSelected<0||nextSelected>2||nextBuilding<0||nextBuilding>2||nextWork<0||nextWork>COST||!nextActive&&nextWork!=0)throw new IllegalArgumentException("Builder work snapshot");selected=nextSelected;building=nextBuilding;work=nextWork;active=nextActive;connected=tag.getBoolean("Connected");}

}
