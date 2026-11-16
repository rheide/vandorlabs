package com.vandorlabs.neoforge.infuser;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.rack.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Source-compatible solar work; the one physical battery stack remains the charge owner. */
public final class NativeInfuserEntity extends BlockEntity implements WorldlyContainer {
 private ItemStack[] items={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
 private int fuelRemaining,fuelDuration,progress;private boolean inert;private CompoundTag retained=new CompoundTag();
 public NativeInfuserEntity(BlockPos pos,BlockState state){super(NativeInfuserBindings.ENTITY,pos,state);}
 public boolean inert(){return inert;}
 public boolean available(){return !inert&&!isRemoved()&&level instanceof ServerLevel server&&server.getServer().isSameThread()&&loaded()&&level.getBlockEntity(worldPosition)==this&&!held();}
 public boolean loaded(){return level!=null&&level.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4)!=null;}
 public boolean held(){return level!=null&&com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition);}
 public boolean canUse(Player player){return !inert&&!isRemoved()&&loaded()&&level.getBlockEntity(worldPosition)==this&&NativeConfigurationAccess.canEditDialog(player,this)&&!held();}
 public int fuelRemaining(){return fuelRemaining;}public int fuelDuration(){return fuelDuration;}public int progress(){return progress;}
 public static int fuelTicks(ItemStack stack){return stack.isEmpty()?0:stack.is(Items.REDSTONE)?33:stack.is(Items.REDSTONE_BLOCK)?300:0;}
 public static boolean acceptsInput(ItemStack stack){return !stack.isEmpty()&&(stack.is(Items.IRON_INGOT)||NativeBatteryModule.valid(stack));}
 /** Source canSeeSky is a synchronous column query, not the asynchronously propagated SKY brightness. */
 public boolean hasSunlight(){
  if(level==null||!level.isDay()||!loaded())return false;var probe=worldPosition.above();int x=probe.getX()&15,z=probe.getZ()&15;
  var chunk=level.getChunkSource().getChunkNow(probe.getX()>>4,probe.getZ()>>4);var sources=chunk.getSkyLightSources();
  if(sources==null||sources.getLowestSourceY(x,z)>probe.getY())return false;
  // Preserve vanilla precipitation/biome rules without its stale brightness-based canSeeSky guard.
  boolean rain=level.isRaining()&&chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z)+1<=probe.getY()
   &&level.getBiome(probe).value().getPrecipitationAt(probe)==net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
  return !rain;
 }
 public boolean canProcess(){var input=items[0];var output=items[2];if(NativeBatteryModule.valid(input))return NativeBatteryModule.energy(input)<NativeBatteryModule.CAPACITY||output.isEmpty();return input.is(Items.IRON_INGOT)&&(output.isEmpty()||output.is(VandorLabsBootstrap.MATTER_INGOT.get())&&output.getCount()<Math.min(64,output.getMaxStackSize()));}
 public static void tick(Level level,BlockPos pos,BlockState state,NativeInfuserEntity tile){tile.tick();}
 public void tick(){
  if(!available())return;
  if(!canProcess()){if(progress!=0){progress=0;setChanged();}return;}
  var input=items[0];
  if(NativeBatteryModule.valid(input)&&NativeBatteryModule.energy(input)==NativeBatteryModule.CAPACITY){if(items[2].isEmpty()){items[2]=input;items[0]=ItemStack.EMPTY;progress=0;setChanged();}return;}
  if(!hasSunlight())return;
  int remaining=fuelRemaining;if(remaining<=0){remaining=fuelTicks(items[1]);if(remaining==0)return;items[1].shrink(1);fuelDuration=remaining;}
  fuelRemaining=remaining-1;
  if(NativeBatteryModule.valid(input)){NativeBatteryModule.transfer(input,50,false,true);progress=NativeBatteryModule.energy(input)*200/NativeBatteryModule.CAPACITY;}
  else if(++progress>=200){input.shrink(1);if(items[2].isEmpty())items[2]=new ItemStack(VandorLabsBootstrap.MATTER_INGOT.get());else items[2].grow(1);progress=0;}
  setChanged();
 }
 @Override public int getContainerSize(){return 3;}@Override public boolean isEmpty(){for(var stack:items)if(!stack.isEmpty())return false;return true;}
 @Override public ItemStack getItem(int slot){return slot>=0&&slot<3?items[slot]:ItemStack.EMPTY;}
 @Override public ItemStack removeItem(int slot,int count){if(!available()||slot<0||slot>=3||count<=0)return ItemStack.EMPTY;var result=items[slot].split(count);if(!result.isEmpty())setChanged();return result;}
 @Override public ItemStack removeItemNoUpdate(int slot){if(!available()||slot<0||slot>=3)return ItemStack.EMPTY;var result=items[slot];items[slot]=ItemStack.EMPTY;setChanged();return result;}
 @Override public void setItem(int slot,ItemStack stack){if(!available()||slot<0||slot>=3)return;if(slot==0&&!ItemStack.isSameItemSameComponents(items[0],stack))progress=0;items[slot]=stack;if(!stack.isEmpty()&&stack.getCount()>Math.min(64,stack.getMaxStackSize()))stack.setCount(Math.min(64,stack.getMaxStackSize()));setChanged();}
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return !inert&&(slot==0?acceptsInput(stack):slot==1&&fuelTicks(stack)>0);}
 @Override public boolean stillValid(Player player){return canUse(player);}@Override public void clearContent(){if(!available())return;Arrays.fill(items,ItemStack.EMPTY);progress=0;setChanged();}
 @Override public int[] getSlotsForFace(Direction side){return side==Direction.UP?new int[]{0}:side==Direction.DOWN?new int[]{2,1}:new int[]{1};}
 @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&canPlaceItem(slot,stack)&&(side==null||side==Direction.UP&&slot==0||side!=Direction.DOWN&&side!=Direction.UP&&slot==1);}
 @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&slot>=0&&slot<3&&(side!=Direction.DOWN||slot!=1||stack.is(Items.BUCKET)||stack.is(Items.WATER_BUCKET));}
 /** onRemove clears ownership before spawning drops; a repeated removal cannot duplicate contents. */
 public List<ItemStack> drain(){if(inert||isRemoved()||level==null||level.isClientSide||held()||level.getBlockEntity(worldPosition)!=this)return List.of();var result=new ArrayList<ItemStack>();for(int slot=0;slot<3;slot++){if(!items[slot].isEmpty())result.add(items[slot]);items[slot]=ItemStack.EMPTY;}progress=0;setChanged();return result;}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  if(inert){tag.merge(retained.copy());NativePlatformComponents.clearForInertSave(this);return;}
  tag.merge(retained.copy());super.saveAdditional(tag,registries);tag.putInt("NativeInfuserSchema",1);tag.putInt("FuelRemaining",fuelRemaining);tag.putInt("FuelDuration",fuelDuration);tag.putInt("Progress",progress);
  var list=new ListTag();for(int slot=0;slot<3;slot++)if(!items[slot].isEmpty()){var entry=new CompoundTag();entry.putByte("Slot",(byte)slot);entry.put("Stack",items[slot].save(registries));list.add(entry);}tag.put("Items",list);
 }
 private static int integer(CompoundTag tag,String key,int fallback,int maximum,boolean legacy){if(!tag.contains(key))return fallback;if(!tag.contains(key,legacy?Tag.TAG_SHORT:Tag.TAG_INT))throw new IllegalArgumentException("Infuser timer type "+key);int value=legacy?tag.getShort(key):tag.getInt(key);if(value<0||value>maximum)throw new IllegalArgumentException("Infuser timer bound "+key);return value;}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){var original=tag.copy();try{
  NativePlatformComponents.requireSupported(original,registries);if(tag.contains("ForgeCaps")&&(!tag.contains("ForgeCaps",Tag.TAG_COMPOUND)||!tag.getCompound("ForgeCaps").isEmpty()))throw new IllegalArgumentException("Unsupported Infuser capability owner");
  boolean legacy=!tag.contains("NativeInfuserSchema");if(!legacy&&(!tag.contains("NativeInfuserSchema",Tag.TAG_INT)||tag.getInt("NativeInfuserSchema")!=1))throw new IllegalArgumentException("Infuser schema");
  if(!legacy&&(tag.contains("BurnTime")||tag.contains("CookTime")||tag.contains("CookTimeTotal")))throw new IllegalArgumentException("Mixed Infuser timelines");
  int nextFuel=integer(tag,legacy?"BurnTime":"FuelRemaining",0,300,legacy),nextDuration=legacy?0:integer(tag,"FuelDuration",0,300,false),nextProgress=integer(tag,legacy?"CookTime":"Progress",0,200,legacy);
  if(legacy&&integer(tag,"CookTimeTotal",200,200,true)!=200)throw new IllegalArgumentException("Infuser cook duration");
  if(!legacy&&nextDuration!=0&&nextFuel>nextDuration)throw new IllegalArgumentException("Infuser fuel timeline");
  var staged=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};boolean[] seen=new boolean[3];
  if(tag.contains("Items")&&(!(tag.get("Items") instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND||list.size()>3))throw new IllegalArgumentException("Infuser inventory schema");
  for(var value:tag.getList("Items",Tag.TAG_COMPOUND)){var entry=(CompoundTag)value;if(!entry.contains("Slot",Tag.TAG_BYTE))throw new IllegalArgumentException("Infuser slot type");int slot=entry.getByte("Slot")&255;if(slot>=3||seen[slot])throw new IllegalArgumentException("Infuser duplicate slot");seen[slot]=true;ItemStack stack;
   if(!legacy){if(!entry.getAllKeys().equals(Set.of("Slot","Stack"))||!entry.contains("Stack",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Infuser item envelope");var encoded=entry.getCompound("Stack");stack=NativeInventoryItems.modern(encoded,registries);if(!encoded.equals(stack.save(registries)))throw new IllegalArgumentException("Infuser lossy item components");}
   else if(entry.getString("id").equals("vandorlabs:battery_module"))stack=NativeRackData.readItem(entry,registries);
   else stack=NativeInventoryItems.legacy(entry,registries);
   if(stack.isEmpty()||stack.getCount()>Math.min(64,stack.getMaxStackSize())||NativeInventoryItems.containedOwnership(stack)||!(slot==0?acceptsInput(stack):slot==1?fuelTicks(stack)>0:stack.is(VandorLabsBootstrap.MATTER_INGOT.get())||NativeBatteryModule.valid(stack)))throw new IllegalArgumentException("Infuser saved slot owner");staged[slot]=stack;
  }
  if(nextProgress==200&&!NativeBatteryModule.valid(staged[0]))throw new IllegalArgumentException("Infuser completed work without battery owner");
  try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}
  items=staged;fuelRemaining=nextFuel;fuelDuration=nextDuration;progress=nextProgress;retained=original;for(String key:List.of("id","x","y","z","Items","NativeInfuserSchema","FuelRemaining","FuelDuration","Progress","BurnTime","CookTime","CookTimeTotal"))retained.remove(key);NativePlatformComponents.forgetLoadedFields(retained);inert=false;
 }catch(RuntimeException failed){retained=original;inert=true;Arrays.fill(items,ItemStack.EMPTY);fuelRemaining=fuelDuration=progress=0;}}
 /** Public chunk data never carries private furnace items or catalyst ownership. */
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putBoolean("InfuserInert",inert);return tag;}
 @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){inert=tag.getBoolean("InfuserInert");}
}
