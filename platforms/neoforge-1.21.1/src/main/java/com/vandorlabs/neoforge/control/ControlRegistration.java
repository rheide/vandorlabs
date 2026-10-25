package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ControlRegistration {
    public static final DeferredHolder<Block,MountedControlBlock> BUTTON=VandorLabsModern.BLOCKS.register("push_button",()->new MountedControlBlock(true,properties()));
    public static final DeferredHolder<Block,MountedControlBlock> ROCKER=VandorLabsModern.BLOCKS.register("rocker_switch",()->new MountedControlBlock(false,properties()));
    static {VandorLabsModern.ITEMS.register("push_button",()->new BlockItem(BUTTON.get(),new Item.Properties()));VandorLabsModern.ITEMS.register("rocker_switch",()->new BlockItem(ROCKER.get(),new Item.Properties()));}
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MountedControlEntity>> ENTITY=VandorLabsModern.ENTITIES.register("redstone_channel_control",()->BlockEntityType.Builder.of(MountedControlEntity::new,BUTTON.get(),ROCKER.get()).build(null));
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn().lightLevel(state->state.getValue(MountedControlBlock.ON)?5:0);}
    public static void initialize(){ControlMenu.initialize();}
    public static java.util.List<ItemStack> creativeItems(){return java.util.List.of(new ItemStack(BUTTON.get()),new ItemStack(ROCKER.get()));}
    private ControlRegistration(){}
}
