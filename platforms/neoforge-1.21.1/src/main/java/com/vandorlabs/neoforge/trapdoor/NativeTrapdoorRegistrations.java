package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Canonical definitions; activate only before attaching the bootstrap's deferred registers. */
public final class NativeTrapdoorRegistrations {
    public static final String ID="programmable_trapdoor",DIAGONAL_ID="programmable_diagonal_trapdoor";
    public static final DeferredHolder<Block,NativeTrapdoorBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(ID,()->block(false));
    public static final DeferredHolder<Block,NativeTrapdoorBlock> DIAGONAL_BLOCK=VandorLabsBootstrap.BLOCKS.register(DIAGONAL_ID,()->block(true));
    public static final DeferredHolder<Item,NativeTrapdoorItem> ITEM=VandorLabsBootstrap.ITEMS.register(ID,()->new NativeTrapdoorItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<Item,NativeTrapdoorItem> DIAGONAL_ITEM=VandorLabsBootstrap.ITEMS.register(DIAGONAL_ID,()->new NativeTrapdoorItem(DIAGONAL_BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeTrapdoorEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(ID,()->BlockEntityType.Builder.<NativeTrapdoorEntity>of((pos,state)->new NativeTrapdoorEntity(NativeTrapdoorRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeTrapdoorEntity>> DIAGONAL_ENTITY=VandorLabsBootstrap.ENTITIES.register(DIAGONAL_ID,()->BlockEntityType.Builder.<NativeTrapdoorEntity>of((pos,state)->new NativeTrapdoorEntity(NativeTrapdoorRegistrations.DIAGONAL_ENTITY.get(),pos,state),DIAGONAL_BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<NativeTrapdoorMenu>> MENU=VandorLabsBootstrap.MENUS.register(ID,()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new NativeTrapdoorMenu(NativeTrapdoorRegistrations.MENU.get(),id,inventory,buffer.readBlockPos())));
    private NativeTrapdoorRegistrations(){}
    private static NativeTrapdoorBlock block(boolean diagonal){return new NativeTrapdoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops().isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false),diagonal){
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec(){return com.mojang.serialization.MapCodec.unit(this);}
        @Override protected BlockEntityType<?> entityType(){return diagonal?DIAGONAL_ENTITY.get():ENTITY.get();}
        @Override protected MenuType<NativeTrapdoorMenu> menuType(){return MENU.get();}
        @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    };}
    public static void register(IEventBus bus){bus.addListener(TrapdoorSettingsPayload::register);bus.addListener(TrapdoorUsePayload::register);}
}
