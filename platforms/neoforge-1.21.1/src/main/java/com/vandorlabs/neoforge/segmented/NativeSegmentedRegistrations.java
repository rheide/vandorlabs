package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Canonical Segmented Door definitions; initialize before attaching the shared deferred registers. */
public final class NativeSegmentedRegistrations {
    public static final String ID="segmented_door";
    public static final DeferredHolder<Block,NativeSegmentedDoorBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,NativeSegmentedRegistrations::block);
    public static final DeferredHolder<Item,NativeSegmentedItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeSegmentedItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeSegmentedDoorEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeSegmentedDoorEntity>of((pos,state)->new NativeSegmentedDoorEntity(NativeSegmentedRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeSegmentedMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeSegmentedMenu(NativeSegmentedRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
    private NativeSegmentedRegistrations(){}
    private static NativeSegmentedDoorBlock block(){return new NativeSegmentedDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops().isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)){
        @Override protected BlockEntityType<?> entityType(){return ENTITY.get();}
        @Override protected MenuType<NativeSegmentedMenu> menuType(){return MENU.get();}
    };}
    public static void register(IEventBus bus){bus.addListener(SegmentedSettingsPayload::register);bus.addListener(SegmentedUsePayload::register);}
}
