package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Shared canopy/backrest space must preserve both blocks and vehicle ownership. */
final class PilotCanopyRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("pilot canopy: "+message);}
    static void run(World world,EntityPlayerMP player){
        BlockPilotSeat seat=(BlockPilotSeat)ModBlocks.PILOT_SEAT;
        BlockTelescopicLandingGear gear=null;
        for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"registered landing gear");
        int count=0;
        for(Block raw:Block.REGISTRY)if(raw instanceof BlockCanopy){
            BlockCanopy canopy=(BlockCanopy)raw;
            if(!canopy.kind.equals("regular_canopy_glass") && !canopy.kind.startsWith("space_shuttle_") && !canopy.kind.startsWith("space_bubble_wedge_"))continue;
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean seatFirst:new boolean[]{false,true}){
                BlockPos anchor=new BlockPos(400,112,400);
                BlockPos lower=anchor.offset(facing.rotateY(),canopy.width/2).offset(facing.getOpposite(),canopy.length/2).down();
                for(BlockPos at:BlockPos.getAllInBox(anchor.add(-8,-3,-8),anchor.add(8,4,8)))world.setBlockToAir(at);
                IBlockState seatState=seat.getDefaultState().withProperty(BlockPilotSeat.FACING,facing);
                if(seatFirst){world.setBlockState(lower,seatState,3);seat.onBlockPlacedBy(world,lower,seatState,player,new ItemStack(seat));}
                require(canopy.fits(world,anchor,facing),"canopy fits over seat reservation "+canopy.kind+" "+seatFirst);
                IBlockState canopyState=canopy.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                world.setBlockState(anchor,canopyState,3);canopy.onBlockPlacedBy(world,anchor,canopyState,player,new ItemStack(canopy));
                if(!seatFirst){require(seat.canPlaceBlockAt(world,lower),"seat fits below "+canopy.kind);world.setBlockState(lower,seatState,3);seat.onBlockPlacedBy(world,lower,seatState,player,new ItemStack(seat));}
                require(world.getBlockState(lower).getBlock()==seat && world.getBlockState(lower.up()).getBlock()==canopy,"both blocks survive either placement order");
                TileEntityPilotSeat tile=(TileEntityPilotSeat)world.getTileEntity(lower);tile.setTaxiMode(false);
                for(int height=0;height<4;height++){tile.setHeight(height);require(seatState.getBoundingBox(world,lower).maxY==1.5+height/8D,"full shared backrest collision");}
                player.setSneaking(false);seat.onBlockActivated(world,lower,seatState,player,EnumHand.MAIN_HAND,facing,.5F,.3F,.5F);
                require(player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"boarding below canopy");player.dismountRidingEntity();
                world.setBlockState(lower.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(lower.down(2),gear.getDefaultState(),3);
                require(((TileEntityLandingGear)world.getTileEntity(lower.down(2))).configure(0,0,0,0),"settled gear");
                VehicleScan scan=new VehicleScan(world,lower);for(int n=0;n<100 && !scan.step(64);n++);
                require(scan.result!=null && scan.error==null,"shared backrest vehicle scan "+canopy.kind+": "+scan.error);
                require(scan.result.at(lower.up().subtract(scan.origin)).state.getBlock()==canopy,"canopy captured without replacing it with seat marker");
                world.setBlockToAir(lower.up());require(world.getBlockState(lower).getBlock()==seat,"removing canopy preserves seat");
                require(world.getBlockState(lower.up()).getBlock()==seat && world.getBlockState(lower.up()).getValue(BlockPilotSeat.UPPER),"canopy removal restores backrest reservation");
                world.setBlockToAir(lower);count++;
            }
        }
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(392,109,392),new BlockPos(408,116,408)))world.setBlockToAir(at);
        System.out.println("[vandorlabs][reprolab] pilot-canopy PASS variants="+count+" both-placement-orders heights boarding scan canopy-removal");
    }
}
