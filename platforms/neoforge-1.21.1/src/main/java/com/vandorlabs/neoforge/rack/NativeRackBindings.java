package com.vandorlabs.neoforge.rack;

import net.minecraft.core.BlockPos;
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

/** Explicit Rack adapter; navigation on native Vehicles remains outside this registration. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeRackBindings {
    public static NativeRackBlock BLOCK;
    public static Item BLANK,AUTOPILOT;
    public static BlockEntityType<NativeRackEntity> ENTITY;
    public static MenuType<NativeRackMenu> MENU;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.moduleRack","true"));}
    private static ResourceLocation id(String value){return ResourceLocation.fromNamespaceAndPath("vandorlabs",value);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,helper->{BLOCK=new NativeRackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).requiresCorrectToolForDrops().noOcclusion().sound(SoundType.METAL));helper.register(id("module_rack"),BLOCK);});
        event.register(Registries.ITEM,helper->{helper.register(id("module_rack"),new NativeRackItem(BLOCK,new Item.Properties()));BLANK=new Item(new Item.Properties());AUTOPILOT=new Item(new Item.Properties());helper.register(id("blank_module"),BLANK);helper.register(id("autopilot_module"),AUTOPILOT);});
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{ENTITY=BlockEntityType.Builder.of((BlockPos pos,net.minecraft.world.level.block.state.BlockState state)->new NativeRackEntity(ENTITY,pos,state),BLOCK).build(null);helper.register(id("module_rack"),ENTITY);});
        event.register(Registries.MENU,helper->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeRackMenu(MENU,window,inventory,buffer.readBlockPos()));helper.register(id("module_rack"),MENU);});
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())NativeRackSettingsPayload.register(event);}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs"))){event.accept(BLOCK);event.accept(BLANK);event.accept(AUTOPILOT);}}
    public static boolean open(Player player,Level level,BlockPos pos){
        if(!enabled()||MENU==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeRackEntity owner)||owner.inert()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,owner))return false;
        if(player instanceof ServerPlayer actor){if(!actor.getServer().isSameThread())return false;actor.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeRackMenu(MENU,window,owner),owner.getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(pos));}return true;
    }
    private NativeRackBindings(){}
}
