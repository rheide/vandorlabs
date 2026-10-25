package com.vandorlabs.neoforge.table;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class TableRegistration {
    public static final DeferredHolder<Block,TableBlock> BLOCK=VandorLabsModern.BLOCKS.register("industrial_table",()->new TableBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().forceSolidOn().dynamicShape()));
    public static final DeferredHolder<Item,BlockItem> ITEM=VandorLabsModern.ITEMS.register("industrial_table",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static void initialize(){}
    public static ItemStack creativeItem(){return new ItemStack(ITEM.get());}
    private TableRegistration(){}
}
