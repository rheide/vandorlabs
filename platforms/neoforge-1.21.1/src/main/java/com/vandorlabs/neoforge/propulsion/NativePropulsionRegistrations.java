package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.propulsion.PropulsionDefinition;
import java.util.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Released standalone engine identities; registration is explicitly attached by the platform bootstrap. */
public final class NativePropulsionRegistrations {
 public static final Map<String,DeferredHolder<Block,NativePropulsionBlock>> BLOCKS;
 public static final Map<String,DeferredHolder<Item,NativePropulsionItem>> ITEMS;
 static {
  var blocks=new LinkedHashMap<String,DeferredHolder<Block,NativePropulsionBlock>>();
  for(var definition:PropulsionDefinition.values())blocks.put(definition.id,VandorLabsBootstrap.BLOCKS.register(definition.id,()->new Registered(definition.id)));
  BLOCKS=Collections.unmodifiableMap(blocks);
  var items=new LinkedHashMap<String,DeferredHolder<Item,NativePropulsionItem>>();
  for(var definition:PropulsionDefinition.values())if(definition.shape==0)items.put(definition.id,VandorLabsBootstrap.ITEMS.register(definition.id,()->new NativePropulsionItem(BLOCKS.get(definition.id).get(),new Item.Properties())));
  ITEMS=Collections.unmodifiableMap(items);
 }
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativePropulsionEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register("redstone_light",()->BlockEntityType.Builder.<NativePropulsionEntity>of((pos,state)->new NativePropulsionEntity(NativePropulsionRegistrations.ENTITY.get(),pos,state),BLOCKS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<NativePropulsionMenu>> MENU=VandorLabsBootstrap.MENUS.register("redstone_light",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativePropulsionMenu(NativePropulsionRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 private static final class Registered extends NativePropulsionBlock {
  Registered(String id){super(id,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().isRedstoneConductor((s,w,p)->false),()->ENTITY.get(),name->{var holder=BLOCKS.get(name);return holder==null?null:holder.get();});}
  @Override protected MenuType<NativePropulsionMenu> menuType(){return MENU.get();}
 }
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(PropulsionSettingsPayload::register);}
 private NativePropulsionRegistrations(){}
}
