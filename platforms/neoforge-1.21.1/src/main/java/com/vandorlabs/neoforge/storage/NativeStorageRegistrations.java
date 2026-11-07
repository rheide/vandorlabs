package com.vandorlabs.neoforge.storage;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
/** Canonical definitions initialize only when register is explicitly activated before deferred-register attachment. */
public final class NativeStorageRegistrations {
 public static final String ID="programmable_storage";
 public static final DeferredHolder<Block,NativeStorageBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,Registered::new);
 public static final DeferredHolder<Item,NativeStorageItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeStorageItem(BLOCK.get(),new Item.Properties()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeStorageEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeStorageEntity>of((pos,state)->new NativeStorageEntity(NativeStorageRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<NativeStorageMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeStorageMenu(NativeStorageRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 private static final class Registered extends NativeStorageBlock {
  Registered(){super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops(),()->ENTITY.get());}
  @Override protected MenuType<NativeStorageMenu> menuType(){return MENU.get();}
 }
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(StorageSettingsPayload::register);bus.addListener(NativeStorageRegistrations::capabilities);}
 private static void capabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event){event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,ENTITY.get(),(owner,side)->owner.itemHandler());}
 private NativeStorageRegistrations(){}
}
