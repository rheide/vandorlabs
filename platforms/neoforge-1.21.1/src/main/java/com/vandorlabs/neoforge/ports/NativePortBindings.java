package com.vandorlabs.neoforge.ports;

import java.util.*;
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
import net.neoforged.neoforge.capabilities.*;

/** Canonical native port registration; explicit false retains baseline fixtures. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativePortBindings {
    public static final Map<NativePortKind,NativePortBlock> BLOCKS=new EnumMap<>(NativePortKind.class);
    public static final Map<NativePortKind,BlockEntityType<NativeResourcePortEntity>> ENTITIES=new EnumMap<>(NativePortKind.class);
    public static MenuType<NativePortMenu> MENU;
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("vandorlabs.resourcePorts","true"));}
    private static ResourceLocation id(String value){return ResourceLocation.fromNamespaceAndPath("vandorlabs",value);}
    @SubscribeEvent public static void register(RegisterEvent event){if(!enabled())return;
        event.register(Registries.BLOCK,helper->{for(var kind:NativePortKind.values()){var block=new NativePortBlock(kind,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).requiresCorrectToolForDrops().sound(SoundType.METAL));BLOCKS.put(kind,block);helper.register(id(kind.id),block);}});
        event.register(Registries.ITEM,helper->{for(var kind:NativePortKind.values())helper.register(id(kind.id),new NativePortItem(BLOCKS.get(kind),new Item.Properties().stacksTo(1)));});
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{for(var kind:NativePortKind.values()){var type=BlockEntityType.Builder.<NativeResourcePortEntity>of((pos,state)->((NativePortBlock)state.getBlock()).newBlockEntity(pos,state) instanceof NativeResourcePortEntity owner?owner:null,BLOCKS.get(kind)).build(null);ENTITIES.put(kind,type);helper.register(id(kind.id),type);}});
        event.register(Registries.MENU,helper->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativePortMenu(MENU,window,inventory,buffer.readBlockPos()));helper.register(id("resource_port"),MENU);});
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){if(!enabled())return;for(var type:ENTITIES.values()){
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,type,(owner,side)->owner instanceof NativePowerPortEntity power?power.energy(side):null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,type,(owner,side)->owner instanceof NativeFluidPortEntity fluid?fluid.fluid(side):null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,type,(owner,side)->owner instanceof NativeMatterPortEntity matter?matter.items(side):null);
        }
    }
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativePortSettingsPayload.register(event);}
    @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event){if(enabled()&&event.getTabKey().location().equals(id("vandorlabs")))for(var block:BLOCKS.values())event.accept(block);}
    public static boolean open(Player player,Level level,BlockPos pos){if(!enabled()||MENU==null||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,level,pos)||!(level.getBlockEntity(pos) instanceof NativePortOwner owner)||owner.inert())return false;if(player instanceof ServerPlayer actor){if(!actor.getServer().isSameThread())return false;actor.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativePortMenu(MENU,window,owner),owner.portBlockEntity().getBlockState().getBlock().getName()),buffer->buffer.writeBlockPos(pos));}return true;}
    private NativePortBindings(){}
}
