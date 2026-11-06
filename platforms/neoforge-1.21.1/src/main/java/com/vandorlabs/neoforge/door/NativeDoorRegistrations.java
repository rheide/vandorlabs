package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Ordinary Door registrations. Intentionally not installed until complete renderer/client acceptance. */
public final class NativeDoorRegistrations {
    public static final DeferredHolder<Block,NativeOrdinaryDoorBlock> ORDINARY=VandorLabsBootstrap.BLOCKS.register(NativeDoorSettings.ORDINARY_ID,()->block(true));
    public static final DeferredHolder<Block,NativeOrdinaryDoorBlock> LEGACY_ROTATING=VandorLabsBootstrap.BLOCKS.register(NativeDoorSettings.LEGACY_ROTATING_ID,()->block(false));
    public static final DeferredHolder<Block,NativeOrdinaryDoorBlock> LEGACY_SLIDING=VandorLabsBootstrap.BLOCKS.register(NativeDoorSettings.LEGACY_SLIDING_ID,()->block(true));
    public static final DeferredHolder<Item,NativeDoorItem> ITEM=VandorLabsBootstrap.ITEMS.register(NativeDoorSettings.ORDINARY_ID,()->new NativeDoorItem(ORDINARY.get(),new Item.Properties()));
    public static final DeferredHolder<Item,NativeDoorItem> LEGACY_ROTATING_ITEM=VandorLabsBootstrap.ITEMS.register(NativeDoorSettings.LEGACY_ROTATING_ID,()->new NativeDoorItem(LEGACY_ROTATING.get(),new Item.Properties()));
    public static final DeferredHolder<Item,NativeDoorItem> LEGACY_SLIDING_ITEM=VandorLabsBootstrap.ITEMS.register(NativeDoorSettings.LEGACY_SLIDING_ID,()->new NativeDoorItem(LEGACY_SLIDING.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeProgrammableDoorEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(NativeDoorSettings.ORDINARY_ID,()->BlockEntityType.Builder.<NativeProgrammableDoorEntity>of((pos,state)->{
        if(!(state.getBlock() instanceof NativeOrdinaryDoorBlock block))throw new IllegalArgumentException("Ordinary Door block required");return new NativeProgrammableDoorEntity(NativeDoorRegistrations.ENTITY.get(),pos,state,false,block.slidingModel());
    },ORDINARY.get(),LEGACY_ROTATING.get(),LEGACY_SLIDING.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeDoorMenu>> MENU=VandorLabsBootstrap.MENUS.register(NativeDoorSettings.ORDINARY_ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeDoorMenu(NativeDoorRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
    private NativeDoorRegistrations(){}
    private static NativeOrdinaryDoorBlock block(boolean sliding){return new NativeOrdinaryDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops(),()->ENTITY.get(),()->MENU.get(),()->ITEM.get(),sliding);}
    /** Call during mod construction, before the shared deferred registers are attached to their bus. */
    public static void register(IEventBus bus){bus.addListener(DoorPayloads::register);}
}
