package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Nine-part Large Door using the common Door editor, credentials and renderer. */
public final class NativeLargeDoorRegistrations {
    public static final DeferredHolder<Block,NativeLargeDoorBlock> BLOCK=VandorLabsBootstrap.BLOCKS.register(NativeDoorSettings.LARGE_ID,()->new NativeLargeDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5,6).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops(),()->NativeLargeDoorRegistrations.ENTITY.get(),()->NativeDoorRegistrations.MENU.get(),()->NativeLargeDoorRegistrations.ITEM.get()));
    public static final DeferredHolder<Item,NativeLargeDoorItem> ITEM=VandorLabsBootstrap.ITEMS.register(NativeDoorSettings.LARGE_ID,()->new NativeLargeDoorItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NativeLargeDoorEntity>> ENTITY=VandorLabsBootstrap.ENTITIES.register(NativeDoorSettings.LARGE_ID,()->BlockEntityType.Builder.<NativeLargeDoorEntity>of((pos,state)->new NativeLargeDoorEntity(NativeLargeDoorRegistrations.ENTITY.get(),pos,state),BLOCK.get()).build(null));
    private NativeLargeDoorRegistrations(){}
    /** Initialize definitions before the shared deferred registers attach to the mod bus. */
    public static void register(){}
}
