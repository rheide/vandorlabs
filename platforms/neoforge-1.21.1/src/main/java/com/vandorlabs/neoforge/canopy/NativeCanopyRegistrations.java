package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import java.util.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Inactive canonical definitions. The default bootstrap deliberately does not initialize these definitions. */
public final class NativeCanopyRegistrations {
    public static final Map<String,DeferredHolder<Block,NativeCanopyBlock>> BLOCKS=blocks();
    public static final Map<String,DeferredHolder<Item,NativeCanopyItem>> ITEMS=items();
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeCanopyEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register("canopy",()->BlockEntityType.Builder.<NativeCanopyEntity>of((pos,state)->new NativeCanopyEntity(NativeCanopyRegistrations.ENTITY.get(),pos,state,((NativeCanopyBlock)state.getBlock()).kind()),BLOCKS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeCanopyMenu>> MENU=VandorLabsBootstrap.MENUS.register("canopy",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeCanopyMenu(NativeCanopyRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
    private static Map<String,DeferredHolder<Block,NativeCanopyBlock>> blocks(){var values=new LinkedHashMap<String,DeferredHolder<Block,NativeCanopyBlock>>();for(String kind:NativeCanopyKinds.IDS)values.put(kind,VandorLabsBootstrap.BLOCKS.register(kind,()->new NativeCanopyBlock(kind,()->ENTITY.get(),()->MENU.get(),BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.5F,1.5F).sound(SoundType.GLASS))));return Collections.unmodifiableMap(values);}
    private static Map<String,DeferredHolder<Item,NativeCanopyItem>> items(){var values=new LinkedHashMap<String,DeferredHolder<Item,NativeCanopyItem>>();BLOCKS.forEach((kind,block)->values.put(kind,VandorLabsBootstrap.ITEMS.register(kind,()->new NativeCanopyItem(block.get(),new Item.Properties()))));return Collections.unmodifiableMap(values);}
    private static boolean bound;
    public static void register(net.neoforged.bus.api.IEventBus bus){if(bound)return;bound=true;bus.addListener(CanopySettingsPayload::register);bus.addListener(CanopyUsePayload::register);}
    private NativeCanopyRegistrations(){}
}
