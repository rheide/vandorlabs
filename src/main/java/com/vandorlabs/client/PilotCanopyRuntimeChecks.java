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
    private static void checkShuttlePlacement(World world,EntityPlayerMP player){
        float oldYaw=player.rotationYaw;
        ItemStack oldHeld=player.getHeldItemMainhand().copy();
        int checked=0;
        try{
            for(Block raw:Block.REGISTRY)if(raw instanceof BlockCanopy && ((BlockCanopy)raw).kind.startsWith("space_shuttle_")){
                BlockCanopy canopy=(BlockCanopy)raw;
                for(EnumFacing playerFacing:EnumFacing.HORIZONTALS){
                    BlockPos ground=new BlockPos(430,111,430);
                    for(BlockPos at:BlockPos.getAllInBox(ground.add(-6,0,-6),ground.add(6,5,6)))world.setBlockToAir(at);
                    world.setBlockState(ground,Blocks.STONE.getDefaultState(),2);
                    player.rotationYaw=playerFacing.getHorizontalAngle();
                    require(player.getHorizontalFacing()==playerFacing,"placement player yaw fixture");
                    ItemStack held=new ItemStack(canopy);
                    player.setHeldItem(EnumHand.MAIN_HAND,held);
                    require(held.getItem().onItemUse(player,world,ground,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,
                            "Shuttle item placement succeeds "+canopy.kind);
                    EnumFacing expected=playerFacing.getOpposite();
                    require(world.getBlockState(ground.up()).getBlock()==canopy
                            && world.getBlockState(ground.up()).getValue(BlockCanopy.FACING)==expected,
                            "Shuttle opens away from player "+canopy.kind+" player="+playerFacing);
                    checked++;
                }
            }
            require(checked>=8,"both Shuttle widths tested in four directions");
        }finally{
            player.rotationYaw=oldYaw;player.setHeldItem(EnumHand.MAIN_HAND,oldHeld);
            for(BlockPos at:BlockPos.getAllInBox(new BlockPos(424,111,424),new BlockPos(436,116,436)))world.setBlockToAir(at);
        }
    }
    static void run(World world,EntityPlayerMP player){
        BlockPilotSeat seat=(BlockPilotSeat)ModBlocks.PILOT_SEAT;
        BlockTelescopicLandingGear gear=null;
        for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"registered landing gear");
        checkShuttlePlacement(world,player);
        int count=0;
        for(Block raw:Block.REGISTRY)if(raw instanceof BlockCanopy){
            BlockCanopy canopy=(BlockCanopy)raw;
            if(!canopy.supportsSeatBackrest())continue;
            int maxJoined=canopy.opening() && canopy.width==1?3:1;
            for(int joined=1;joined<=maxJoined;joined++){
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean seatFirst:new boolean[]{false,true}){
                BlockPos anchor=new BlockPos(400,112,400);
                BlockPos lower=anchor.offset(facing.rotateY(),canopy.width*joined/2).offset(facing.getOpposite(),canopy.length/2).down();
                for(BlockPos at:BlockPos.getAllInBox(anchor.add(-8,-3,-8),anchor.add(8,4,8)))world.setBlockToAir(at);
                IBlockState seatState=seat.getDefaultState().withProperty(BlockPilotSeat.FACING,facing);
                if(seatFirst){world.setBlockState(lower,seatState,3);seat.onBlockPlacedBy(world,lower,seatState,player,new ItemStack(seat));}
                require(canopy.fits(world,anchor,facing),"canopy fits over seat reservation "+canopy.kind+" "+seatFirst);
                IBlockState canopyState=canopy.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                for(int column=0;column<joined;column++){
                    BlockPos member=anchor.offset(facing.rotateY(),column*canopy.width);
                    require(canopy.fits(world,member,facing),"joined canopy fits over seat reservation");
                    world.setBlockState(member,canopyState,3);canopy.onBlockPlacedBy(world,member,canopyState,player,new ItemStack(canopy));
                }
                require(((TileEntityCanopy)world.getTileEntity(anchor)).owner().width()==canopy.width*joined,
                        "opening canopy joined width "+joined);
                if(!seatFirst){
                    require(seat.canPlaceBlockAt(world,lower),"seat fits below "+canopy.kind);
                    ItemStack held=player.getHeldItemMainhand().copy();player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(seat));
                    require(!canopy.onBlockActivated(world,lower.up(),world.getBlockState(lower.up()),player,EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,0,.5F),"canopy must pass seat item click through "+canopy.kind);
                    require(player.getHeldItemMainhand().getItem().onItemUse(player,world,lower.up(),EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,0,.5F)==EnumActionResult.SUCCESS,"seat item fits below "+canopy.kind+" width="+joined);
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
                VehicleScan scan=new VehicleScan(world,lower);for(int n=0;n<100 && !scan.step(64);n++);
                require(scan.result!=null && scan.error==null,"shared backrest vehicle scan "+canopy.kind+": "+scan.error);
                require(scan.result.at(lower.up().subtract(scan.origin)).state.getBlock()==canopy,"canopy captured without replacing it with seat marker");
                for(int column=0;column<joined;column++)
                    for(BlockPos member:canopy.cells(anchor.offset(facing.rotateY(),column*canopy.width),facing)){
                        VehicleStructure.Cell captured=scan.result.at(member.subtract(scan.origin));
                        require(captured!=null && captured.state.getBlock()==canopy,"all joined canopy cells captured");
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
