package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Native placed segments, each with its own controller and seat, share one vehicle owner. */
public final class VehicleSegmentControllerChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segment controls: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos origin=new BlockPos(1300,130,1300);net.minecraft.block.Block gear=null;
        for(net.minecraft.block.Block block:net.minecraft.block.Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear&&!(block instanceof BlockNonWheeledLandingGear)){gear=block;break;}require(gear!=null,"native landing support");
        try{
            for(BlockPos at:BlockPos.getAllInBox(origin.add(-2,-1,-2),origin.add(5,5,11)))world.setBlockToAir(at);
            for(int segment=0;segment<2;segment++){
                int z=segment*5;for(int x=0;x<3;x++)for(int row=0;row<3;row++)world.setBlockState(origin.add(x,1,z+row),net.minecraft.init.Blocks.IRON_BLOCK.getDefaultState(),2);
                for(int x=0;x<3;x++){BlockPos at=origin.add(x,0,z+1);world.setBlockState(at,gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0);}
                world.setBlockState(origin.add(0,2,z),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
                BlockPos seat=origin.add(1,2,z);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new net.minecraft.item.ItemStack(ModBlocks.PILOT_SEAT));
            }
            net.minecraft.block.Block coupler=net.minecraft.block.Block.getBlockFromName("vandorlabs:square_coupler");
            for(int i=0;i<2;i++){BlockPos at=origin.add(1,1,3+i);world.setBlockState(at,coupler.getDefaultState().withProperty(com.vandorlabs.coupler.BlockCoupler.FACING,i==0?EnumFacing.SOUTH:EnumFacing.NORTH).withProperty(com.vandorlabs.coupler.BlockCoupler.LOCKED,true),2);net.minecraft.tileentity.TileEntity tile=world.getTileEntity(at);NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setBoolean("CouplerRequested",true);data.setBoolean("CouplerReceived",true);data.setBoolean("CouplerArticulation",true);tile.readFromNBT(data);}
            for(int selected=0;selected<2;selected++){
                BlockPos seat=origin.add(1,2,selected*5);VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<1000&&!scan.step(256);n++){}require(scan.result!=null,"native two-controller conversion scan: "+scan.error);require(scan.origin.add(scan.result.controller().pos).equals(origin.add(0,2,selected*5)),"canonical controller follows piloted segment");
                VehicleWorld view=new VehicleWorld(world,scan.result);require(scan.result.cells.stream().filter(c->c.state.getBlock() instanceof BlockVehicleController).count()==2,"passive controller remains an ordinary saved block");
                VehicleStructure reloaded=VehicleStructure.read(scan.result.write());require(reloaded.controller().pos.equals(scan.result.controller().pos),"controller ordering survives save/reload");VehicleArticulation.requireAssemblyLimit(scan.result);
            }
            world.setBlockToAir(origin.add(0,2,0));VehicleScan missing=new VehicleScan(world,origin.add(1,2,0));for(int n=0;n<1000&&!missing.step(256);n++){}require(missing.result==null&&missing.error.contains("piloted segment needs its own Vehicle Controller"),"a passive segment controller cannot replace the missing driven controller");world.setBlockState(origin.add(0,2,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
            world.setBlockState(origin.add(2,2,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);VehicleScan invalid=new VehicleScan(world,origin.add(1,2,0));for(int n=0;n<1000&&!invalid.step(256);n++){}require(invalid.result==null&&invalid.error.contains("at most one Vehicle Controller"),"two controllers in driven segment still refuse");
            System.out.println("[vandorlabs][reprolab] segment-controllers PASS native scans both driver segments passive controller NBT canonical save order same-segment duplicate refusal");
        }finally{for(BlockPos at:BlockPos.getAllInBox(origin.add(-2,-1,-2),origin.add(5,5,11)))world.setBlockToAir(at);}
    }
    private VehicleSegmentControllerChecks(){}
}
