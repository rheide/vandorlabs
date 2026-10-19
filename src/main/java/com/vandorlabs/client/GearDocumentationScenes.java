package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One real modular assembly per clip, with unobstructed full-stroke framing. */
final class GearDocumentationScenes {
    static void build(World world,String shot){
        build(world,shot,new BlockPos(160,8,-18));
    }
    static void build(World world,String shot,BlockPos root){
        boolean large=shot.contains("_large_"),swing=shot.contains("_swing_");
        GearBlock arm=large?(swing?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_EXTENDER):(swing?GearRegistry.GEAR_SWING_ARM:GearRegistry.GEAR_EXTENDER);
        GearBlock foot=shot.endsWith("wheel")?(large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL):shot.endsWith("ski")?GearRegistry.LANDING_SKI:GearRegistry.LANDING_PLATE;
        if(shot.contains("_mount_"))arm=large?GearRegistry.LARGE_GEAR_MOUNT:GearRegistry.GEAR_MOUNT;
        place(world,arm,root);
        if(foot.kind()==GearBlock.Kind.WHEEL)place(world,foot,root.down());
        else for(BlockPos p:arm.componentCells(world,root))place(world,foot,p.down());
        for(BlockPos p:arm.componentCells(world,root))world.setBlockState(p.up(),Blocks.IRON_BLOCK.getDefaultState(),2);
        GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);
        if(!tile.rebuild())throw new IllegalStateException("Gear documentation formation: "+tile.error());
    }
    private static void place(World world,GearBlock block,BlockPos pos){world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,world.getBlockState(pos),null,new ItemStack(block));}
}
