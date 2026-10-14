package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Comparable live views of fixed roof frames and adjustable front-window profiles. */
final class CanopyFrameDocumentationScenes {
    static final String[] NAMES={"joined_frame_closed","joined_frame_slide_back","joined_frame_slide_forward","joined_frame_hinge","front_shallow_small","front_shallow_large","front_deep_small","front_deep_large"};
    static void build(World world,int scene){
        world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        for(net.minecraft.entity.Entity entity:new java.util.ArrayList<>(world.loadedEntityList))if(entity instanceof net.minecraft.entity.EntityLiving)entity.setDead();
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(98,19,32),new BlockPos(118,30,48)))world.setBlockToAir(pos);
        for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(98,19,32),new BlockPos(118,19,48)))world.setBlockState(pos,Blocks.STONEBRICK.getDefaultState(),2);
        LargeCanopySettings settings=new LargeCanopySettings();settings.shade=13;settings.tint=1;
        if(scene<4){
            settings.angle=1;settings.motion=scene==3?1:0;settings.slideForward=scene==2;
            for(int x=0;x<2;x++)place(world,"canopy",new BlockPos(104+x,20,40),settings);
            for(int x=0;x<2;x++)place(world,"regular_canopy_glass",new BlockPos(104+x,20,42),settings);
            if(scene>0){TileEntityCanopy owner=TileEntityCanopy.at(world,new BlockPos(104,20,40)).owner();owner.toggle();for(int tick=0;tick<16;tick++)owner.update();owner.sync();}
        }else{
            settings.depth=scene<6?8:64;settings.scale=scene%2==0?25:90;
            place(world,"front_canopy_glass",new BlockPos(100,20,40),settings);
            place(world,"large_front_canopy_glass",new BlockPos(108,20,40),settings);
        }
    }
    private static void place(World world,String id,BlockPos pos,LargeCanopySettings settings){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+id);
        ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);
        world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,block.getDefaultState(),null,stack);
    }
}
