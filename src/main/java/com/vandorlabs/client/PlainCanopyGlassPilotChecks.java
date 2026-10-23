package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Plain glass shares the backrest cell at every seat height and through each placement entry point. */
final class PlainCanopyGlassPilotChecks {
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("plain canopy glass: "+what);}
    static void run(World world,EntityPlayerMP player,EnumFacing facing){
        BlockPos lower=new BlockPos(390,110,150),upper=lower.up(),wall=upper.offset(facing.getOpposite());world.getChunkFromBlockCoords(lower);
        BlockCanopy glass=(BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:regular_canopy_glass");BlockPilotSeat seat=(BlockPilotSeat)ModBlocks.PILOT_SEAT;
        Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;boolean creative=player.capabilities.isCreativeMode;ItemStack held=player.getHeldItemMainhand();int cases=0;
        try{player.capabilities.isCreativeMode=true;player.setPosition(lower.getX()+3,lower.getY()+2,lower.getZ()+3);player.rotationYaw=facing.getOpposite().getHorizontalAngle();player.rotationPitch=0;
            for(int height=0;height<4;height++)for(int route=0;route<4;route++){
                world.setBlockToAir(upper);world.setBlockToAir(lower);world.setBlockToAir(wall);
                ItemStack chair=new ItemStack(seat);chair.getOrCreateSubCompound("BlockEntityTag").setInteger("PilotHeight",height);chair.getOrCreateSubCompound("BlockEntityTag").setBoolean("TaxiMode",false);
                if(route<3){require(((ItemBlock)chair.getItem()).placeBlockAt(chair,player,world,lower,EnumFacing.UP,.5F,1,.5F,seat.getDefaultState().withProperty(BlockPilotSeat.FACING,facing)),"seat fixture placement");}
                else world.setBlockState(lower,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                ItemStack stack=new ItemStack(glass);player.setHeldItem(EnumHand.MAIN_HAND,stack);BlockPos clicked=route==1?upper:route==2?wall:lower;EnumFacing side=route==2?facing:EnumFacing.UP;
                if(route==2)world.setBlockState(wall,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                require(((ItemBlock)stack.getItem()).canPlaceBlockOnSide(world,clicked,side,player,stack),"client glass placement height="+height+" route="+route+" facing="+facing);
                require(player.interactionManager.processRightClickBlock(player,world,stack,EnumHand.MAIN_HAND,clicked,side,.5F,.9F,.5F)==EnumActionResult.SUCCESS,"server glass placement");
                require(world.getBlockState(upper).getBlock()==glass&&world.isAirBlock(upper.up()),"glass uses backrest cell, without an extra vertical gap");
                if(route==3){world.setBlockToAir(lower);player.setHeldItem(EnumHand.MAIN_HAND,chair);require(player.interactionManager.processRightClickBlock(player,world,chair,EnumHand.MAIN_HAND,upper,EnumFacing.DOWN,.5F,0,.5F)==EnumActionResult.SUCCESS,"pilot chair can be placed beneath existing plain glass");}
                TileEntityPilotSeat pilot=(TileEntityPilotSeat)world.getTileEntity(lower);require(pilot!=null&&pilot.getHeight()==height,"seat configuration retained");
                require(Math.abs(seat.getBoundingBox(world.getBlockState(lower),world,lower).maxY-(1.5+height/8D))<1e-8,"shared backrest collision retained");
                require(TileEntityCanopy.at(world,upper).constituent().getPos().equals(upper),"glass anchor at the same height as an opening canopy");
                world.destroyBlock(upper,false);require(world.getTileEntity(lower)==pilot&&world.getBlockState(upper).getBlock()==seat&&world.getBlockState(upper).getValue(BlockPilotSeat.UPPER),"removal restores upper seat reservation without removing seat");
                if(route==2)require(world.getBlockState(wall).getBlock()==net.minecraft.init.Blocks.STONE,"support wall retained");world.setBlockToAir(lower);world.setBlockToAir(upper);world.setBlockToAir(wall);cases++;
            }
        }finally{world.setBlockToAir(lower);world.setBlockToAir(upper);world.setBlockToAir(wall);player.setHeldItem(EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;player.setPosition(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
        System.out.println("[vandorlabs][reprolab] plain-canopy-glass-pilot PASS cases="+cases+" "+facing+" all heights lower upper wall clicks glass-first placement collision and removal");
    }
}
