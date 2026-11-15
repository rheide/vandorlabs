package com.vandorlabs.neoforge.surface;

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

/** Canonical standalone Surface registration, independent of Vehicle Mode and controller adapters. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeSurfaceBindings {
    public static NativeSurfaceBlock BLOCK;
    public static BlockEntityType<NativeSurfaceEntity> ENTITY;
    public static MenuType<NativeSurfaceMenu> MENU;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.turntables","true"));}
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,helper->{BLOCK=new NativeSurfaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).requiresCorrectToolForDrops().sound(SoundType.METAL));helper.register(id("turntable_surface"),BLOCK);});
        event.register(Registries.ITEM,helper->helper.register(id("turntable_surface"),new NativeSurfaceItem(BLOCK,new Item.Properties())));
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{ENTITY=BlockEntityType.Builder.of((pos,state)->new NativeSurfaceEntity(ENTITY,pos,state),BLOCK).build(null);helper.register(id("turntable_surface"),ENTITY);});
        event.register(Registries.MENU,helper->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeSurfaceMenu(MENU,window,inventory,buffer.readBlockPos()));helper.register(id("turntable_surface"),MENU);});
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())NativeSurfaceSettingsPayload.register(event);}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs")))event.accept(BLOCK);}
    public static boolean open(Player actor,Level level,net.minecraft.core.BlockPos pos){if(!enabled()||MENU==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)||tile.inert()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,tile))return false;if(actor instanceof ServerPlayer player){if(!player.getServer().isSameThread())return false;player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeSurfaceMenu(MENU,window,tile),tile.getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(pos));}return true;}
    private NativeSurfaceBindings(){}
}
