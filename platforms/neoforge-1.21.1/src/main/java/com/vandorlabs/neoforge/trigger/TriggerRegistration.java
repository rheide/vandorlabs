package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class TriggerRegistration {
    public static final DeferredHolder<Block,TriggerBlock> BLOCK=VandorLabsModern.BLOCKS.register("programmable_trigger_block",()->new TriggerBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item,BlockItem> ITEM=VandorLabsModern.ITEMS.register("programmable_trigger_block",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<TriggerEntity>> ENTITY=VandorLabsModern.ENTITIES.register("programmable_trigger",()->BlockEntityType.Builder.of(TriggerEntity::new,BLOCK.get()).build(null));
    public static void initialize(){TriggerMenu.initialize();}
    public static ItemStack creativeItem(){return new ItemStack(ITEM.get());}
    private TriggerRegistration(){}
}
