package com.vandorlabs.neoforge.navigation;
import com.vandorlabs.navigation.NavigationLayout;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeNavigationTableBindings {
 public static NativeNavigationTableBlock FLAT,ANGLED;public static BlockEntityType<NativeNavigationTableEntity> ENTITY;public static MenuType<NativeNavigationTableMenu> MENU;
 private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
 @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK,h->{FLAT=new NativeNavigationTableBlock(properties(),NavigationLayout.Kind.FLAT);ANGLED=new NativeNavigationTableBlock(properties(),NavigationLayout.Kind.ANGLED);h.register(id("navigation_table_flat"),FLAT);h.register(id("navigation_table_angled"),ANGLED);});event.register(Registries.ITEM,h->{h.register(id("navigation_table_flat"),new BlockItem(FLAT,new Item.Properties()));h.register(id("navigation_table_angled"),new BlockItem(ANGLED,new Item.Properties()));});event.register(Registries.BLOCK_ENTITY_TYPE,h->{ENTITY=BlockEntityType.Builder.of(NativeNavigationTableEntity::new,FLAT,ANGLED).build(null);h.register(id("navigation_table"),ENTITY);});event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeNavigationTableMenu(window,inventory,buffer.readBlockPos()));h.register(id("navigation_table"),MENU);});}
 private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3,6).noOcclusion().sound(SoundType.METAL);}
 @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(event.getTabKey().location().equals(id("vandorlabs"))){event.accept(FLAT);event.accept(ANGLED);}}
 @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){NativeNavigationTableSettingsPayload.register(e);}
 public static boolean open(Player actor,Level level,BlockPos pos){if(MENU==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeNavigationTableEntity t)||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,t))return false;if(actor instanceof ServerPlayer p){if(!p.getServer().isSameThread())return false;p.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeNavigationTableMenu(window,t),t.getBlockState().getBlock().getName()),b->b.writeBlockPos(pos));}return true;}
 private NativeNavigationTableBindings(){}
}
