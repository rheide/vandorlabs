package com.vandorlabs.neoforge.builders;

import com.mojang.serialization.Dynamic;
import com.vandorlabs.credentials.CatalogChipPolicy;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One inert block template, with no tile ownership, inventories, capabilities or opaque components. */
public final class NativeBlockRecipeData {
 public static final String ID="digitized_block";public static final int VERSION=1,MAX_BYTES=16384;
 private static final Set<String> APPEARANCE=Set.of("housingTexture","HousingTexture","HousingTextureVersion","SideTexture","MainTextureLayout","SlabTileSides","FaceTexturesEnabled","FaceTextures","SurfaceTextureLayout","SurfaceTileSides","SelectedScreen","PrimarySurfaceTexture","SecondarySurfaceTexture","MainTexture","TextureVersion","PropulsionShape","PropulsionSideTexture","ParticleLevel","ParticleStream","SignalBrightness","ParticleThreshold","RedstoneInverted","PropulsionJoin","RedstoneChannel","RedstoneChannels","ManualOn");
 private static final Set<String> ARRAYS=Set.of("FaceTextures","RedstoneChannels");
 private static HolderLookup.Provider registries(){return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);}
 public static BlockState state(ItemStack stack){if(!(stack.getItem() instanceof BlockItem item))throw new IllegalArgumentException("A block item is required");var properties=stack.get(DataComponents.BLOCK_STATE);return properties==null?item.getBlock().defaultBlockState():properties.apply(item.getBlock().defaultBlockState());}
 public static boolean eligible(ItemStack stack){
  if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item)||stack.getCount()<1||stack.getCount()>stack.getMaxStackSize())return false;
  try{var block=item.getBlock();var id=BuiltInRegistries.BLOCK.getKey(block);if(block==Blocks.AIR||id==null||!BuiltInRegistries.BLOCK.containsKey(id))return false;var state=state(stack);
   if(id.getNamespace().equals("vandorlabs")){
    String name=id.getPath();boolean allowed=block instanceof com.vandorlabs.neoforge.ProgrammableBlock||block instanceof com.vandorlabs.neoforge.shape.NativeProgrammableSlab||block instanceof NativeBuilderCoreBlock||block instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverCoreBlock||block instanceof com.vandorlabs.neoforge.propulsion.NativePropulsionBlock||block instanceof com.vandorlabs.neoforge.light.NativeLightBlock||block instanceof com.vandorlabs.neoforge.ramp.NativeRampBlock||block instanceof com.vandorlabs.neoforge.screen.NativeScreenBlock&&(name.endsWith("screen")||name.endsWith("console"));if(!allowed)return false;
   }else if(id.getNamespace().equals("minecraft")){
    if(block instanceof DoorBlock||block instanceof BedBlock||block instanceof BushBlock||block instanceof CactusBlock||block instanceof SugarCaneBlock||block instanceof VineBlock||block instanceof HugeMushroomBlock||block instanceof PumpkinBlock||block instanceof CarvedPumpkinBlock||block==Blocks.MELON||block instanceof PistonHeadBlock||block instanceof MovingPistonBlock||state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.getRenderShape()!=RenderShape.MODEL)return false;
    if(!(block instanceof SlabBlock||block instanceof TrapDoorBlock)){var shape=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);if(shape.isEmpty())return false;var b=shape.bounds();if(b.minX!=0||b.minY!=0||b.minZ!=0||b.maxX!=1||b.maxY!=1||b.maxZ!=1)return false;}
   }else return false;
   for(var entry:stack.getComponentsPatch().entrySet()){
    var key=entry.getKey();if(entry.getValue().isEmpty())return false;
    if(key==DataComponents.BLOCK_STATE){var p=stack.get(DataComponents.BLOCK_STATE);for(var property:p.properties().entrySet()){var definition=block.getStateDefinition().getProperty(property.getKey());if(definition==null||definition.getValue(property.getValue()).isEmpty()||property.getKey().equals("formed")&&!property.getValue().equals("false"))return false;}}
    else if(key==DataComponents.CUSTOM_NAME){var text=stack.get(DataComponents.CUSTOM_NAME);if(text.getString().length()>256||!text.equals(Component.literal(text.getString())))return false;}
    else if(key==DataComponents.BLOCK_ENTITY_DATA){if(!id.getNamespace().equals("vandorlabs")||!appearance(stack.get(DataComponents.BLOCK_ENTITY_DATA).getUnsafe(),true))return false;}
    else if(key==DataComponents.CUSTOM_DATA){var tag=stack.get(DataComponents.CUSTOM_DATA).getUnsafe();if(!id.getNamespace().equals("vandorlabs")||!tag.getAllKeys().equals(Set.of("BlockEntityTag"))||!tag.contains("BlockEntityTag",Tag.TAG_COMPOUND)||!appearance(tag.getCompound("BlockEntityTag"),false))return false;}
    else return false;
   }
   return NativeInventoryItems.write(stack.copyWithCount(1),registries()).sizeInBytes()<=MAX_BYTES;
  }catch(RuntimeException unsafe){return false;}
 }
 private static boolean appearance(CompoundTag tag,boolean nativeComponent){if(tag.sizeInBytes()>MAX_BYTES)return false;for(String key:tag.getAllKeys()){
  if(nativeComponent&&key.equals("id")){if(!tag.contains(key,Tag.TAG_STRING)||!tag.getString(key).startsWith("vandorlabs:"))return false;continue;}
  if(!APPEARANCE.contains(key))return false;if(ARRAYS.contains(key)){if(!tag.contains(key,Tag.TAG_INT_ARRAY)||tag.getIntArray(key).length>(key.equals("FaceTextures")?6:64))return false;}
  else if(!tag.contains(key,Tag.TAG_INT)&&!tag.contains(key,Tag.TAG_BYTE))return false;
 }return true;}
 public static ItemStack read(ItemStack chip){return read(chip,registries());}
 public static ItemStack read(ItemStack chip,HolderLookup.Provider registries){if(!NativeCredentialItems.accepts(chip,true))return ItemStack.EMPTY;var tag=NativeChipTypes.data(chip);if(!tag.getString("ChipType").equals("catalog")||!tag.contains("CatalogBlock",Tag.TAG_COMPOUND)||!tag.contains("CatalogName",Tag.TAG_STRING)||tag.getString("CatalogName").length()>40)return ItemStack.EMPTY;var catalog=tag.getCompound("Catalog");var designs=catalog.getCompound("Designs");var ids=designs.getList("synthesis_tower",Tag.TAG_STRING);if(!catalog.contains("Version",Tag.TAG_INT)||catalog.getInt("Version")!=1||ids.size()!=1||!ids.getString(0).equals(ID))return ItemStack.EMPTY;try{return decode(tag.getCompound("CatalogBlock"),registries);}catch(RuntimeException invalid){return ItemStack.EMPTY;}}
 public static CompoundTag publicData(ItemStack chip){var stack=read(chip);if(stack.isEmpty())return null;return encode(stack,registries());}
 public static ItemStack readPublic(CompoundTag tag){return decode(tag,registries());}
 private static ItemStack decode(CompoundTag tag,HolderLookup.Provider registries){
  if(!tag.getAllKeys().equals(Set.of("Version","Stack","State"))||!tag.contains("Version",Tag.TAG_INT)||tag.getInt("Version")!=VERSION||!tag.contains("Stack",Tag.TAG_COMPOUND)||!tag.contains("State",Tag.TAG_COMPOUND)||tag.sizeInBytes()>MAX_BYTES)throw new IllegalArgumentException("Block recipe schema");
  var raw=tag.getCompound("Stack");boolean legacy=raw.contains("Count",Tag.TAG_BYTE);ItemStack stack=legacy?legacy(raw,tag.getCompound("State"),registries):NativeInventoryItems.modern(raw,registries);if(!legacy&&!raw.equals(NativeInventoryItems.write(stack,registries)))throw new IllegalArgumentException("Noncanonical block recipe stack");if(stack.getCount()!=1||!eligible(stack))throw new IllegalArgumentException("Unsafe block recipe");
  if(!legacy&&!NbtUtils.writeBlockState(state(stack)).equals(tag.getCompound("State")))throw new IllegalArgumentException("Block recipe state mismatch");return stack.copy();
 }
 private static CompoundTag encode(ItemStack stack,HolderLookup.Provider registries){var tag=new CompoundTag();tag.putInt("Version",VERSION);tag.put("Stack",NativeInventoryItems.write(stack.copyWithCount(1),registries));tag.put("State",NbtUtils.writeBlockState(state(stack)));if(tag.sizeInBytes()>MAX_BYTES)throw new IllegalArgumentException("Block recipe too large");return tag;}
 public static boolean canDigitizeChip(ItemStack chip){
  if(!NativeCredentialItems.accepts(chip,true))return false;
  var root=chip.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();
  var tag=root.contains("ChipType")?root:root.getCompound(NativeCredentialItems.LEGACY_KEY).getCompound("tag");
  return !CatalogChipPolicy.isMaster(tag.getString("ChipType"),tag.getString("CatalogName"),tag.contains("CatalogBlock",Tag.TAG_COMPOUND));
 }
 public static void write(ItemStack chip,ItemStack input,HolderLookup.Provider registries){
  if(!canDigitizeChip(chip))throw new IllegalArgumentException("Digitize requires a writable Data Chip. The Master Catalog is protected.");
  if(!eligible(input))throw new IllegalArgumentException("This block cannot be digitized");var template=encode(input,registries);var root=chip.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();for(String key:List.of("JournalTitle","JournalPages","JournalFont","NavigationLocation","NavigationMapPixels","VehicleDesign","VehicleDesignCodec","VehicleShape","VehicleName","Catalog","CatalogBlock","CatalogName"))root.remove(key);
  var catalog=new CompoundTag();catalog.putInt("Version",1);var designs=new CompoundTag();var ids=new ListTag();ids.add(StringTag.valueOf(ID));designs.put("synthesis_tower",ids);catalog.put("Designs",designs);root.putString("ChipType","catalog");String label=input.getHoverName().getString();root.putString("CatalogName",label.substring(0,Math.min(40,label.length())));root.put("Catalog",catalog);root.put("CatalogBlock",template);chip.set(DataComponents.CUSTOM_DATA,CustomData.of(root));chip.set(DataComponents.CUSTOM_NAME,Component.literal("Catalog Chip ("+root.getString("CatalogName")+")"));
 }
 private static ItemStack legacy(CompoundTag raw,CompoundTag oldState,HolderLookup.Provider registries){
  if(!raw.contains("Count",Tag.TAG_BYTE)||raw.getByte("Count")!=1||!raw.contains("Damage",Tag.TAG_SHORT)||raw.contains("ForgeCaps"))throw new IllegalArgumentException("Legacy recipe ownership");var id=ResourceLocation.tryParse(raw.getString("id"));if(id==null||raw.getShort("Damage")<0||raw.getShort("Damage")>15)throw new IllegalArgumentException("Legacy recipe identity");
  if(!oldState.getAllKeys().stream().allMatch(k->k.equals("Name")||k.equals("Properties"))||!oldState.contains("Name",Tag.TAG_STRING)||oldState.contains("Properties")&&!oldState.contains("Properties",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Legacy recipe palette");
  ItemStack result;BlockState desired;
  if(id.getNamespace().equals("minecraft")){
   if(raw.contains("tag")&&!raw.getCompound("tag").getAllKeys().stream().allMatch(k->k.equals("display")))throw new IllegalArgumentException("Legacy vanilla template data");result=NativeInventoryItems.legacy(raw,registries);var fixed=DataFixers.getDataFixer().update(References.BLOCK_STATE,net.minecraft.util.datafix.fixes.BlockStateData.upgradeBlockStateTag(new Dynamic<>(NbtOps.INSTANCE,oldState)),1451,SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();if(!(fixed instanceof CompoundTag stateTag))throw new IllegalArgumentException("Legacy recipe state");desired=NativeRawCell.decodePalette(registries,stateTag,Map.of());
  }else if(id.getNamespace().equals("vandorlabs")){
   for(String key:raw.getAllKeys())if(!Set.of("id","Count","Damage","tag","ForgeCaps").contains(key))throw new IllegalArgumentException("Legacy template field");var alias=com.vandorlabs.neoforge.navigation.observer.NativeObserverAliases.palette(oldState);var blockId=ResourceLocation.tryParse(alias.getString("Name"));if(blockId==null||!BuiltInRegistries.BLOCK.containsKey(blockId))throw new IllegalArgumentException("Unavailable legacy block recipe");var expected=new CompoundTag();expected.putString("Name",id.toString());expected=com.vandorlabs.neoforge.navigation.observer.NativeObserverAliases.palette(expected);if(!expected.getString("Name").equals(blockId.toString()))throw new IllegalArgumentException("Legacy item/state identity mismatch");var block=BuiltInRegistries.BLOCK.get(blockId);desired=block.defaultBlockState();var properties=alias.getCompound("Properties");for(String key:properties.getAllKeys()){var property=block.getStateDefinition().getProperty(key);if(property==null||!properties.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Unavailable legacy recipe state property");desired=with(desired,property,properties.getString(key));}if(desired!=legacyState(block,raw.getShort("Damage")))throw new IllegalArgumentException("Legacy metadata/state mismatch");result=new ItemStack(block);var tag=raw.getCompound("tag");for(String key:tag.getAllKeys())if(!key.equals("BlockEntityTag")&&!key.equals("display"))throw new IllegalArgumentException("Legacy template item data");if(tag.contains("BlockEntityTag")){if(!tag.contains("BlockEntityTag",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Legacy appearance type");var config=tag.getCompound("BlockEntityTag").copy();for(String key:List.of("id","x","y","z"))config.remove(key);if(!appearance(config,false))throw new IllegalArgumentException("Legacy template appearance");var custom=new CompoundTag();custom.put("BlockEntityTag",config);result.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));}if(tag.contains("display")){if(!tag.contains("display",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Legacy name envelope");var display=tag.getCompound("display");if(!display.getAllKeys().stream().allMatch(k->(k.equals("Name")||k.equals("LocName"))&&display.contains(k,Tag.TAG_STRING)&&display.getString(k).length()<=256))throw new IllegalArgumentException("Legacy template name");if(display.contains("Name",Tag.TAG_STRING))result.set(DataComponents.CUSTOM_NAME,Component.literal(display.getString("Name")));}
  }else throw new IllegalArgumentException("Foreign legacy block recipe");
  if(!(result.getItem() instanceof BlockItem item)||item.getBlock()!=desired.getBlock())throw new IllegalArgumentException("Legacy recipe stack/state disagreement");var values=new HashMap<String,String>();for(var entry:desired.getValues().entrySet())values.put(entry.getKey().getName(),propertyValue(entry.getKey(),entry.getValue()));if(!values.isEmpty())result.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(values));return result;
 }
 private static BlockState legacyState(Block block,int meta){
  if(block instanceof com.vandorlabs.neoforge.screen.NativeScreenBlock screen)return screen.state(screen.family().decode(meta));
  if(block instanceof com.vandorlabs.neoforge.ramp.NativeRampBlock ramp)return ramp.legacyState(meta);
  var state=block.defaultBlockState();boolean core=block instanceof NativeBuilderCoreBlock||block instanceof com.vandorlabs.neoforge.navigation.observer.NativeObserverCoreBlock;var facing=block.getStateDefinition().getProperty("facing");if(facing!=null){var face=core?Direction.from2DDataValue(meta&3):(meta&7)<6?Direction.from3DDataValue(meta&7):state.getValue((net.minecraft.world.level.block.state.properties.DirectionProperty)facing);state=with(state,facing,face.getSerializedName());}var half=block.getStateDefinition().getProperty("half");if(half!=null)state=with(state,half,(meta&8)==0?"bottom":"top");var formed=block.getStateDefinition().getProperty("formed");if(formed!=null)state=with(state,formed,(meta&4)==0?"false":"true");var powered=block.getStateDefinition().getProperty("powered");if(powered!=null)state=with(state,powered,(meta&8)==0?"false":"true");return state;
 }
 private static <T extends Comparable<T>> BlockState with(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Legacy recipe property")));}
 private static <T extends Comparable<T>> String propertyValue(net.minecraft.world.level.block.state.properties.Property<T> property,Comparable<?> value){return property.getName((T)value);}
 private NativeBlockRecipeData(){}
}
