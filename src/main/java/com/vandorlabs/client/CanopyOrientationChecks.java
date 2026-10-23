package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual item placement on every exposed side of both opening shell lengths. */
final class CanopyOrientationChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("canopy orientation: "+message);}
    private static boolean checked;
    static void run(WorldServer world,EntityPlayerMP player){
        if(checked)return;
        ItemStack held=player.getHeldItemMainhand();float yaw=player.rotationYaw;boolean creative=player.capabilities.isCreativeMode;
        BlockPos origin=new BlockPos(340,60,146);int cases=0;
        try{
            player.capabilities.isCreativeMode=true;
            for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)world.getChunkFromBlockCoords(origin.add(x,0,z));
            for(String sourceId:new String[]{"canopy","long_canopy"})for(String targetId:new String[]{"canopy","long_canopy"})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mount=0;mount<2;mount++)for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST}){
                BlockCanopy source=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+sourceId),target=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+targetId);
                world.setBlockState(origin,source.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);
                ItemStack sourceStack=new ItemStack(source);net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();com.vandorlabs.canopy.LargeCanopySettings config=new com.vandorlabs.canopy.LargeCanopySettings();config.mount=mount;config.write(settings);sourceStack.setTagCompound(settings);
                source.onBlockPlacedBy(world,origin,world.getBlockState(origin),player,sourceStack);
                java.util.List<BlockPos> cells=TileEntityCanopy.at(world,origin).cells();BlockPos clicked=origin;
                if(side.getAxis().isHorizontal())for(BlockPos cell:cells)if(cell.getX()*side.getDirectionVec().getX()+cell.getZ()*side.getDirectionVec().getZ()>clicked.getX()*side.getDirectionVec().getX()+clicked.getZ()*side.getDirectionVec().getZ())clicked=cell;
                ItemStack stack=new ItemStack(target);player.setHeldItem(EnumHand.MAIN_HAND,stack);player.rotationYaw=facing.getOpposite().getHorizontalAngle();
                require(stack.getItem().onItemUse(player,world,clicked,EnumHand.MAIN_HAND,side,.5F+side.getDirectionVec().getX()*.5F,.5F+side.getDirectionVec().getY()*.5F,.5F+side.getDirectionVec().getZ()*.5F)==EnumActionResult.SUCCESS,"placement "+sourceId+" -> "+targetId+"/"+facing+"/"+mount+"/"+side);
                TileEntityCanopy placed=TileEntityCanopy.at(world,clicked.offset(side));
                require(placed!=null&&placed.owner().facing()==facing&&placed.owner().large.mount==mount,"inherits facing and mount from clicked shell");
                world.destroyBlock(placed.constituent().getPos(),false);world.destroyBlock(origin,false);cases++;
            }
        }finally{player.setHeldItem(EnumHand.MAIN_HAND,held);player.rotationYaw=yaw;player.capabilities.isCreativeMode=creative;}
        checked=true;
        System.out.println("[vandorlabs][reprolab] canopy-click-orientation PASS cases="+cases);
    }
}
