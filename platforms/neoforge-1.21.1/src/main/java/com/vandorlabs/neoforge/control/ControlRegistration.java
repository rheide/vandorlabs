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
    public static final DeferredHolder<Block,MountedPowerLeverBlock> INDUSTRIAL_POWER=power("industrial_power_lever",false,1),COMPACT_POWER=power("compact_power_lever",false,0),SMALL_POWER=power("small_power_lever",true,0),LARGE_POWER=power("large_power_lever",true,1);
    public static final DeferredHolder<Block,MountedSignalControlBlock> THRUSTER=signal("thruster_lever",0),AIRLINER=signal("airliner_throttle",1),FIGHTER=signal("fighter_throttle",2),WALL_SLIDER=signal("wall_slider",-1);
    public static final java.util.List<DeferredHolder<Block,? extends MountedControlBlock>> ALL=java.util.List.of(BUTTON,ROCKER,INDUSTRIAL_POWER,COMPACT_POWER,SMALL_POWER,LARGE_POWER,THRUSTER,AIRLINER,FIGHTER,WALL_SLIDER);
    static {for(var block:ALL)VandorLabsModern.ITEMS.register(block.getId().getPath(),()->new BlockItem(block.get(),new Item.Properties()));}
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MountedControlEntity>> ENTITY=VandorLabsModern.ENTITIES.register("redstone_channel_control",()->BlockEntityType.Builder.of(MountedControlEntity::new,ALL.stream().map(holder->holder.get()).toArray(Block[]::new)).build(null));
    private static DeferredHolder<Block,MountedPowerLeverBlock> power(String id,boolean twin,int size){return VandorLabsModern.BLOCKS.register(id,()->new MountedPowerLeverBlock(twin,size,BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.NONE).strength(.5F,0).sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion().dynamicShape().forceSolidOff().lightLevel(state->0)));}
    private static DeferredHolder<Block,MountedSignalControlBlock> signal(String id,int type){return VandorLabsModern.BLOCKS.register(id,()->new MountedSignalControlBlock(type,BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn().lightLevel(state->0)));}
    public static MountedSignalControlBlock signal(int type){return switch(type){case 1->AIRLINER.get();case 2->FIGHTER.get();default->THRUSTER.get();};}
    private static BlockBehaviour.Properties properties(){return BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.METAL).strength(5,6).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape().forceSolidOn().lightLevel(state->state.getValue(MountedControlBlock.ON)?5:0);}
    public static void initialize(){ControlMenu.initialize();}
    public static java.util.List<ItemStack> creativeItems(){
        // Alternate throttle types and old large/compact IDs remain registered compatibility items.
        // Their actual choices are exposed through the canonical item's settings, as in Forge.
        return java.util.List.of(BUTTON,ROCKER,INDUSTRIAL_POWER,SMALL_POWER,THRUSTER,WALL_SLIDER).stream().map(holder->new ItemStack(holder.get())).toList();
    }
    private ControlRegistration(){}
}
