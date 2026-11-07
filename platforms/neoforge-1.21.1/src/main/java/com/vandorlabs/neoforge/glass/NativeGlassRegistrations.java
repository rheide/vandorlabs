package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Canonical definitions remain inactive until register is called before deferred-register attachment. */
public final class NativeGlassRegistrations {
 public static final String ID="programmable_glass";
 public static final DeferredHolder<Block,NativeGlassBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,Registered::new);
 public static final DeferredHolder<Item,NativeGlassItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeGlassItem(BLOCK.get(),new Item.Properties()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeGlassEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeGlassEntity>of((pos,state)->new NativeGlassEntity(NativeGlassRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<NativeGlassMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeGlassMenu(NativeGlassRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 private static final class Registered extends NativeGlassBlock {
  Registered(){super(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(.5F,1.2F).sound(SoundType.GLASS),()->ENTITY.get());}
  @Override protected MenuType<NativeGlassMenu> menuType(){return MENU.get();}
 }
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(GlassSettingsPayload::register);}
 private NativeGlassRegistrations(){}
}
