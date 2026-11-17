package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.appliance.ApplianceCatalog;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.HousingSettings;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Four ordinary configured building items. No separate AC block or item identities. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeAirConditionerPresets {
 private static final int COUNT=4;
 private static final class Materials {
  static final int[][] FACES=resolve();
  private static int[][] resolve(){var result=new int[COUNT][6];for(int preset=0;preset<COUNT;preset++)for(int face=0;face<6;face++){String id="appliance_"+ApplianceCatalog.IDS[preset]+"_"+ApplianceCatalog.FACES[face];int found=-1;for(int choice=0;choice<ModernFinishCatalog.ENTRIES.size();choice++)if(ModernFinishCatalog.ENTRIES.get(choice).id().equals(id)){found=ModernFinishCatalog.storedChoice(choice);break;}if(found<0)throw new IllegalStateException("Missing AC housing finish "+id);result[preset][face]=found;}return result;}
 }
 public static int face(int preset,int face){if(!ApplianceCatalog.preset(preset)||face<0||face>=6)throw new IllegalArgumentException("AC face");return Materials.FACES[preset][face];}
 public static ItemStack stack(int preset){if(!ApplianceCatalog.preset(preset))throw new IllegalArgumentException("AC preset");var result=new ItemStack(ApplianceCatalog.slab(preset)?VandorLabsBootstrap.PROGRAMMABLE_SLAB_ITEM.get():VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var settings=new CompoundTag();settings.putString("id",net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get()).toString());HousingSettings.write(new ModernHousingTags(settings),face(preset,2),-1,0,false,true,Materials.FACES[preset].clone());result.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(settings));return result;}
 public static List<ItemStack> stacks(){var result=new ArrayList<ItemStack>(COUNT);for(int i=0;i<COUNT;i++)result.add(stack(i));return List.copyOf(result);}
 /** Only exact complete appliance configurations acquire appliance names and static previews. */
 public static int preset(ItemStack stack){boolean slab=stack.is(VandorLabsBootstrap.PROGRAMMABLE_SLAB_ITEM.get());if(!slab&&!stack.is(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get()))return -1;var component=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(component==null)component=stack.get(DataComponents.CUSTOM_DATA);if(component==null)return -1;var tag=component.getUnsafe();if(tag.contains("BlockEntityTag",10))tag=tag.getCompound("BlockEntityTag");if(!tag.getBoolean(HousingSettings.FACES_ENABLED)||tag.getInt(HousingSettings.LAYOUT)!=0||!tag.contains(HousingSettings.LAYOUT,3)||tag.getBoolean(HousingSettings.TILE_SIDES))return -1;int[] faces=tag.getIntArray(HousingSettings.FACES);if(faces.length!=6)return -1;for(int i=0;i<COUNT;i++)if(ApplianceCatalog.slab(i)==slab&&Arrays.equals(faces,Materials.FACES[i]))return i;return -1;}
 public static Component name(ItemStack stack){int preset=preset(stack);return preset<0?null:Component.translatable("item.vandorlabs.preset."+ApplianceCatalog.IDS[preset]);}
 @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("vandorlabs","vandorlabs")))for(var stack:stacks())event.accept(stack);}
 private NativeAirConditionerPresets(){}
}
