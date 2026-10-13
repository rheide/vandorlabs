package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.LargeCanopySettings;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live documentation fixtures for widths, nose profiles and mounted hinges. */
final class CanopyDocumentationScenes {
    static void build(World world,String shot){
        String suffix=shot.substring("gallery_canopy_motion_".length());
        LargeCanopySettings settings=new LargeCanopySettings();
        settings.shade=12;settings.tint=0;
        if(suffix.startsWith("large_")){
            boolean left=suffix.endsWith("left");settings.depth=left?48:32;settings.scale=55;settings.openable=true;settings.hinge=left?0:2;
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+(left?"large_front_canopy_glass":"front_canopy_glass"));
            place(world,block,new BlockPos(162,left?5:4,-18),EnumFacing.SOUTH,settings);
        }else{
            boolean ceiling=suffix.equals("ceiling");String[] parts=suffix.split("_");
            int width=ceiling?2:Integer.parseInt(parts[1]);settings.motion=suffix.startsWith("hinge") || ceiling?1:0;
            settings.angle=ceiling?1:width-1;settings.mount=ceiling?1:0;
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");
            if(ceiling)for(int x=0;x<width;x++)for(int z=0;z<2;z++)world.setBlockState(new BlockPos(160+x,8,-18+z),net.minecraft.init.Blocks.STONEBRICK.getDefaultState(),2);
            for(int x=0;x<width;x++)place(world,block,new BlockPos(160+x,ceiling?7:4,-18),EnumFacing.NORTH,settings);
        }
    }
    private static void place(World world,BlockCanopy block,BlockPos pos,EnumFacing facing,LargeCanopySettings settings){
        ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);
        world.setBlockState(pos,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);
        block.onBlockPlacedBy(world,pos,world.getBlockState(pos),null,stack);
    }
}
