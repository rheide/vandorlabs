package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockSlab;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual server ItemBlock path; placement checks and ownership stay in the existing slab adapter. */
final class SlabMergeRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Slab merge: "+message);}
    private static void place(WorldServer world,BlockPos pos,boolean upper,EnumFacing face){world.setBlockState(pos,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM).withProperty(BlockAnimatedScreenSelector.FACING,face),3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setHousingTexture(4);tile.setSideTexture(7);tile.setMainTextureLayout(2);tile.setFaceTextures(new FaceTextures(false,new int[]{1,2,3,4,5,6}));tile.setStairGeometry(16,2);}
    private static ItemStack held(EntityPlayerMP player){ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_SLAB,2);NBTTagCompound different=new NBTTagCompound();different.setInteger("HousingTextureVersion",1);different.setInteger("housingTexture",9);different.setInteger("SideTexture",8);stack.setTagInfo("BlockEntityTag",different);player.setHeldItem(EnumHand.MAIN_HAND,stack);return stack;}
    private static void combined(WorldServer world,BlockPos pos,ItemStack stack,EnumFacing facing){require(world.getBlockState(pos).getBlock()==ModBlocks.PROGRAMMABLE_BLOCK&&world.getBlockState(pos).getValue(BlockAnimatedScreenSelector.FACING)==facing&&stack.getCount()==1,"configured cube/facing and one consumed item");TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);require(tile.getHousingTexture()==4&&tile.getSideTexture()==7&&tile.getMainTextureLayout()==2&&!tile.getFaceTextures().enabled&&java.util.Arrays.equals(tile.getFaceTextures().choices(),new int[]{1,2,3,4,5,6})&&tile.stairGeometry()==80,"placed settings and dormant fields retained over incoming item");}
    static void run(WorldServer world,EntityPlayerMP player){BlockPos pos=new BlockPos(304,40,146);ItemStack previous=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode,edit=player.capabilities.allowEdit;int cases=0;try{player.capabilities.isCreativeMode=false;player.capabilities.allowEdit=true;
        for(EnumFacing facing:EnumFacing.values())for(boolean upper:new boolean[]{false,true}){place(world,pos,upper,facing);ItemStack stack=held(player);EnumFacing side=upper?EnumFacing.DOWN:EnumFacing.UP;require(stack.getItem().onItemUse(player,world,pos,EnumHand.MAIN_HAND,side,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"direct merge");combined(world,pos,stack,facing);world.setBlockToAir(pos);cases++;}
        for(EnumFacing side:EnumFacing.values()){BlockPos clicked=pos.offset(side.getOpposite());world.setBlockToAir(clicked);place(world,pos,side.getIndex()%2==0,EnumFacing.EAST);ItemStack stack=held(player);require(stack.getItem().onItemUse(player,world,clicked,EnumHand.MAIN_HAND,side,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"adjacent merge independent of target half/face");combined(world,pos,stack,EnumFacing.EAST);world.setBlockToAir(pos);cases++;}
        place(world,pos,false,EnumFacing.NORTH);ItemStack stack=held(player);player.capabilities.allowEdit=false;require(stack.getItem().onItemUse(player,world,pos,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL&&stack.getCount()==2&&world.getBlockState(pos).getBlock()==ModBlocks.PROGRAMMABLE_SLAB,"denied merge has no ownership change");player.capabilities.allowEdit=true;cases++;
        net.minecraft.entity.item.EntityArmorStand occupant=new net.minecraft.entity.item.EntityArmorStand(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);occupant.preventEntitySpawning=true;require(world.spawnEntity(occupant),"collision fixture spawn");try{require(stack.getItem().onItemUse(player,world,pos,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL&&stack.getCount()==2&&world.getBlockState(pos).getBlock()==ModBlocks.PROGRAMMABLE_SLAB,"occupied full cube refuses without consumption");}finally{occupant.setDead();}cases++;
    }finally{world.setBlockToAir(pos);player.setHeldItem(EnumHand.MAIN_HAND,previous);player.capabilities.isCreativeMode=creative;player.capabilities.allowEdit=edit;}System.out.println("[vandorlabs][reprolab] slab-merge-policy PASS serverItemUseCases="+cases+" both halves/all6facings/direct+adjacent, placed+dormant settings, survival flag consumption, edit/collision refusal");}
    private SlabMergeRuntimeChecks(){}
}
