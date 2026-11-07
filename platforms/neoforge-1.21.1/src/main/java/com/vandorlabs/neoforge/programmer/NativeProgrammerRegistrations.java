package com.vandorlabs.neoforge.programmer;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
/** Original single block/item/entity identity; only the root owns inventory. */
public final class NativeProgrammerRegistrations {
 public static final String ID="data_chip_programmer";
 public static final DeferredHolder<Block,NativeProgrammerBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,Registered::new);
 public static final DeferredHolder<Item,NativeProgrammerItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeProgrammerItem(BLOCK.get(),new Item.Properties()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeProgrammerEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeProgrammerEntity>of((pos,state)->new NativeProgrammerEntity(NativeProgrammerRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<NativeProgrammerMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeProgrammerMenu(NativeProgrammerRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
 private static final class Registered extends NativeProgrammerBlock {
  Registered(){super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F,3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops(),()->ENTITY.get());}
  @Override protected MenuType<NativeProgrammerMenu> menuType(){return MENU.get();}
 }
 public static void register(net.neoforged.bus.api.IEventBus bus){bus.addListener(ProgrammerSettingsPayload::register);}
 private NativeProgrammerRegistrations(){}
}
