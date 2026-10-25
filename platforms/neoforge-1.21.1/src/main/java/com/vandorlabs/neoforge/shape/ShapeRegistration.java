package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ShapeRegistration {
    public static final DeferredHolder<Block,ProgrammableSlabBlock> SLAB=VandorLabsModern.BLOCKS.register("programmable_slab",()->new ProgrammableSlabBlock(properties()));
    public static final DeferredHolder<Block,ProgrammableStairsBlock> STAIRS=VandorLabsModern.BLOCKS.register("programmable_stairs",()->new ProgrammableStairsBlock(properties()));
    public static final DeferredHolder<Item,BlockItem> SLAB_ITEM=VandorLabsModern.ITEMS.register("programmable_slab",()->new ProgrammableSlabItem(SLAB.get(),new Item.Properties()));
    public static final DeferredHolder<Item,BlockItem> STAIR_ITEM=VandorLabsModern.ITEMS.register("programmable_stairs",()->new BlockItem(STAIRS.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ProgrammableShapeEntity>> ENTITY=VandorLabsModern.ENTITIES.register("programmable_shape",()->BlockEntityType.Builder.of(ProgrammableShapeEntity::new,SLAB.get(),STAIRS.get()).build(null));
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn();}
    public static void initialize(){ShapeMenu.initialize();}
    public static java.util.List<ItemStack> creativeItems(){return java.util.List.of(new ItemStack(SLAB_ITEM.get()),new ItemStack(STAIR_ITEM.get()));}
    private ShapeRegistration(){}
}
