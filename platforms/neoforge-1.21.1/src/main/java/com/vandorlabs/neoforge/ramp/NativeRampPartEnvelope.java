package com.vandorlabs.neoforge.ramp;
import java.util.Set;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
/** Generated surfaces own only their protocol and harmless vanilla display metadata. */
final class NativeRampPartEnvelope {
 private static final Set<String> KEYS=Set.of("NativeRampPartSchema","Operation","RootX","RootY","RootZ","NativeRampPartData","NativeRampPublicationRevision","NativeRampObservationTick","Inert","id","x","y","z","keepPacked","components","NeoForgeData","neoforge:attachments","ForgeCaps","ForgeData");
 private static final Set<String> DISPLAY=Set.of("minecraft:custom_name","minecraft:item_name","minecraft:lore");
 static void require(CompoundTag tag){
  for(var key:tag.getAllKeys())if(!KEYS.contains(key))throw new IllegalArgumentException("Unknown generated Ramp envelope");
  for(var key:new String[]{"NeoForgeData","neoforge:attachments","ForgeCaps","ForgeData"})if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_COMPOUND)||!tag.getCompound(key).isEmpty()))throw new IllegalArgumentException("Owned or opaque generated Ramp metadata");
  if(tag.contains("components")){if(!tag.contains("components",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown Ramp component envelope");for(var key:tag.getCompound("components").getAllKeys())if(!DISPLAY.contains(key))throw new IllegalArgumentException("Owned or opaque generated Ramp components");}
  if(tag.contains("keepPacked")&&(!tag.contains("keepPacked",Tag.TAG_BYTE)||tag.getByte("keepPacked")!=0&&tag.getByte("keepPacked")!=1))throw new IllegalArgumentException("Unknown Ramp engine envelope");
 }
 static boolean live(NativeRampPartEntity part){
  if(part.getLevel()!=null&&part.getLevel().isClientSide)return true;
  var values=part.components();int display=(values.has(DataComponents.CUSTOM_NAME)?1:0)+(values.has(DataComponents.ITEM_NAME)?1:0)+(values.has(DataComponents.LORE)?1:0);
  return values.size()==display&&!part.hasAttachments()&&part.getPersistentData().isEmpty();
 }
 private NativeRampPartEnvelope(){}
}
