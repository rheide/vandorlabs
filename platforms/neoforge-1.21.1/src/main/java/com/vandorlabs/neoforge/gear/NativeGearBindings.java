package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Canonical standalone arm bindings. An explicit false switch supports baseline fixtures. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeGearBindings {
    public static final Map<String,NativeGearBlock> BLOCKS=new LinkedHashMap<>();
    public static final Map<String,NativeGearItem> ITEMS=new LinkedHashMap<>();
    public static BlockEntityType<NativeGearEntity> ENTITY,TRACK_ENTITY;
    public static MenuType<NativeGearMenu> MENU;
    private static final boolean ENABLED=Boolean.parseBoolean(System.getProperty("vandorlabs.gearRuntime","true"));
    public static boolean enabled(){return ENABLED;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,helper->{for(var definition:NativeGearDefinition.available()){var block=definition.kind==NativeGearDefinition.Kind.TRACK?new com.vandorlabs.neoforge.gear.track.NativeTrackBlock(definition,0,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F,4.8F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),()->TRACK_ENTITY):new NativeGearBlock(definition,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F,8).sound(SoundType.METAL).noOcclusion().dynamicShape().isRedstoneConductor((s,w,p)->false),()->ENTITY);BLOCKS.put(definition.id,block);helper.register(id(definition.id),block);}if(com.vandorlabs.neoforge.gear.track.NativeTracks.enabled())for(int style=1;style<3;style++){String name=style==1?"armoured_track":"bogie_track";var block=new com.vandorlabs.neoforge.gear.track.NativeTrackBlock(NativeGearDefinition.TRACK,style,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F,4.8F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape(),()->TRACK_ENTITY);BLOCKS.put(name,block);helper.register(id(name),block);}});
        event.register(Registries.ITEM,helper->{for(var entry:BLOCKS.entrySet()){var item=new NativeGearItem(entry.getValue(),new Item.Properties());ITEMS.put(entry.getKey(),item);helper.register(id(entry.getKey()),item);}});
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{ENTITY=BlockEntityType.Builder.<NativeGearEntity>of((pos,state)->new NativeGearEntity(ENTITY,pos,state),BLOCKS.values().stream().filter(block->block.definition().kind!=NativeGearDefinition.Kind.TRACK).toArray(Block[]::new)).build(null);helper.register(id("modular_gear"),ENTITY);if(com.vandorlabs.neoforge.gear.track.NativeTracks.enabled()){TRACK_ENTITY=BlockEntityType.Builder.<NativeGearEntity>of((pos,state)->new com.vandorlabs.neoforge.gear.track.NativeTrackEntity(TRACK_ENTITY,pos,state),BLOCKS.values().stream().filter(block->block.definition().kind==NativeGearDefinition.Kind.TRACK).toArray(Block[]::new)).build(null);helper.register(id("track"),TRACK_ENTITY);}});
        event.register(Registries.MENU,helper->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeGearMenu(MENU,window,inventory,buffer.readBlockPos(),buffer.readUUID()));helper.register(id("modular_gear"),MENU);});
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){
        if(!enabled())return;
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,ENTITY,NativeGearResourceTransfer::energy);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,ENTITY,NativeGearResourceTransfer::fluid);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENTITY,NativeGearResourceTransfer::items);
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativeGearSettingsPayload.register(event);NativeGearMechanismUsePayload.register(event);NativeGearHarvestPayload.register(event);}
    public static boolean open(Player player,Level world,BlockPos pos){if(world.hasChunkAt(pos)&&world.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.gear.track.NativeTrackEntity)return com.vandorlabs.neoforge.gear.track.NativeTracks.open(player,world,pos);if(!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,world,pos))return false;var member=NativeGearFormation.at(world,pos);return member!=null&&openSelected(player,member);}
    public static boolean openRoot(Player player,NativeGearEntity root){return openSelected(player,root);}
    private static boolean openSelected(Player player,NativeGearEntity member){var root=member==null?null:member.controllerEntity();if(!enabled()||MENU==null||root==null||!com.vandorlabs.neoforge.NativeConfigurationAccess.canConfigure(player)||!NativeGearAccess.menu(player,root))return false;if(player instanceof ServerPlayer actor){if(!actor.getServer().isSameThread())return false;root.authorize(actor);actor.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeGearMenu(MENU,window,member),root.getBlockState().getBlock().getName()),buffer->{buffer.writeBlockPos(member.getBlockPos());buffer.writeUUID(member.identity());});}return true;}
    private NativeGearBindings(){}
}
