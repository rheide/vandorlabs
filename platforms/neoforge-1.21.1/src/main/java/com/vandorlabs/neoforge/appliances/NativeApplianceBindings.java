package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.appliance.ApplianceCatalog;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Only the Printer and Scanner have identities; air conditioners are ordinary configured items. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeApplianceBindings {
 public static net.minecraft.world.level.block.entity.BlockEntityType<NativeApplianceEntity> ENTITY;public static net.minecraft.world.inventory.MenuType<NativeApplianceMenu> MENU;
 public static final Map<String,NativeApplianceBlock> BLOCKS=new LinkedHashMap<>();
 private static ResourceLocation id(String value){return ResourceLocation.fromNamespaceAndPath("vandorlabs",value);}
 @SubscribeEvent public static void register(RegisterEvent event){
  event.register(Registries.BLOCK,h->{for(int index=0;index<ApplianceCatalog.IDS.length;index++)if(!ApplianceCatalog.preset(index)){String name=ApplianceCatalog.IDS[index];var block=new NativeApplianceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3,6).requiresCorrectToolForDrops(),name.equals("scanner"));BLOCKS.put(name,block);h.register(id(name),block);}});
  event.register(Registries.ITEM,h->{for(var entry:BLOCKS.entrySet())h.register(id(entry.getKey()),new BlockItem(entry.getValue(),new Item.Properties()));});
  event.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(NativeApplianceEntity::new,BLOCKS.values().toArray(Block[]::new)).build(null);h.register(id("appliance"),ENTITY);NativeApplianceHooks.PRODUCT=NativeApplianceProducts::activate;NativeApplianceHooks.DISPLAY=NativeApplianceProducts::display;});
  event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeApplianceMenu(window,inventory,buffer.readBlockPos()));h.register(id("appliance"),MENU);});
 }
 @SubscribeEvent public static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event){
  event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,ENTITY,NativeApplianceEntity::itemPort);
 }
 @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().equals(id("vandorlabs")))for(var block:BLOCKS.values())event.accept(block);}
 public static boolean open(net.minecraft.world.entity.player.Player player,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos){if(MENU==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeApplianceEntity t)||t.inert()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,t))return false;if(player instanceof net.minecraft.server.level.ServerPlayer actor){if(!t.usable(actor))return false;NativeApplianceHooks.display(actor,t);actor.openMenu(new net.minecraft.world.SimpleMenuProvider((window,inventory,ignored)->new NativeApplianceMenu(window,inventory,t),t.getBlockState().getBlock().getName()),b->b.writeBlockPos(pos));}return true;}
 private NativeApplianceBindings(){}
}
