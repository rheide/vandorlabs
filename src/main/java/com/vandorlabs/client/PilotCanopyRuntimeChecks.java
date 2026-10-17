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
        for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear && !(block instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"registered landing gear");
        int count=0;
        for(Block raw:Block.REGISTRY)if(raw instanceof BlockCanopy){
            BlockCanopy canopy=(BlockCanopy)raw;
            if(!canopy.supportsSeatBackrest())continue;
            int[] joinedWidths=canopy.opening() && canopy.width==1?new int[]{1,3,9}:new int[]{1};
            for(int joined:joinedWidths){
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean seatFirst:new boolean[]{false,true}){
                BlockPos anchor=new BlockPos(400,112,400);
                BlockPos lower=anchor.offset(facing.rotateY(),canopy.width*joined/2).offset(facing.getOpposite(),canopy.length-1).down();
                for(BlockPos at:BlockPos.getAllInBox(anchor.add(-8,-3,-8),anchor.add(8,4,8)))world.setBlockToAir(at);
                player.setPosition(anchor.getX()+6,anchor.getY()+2,anchor.getZ()+6);
                IBlockState seatState=seat.getDefaultState().withProperty(BlockPilotSeat.FACING,facing);
                if(seatFirst){world.setBlockState(lower,seatState,3);seat.onBlockPlacedBy(world,lower,seatState,player,new ItemStack(seat));}
                require(canopy.fits(world,anchor,facing),"canopy fits over seat reservation "+canopy.kind+" "+seatFirst);
                IBlockState canopyState=canopy.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                for(int column=0;column<joined;column++){
                    BlockPos member=anchor.offset(facing.rotateY(),column*canopy.width);
                    require(canopy.fits(world,member,facing),"joined canopy fits over seat reservation");
                    if(seatFirst && joined==1){
                        ItemStack saved=player.getHeldItemMainhand().copy();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(canopy));
                        net.minecraft.item.ItemBlock item=(net.minecraft.item.ItemBlock)player.getHeldItemMainhand().getItem();
                        require(item.canPlaceBlockOnSide(world,lower.up(),facing,player,player.getHeldItemMainhand()),"client placement accepts seat backrest");
                        for(boolean sneak:new boolean[]{false,true}){player.setSneaking(sneak);require(!seat.onBlockActivated(world,lower.up(),world.getBlockState(lower.up()),player,EnumHand.MAIN_HAND,facing,.5F,.5F,.5F),"canopy click passes seat activation");}
                        player.setSneaking(false);
                        require(player.interactionManager.processRightClickBlock(player,world,player.getHeldItemMainhand(),EnumHand.MAIN_HAND,lower.up(),facing,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"normal server interaction places canopy over seat");
                        player.setHeldItem(EnumHand.MAIN_HAND,saved);
                    }else {world.setBlockState(member,canopyState,3);canopy.onBlockPlacedBy(world,member,canopyState,player,new ItemStack(canopy));}
                }
                require(((TileEntityCanopy)world.getTileEntity(anchor)).constituent().getPos().equals(anchor),"rear backrest click places canopy at intended front anchor "+canopy.kind+" facing="+facing+" seatFirst="+seatFirst);
                require(((TileEntityCanopy)world.getTileEntity(anchor)).owner().width()==canopy.width*joined,
                        "opening canopy joined width "+joined);
                if(!seatFirst){
                    require(seat.canPlaceBlockAt(world,lower),"seat fits below "+canopy.kind);
                    ItemStack held=player.getHeldItemMainhand().copy();player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(seat));
                    require(!canopy.onBlockActivated(world,lower.up(),world.getBlockState(lower.up()),player,EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,0,.5F),"canopy must pass seat item click through "+canopy.kind);
                    require(player.getHeldItemMainhand().getItem().onItemUse(player,world,lower.up(),EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,0,.5F)==EnumActionResult.SUCCESS,"seat item fits below "+canopy.kind+" width="+joined+" mayPlace="+world.mayPlace(seat,lower,false,EnumFacing.DOWN,player)+" player="+player.getPositionVector());
                    player.setHeldItem(EnumHand.MAIN_HAND,held);
                }
                require(world.getBlockState(lower).getBlock()==seat && world.getBlockState(lower.up()).getBlock()==canopy,"both blocks survive either placement order");
                TileEntityPilotSeat tile=(TileEntityPilotSeat)world.getTileEntity(lower);tile.setTaxiMode(false);
                for(int height=0;height<4;height++){tile.setHeight(height);require(seatState.getBoundingBox(world,lower).maxY==1.5+height/8D,"full shared backrest collision");}
                player.setSneaking(false);seat.onBlockActivated(world,lower,seatState,player,EnumHand.MAIN_HAND,facing,.5F,.3F,.5F);
                require(player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"boarding below canopy");player.dismountRidingEntity();
                world.setBlockState(lower.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(lower.down(2),gear.getDefaultState(),3);
                require(((TileEntityLandingGear)world.getTileEntity(lower.down(2))).configure(0,0,0,0),"settled gear");
                for(EnumFacing extra:new EnumFacing[]{facing.rotateY(),facing.rotateYCCW()}){
                    world.setBlockState(lower.down().offset(extra),Blocks.STONE.getDefaultState(),3);
                    world.setBlockState(lower.down(2).offset(extra),gear.getDefaultState(),3);
                    require(((TileEntityLandingGear)world.getTileEntity(lower.down(2).offset(extra))).configure(0,0,0,0),"extra settled gear");
                }
                world.setBlockState(lower.down().offset(facing),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),3);
                VehicleScan scan=new VehicleScan(world,lower);for(int n=0;n<100 && !scan.step(64);n++);
                require(scan.result!=null && scan.error==null,"shared backrest vehicle scan "+canopy.kind+": "+scan.error);
                require(scan.result.at(lower.up().subtract(scan.origin)).state.getBlock()==canopy,"canopy captured without replacing it with seat marker");
                for(int column=0;column<joined;column++)
                    for(BlockPos member:canopy.cells(anchor.offset(facing.rotateY(),column*canopy.width),facing)){
                        VehicleStructure.Cell captured=scan.result.at(member.subtract(scan.origin));
                        require(captured!=null && captured.state.getBlock()==canopy,"all joined canopy cells captured "+canopy.kind+" width="+joined+" facing="+facing+" seatFirst="+seatFirst+" member="+member);
                    }
                if(canopy.opening() && facing==EnumFacing.NORTH && !seatFirst)
                    VehicleCanopyMotionRuntimeChecks.run(world,scan.result,lower.up().subtract(scan.origin));
                world.setBlockToAir(lower.up());require(world.getBlockState(lower).getBlock()==seat,"removing canopy preserves seat");
                require(world.getBlockState(lower.up()).getBlock()==seat && world.getBlockState(lower.up()).getValue(BlockPilotSeat.UPPER),"canopy removal restores backrest reservation");
                world.setBlockToAir(lower);count++;
            }
            }
        }
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(392,109,392),new BlockPos(408,116,408)))world.setBlockToAir(at);
        System.out.println("[vandorlabs][reprolab] pilot-canopy PASS variants="+count+" both-placement-orders heights boarding scan canopy-removal");
    }
}
