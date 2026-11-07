package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.screen.ScreenFamily;
import java.util.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The original seven roles share the existing programmable_viewscreen entity identity. */
public final class NativeScreenRegistrations {
 public static final Map<ScreenFamily,DeferredHolder<Block,NativeScreenBlock>> BLOCKS;
 public static final Map<ScreenFamily,DeferredHolder<Item,NativeScreenItem>> ITEMS;
 public static final DeferredHolder<MenuType<?>,MenuType<NativeScreenMenu>> MENU=VandorLabsBootstrap.MENUS.register("programmable_viewscreen",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeScreenMenu(NativeScreenRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 static {
  var blocks=new EnumMap<ScreenFamily,DeferredHolder<Block,NativeScreenBlock>>(ScreenFamily.class);
  var items=new EnumMap<ScreenFamily,DeferredHolder<Item,NativeScreenItem>>(ScreenFamily.class);
  for(var family:ScreenFamily.values()){
   var block=VandorLabsBootstrap.BLOCKS.register(family.id,()->NativeScreenBlock.create(family,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().isRedstoneConductor((state,level,pos)->family==ScreenFamily.SCREEN),()->VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),()->MENU.get()));
   blocks.put(family,block);items.put(family,VandorLabsBootstrap.ITEMS.register(family.id,()->new NativeScreenItem(block.get(),new Item.Properties())));
  }
  BLOCKS=Collections.unmodifiableMap(blocks);ITEMS=Collections.unmodifiableMap(items);
 }
 public static Block[] blocks(){return BLOCKS.values().stream().map(DeferredHolder::get).toArray(Block[]::new);}
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(ScreenSettingsPayload::register);bus.addListener(ScreenPickPayload::register);}
 private NativeScreenRegistrations(){}
}
