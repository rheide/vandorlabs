package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.coupler.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual snapshot membership, including unsupported middle sections and parallel coupler pairs. */
public final class VehicleCouplerSectionChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("coupler sections: "+why);}
    public static VehicleStructure train(World world,int[] lift,boolean parallel,boolean slab,boolean bypass,boolean locked,boolean articulated){
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int section=0;section<lift.length;section++){
            int z=section*6;for(int x=0;x<3;x++)for(int row=0;row<4;row++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z+row),net.minecraft.init.Blocks.STONE.getDefaultState(),null));
            for(int n=0;n<lift[section];n++)cells.add(new VehicleStructure.Cell(new BlockPos(n%3,1+n/3,z+1),net.minecraft.block.Block.getBlockFromName("vandorlabs:repulsor_array").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),null));
            if(section<lift.length-1)for(int x:parallel?new int[]{0,2}:new int[]{1}){
                IBlockState a=net.minecraft.block.Block.getBlockFromName(slab?"vandorlabs:vehicle_coupler_slab":"vandorlabs:square_coupler").getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.SOUTH).withProperty(BlockCoupler.LOCKED,locked),b=a.withProperty(BlockCoupler.FACING,EnumFacing.NORTH);
                TileCoupler at=new TileCoupler();NBTTagCompound data=at.writeToNBT(new NBTTagCompound());data.setBoolean("CouplerRequested",locked);data.setBoolean("CouplerReceived",locked);if(articulated)data.setBoolean("CouplerArticulation",true);
                cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z+4),a,data));cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z+5),b,data));
            }
        }
        if(bypass){for(int z=4;z<=5;z++)cells.add(new VehicleStructure.Cell(new BlockPos(0,0,z),net.minecraft.init.Blocks.STONE.getDefaultState(),null));}
        cells.add(new VehicleStructure.Cell(new BlockPos(0,1,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));BlockPos seat=new BlockPos(1,1,0);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        VehicleStructure result=new VehicleStructure(cells,seat,EnumFacing.NORTH,3);new VehicleWorld(world,result);return result;
    }
    public static void run(World world){
        for(boolean slab:new boolean[]{false,true})for(boolean parallel:new boolean[]{false,true}){
            require(VehicleFlight.canHover(train(world,new int[]{3,3,3},parallel,slab,false,true,false)),"three lift blocks in every section");
            for(int[] counts:new int[][]{{3,3,0},{3,0,6},{0,6,3},{2,3,3}})require(!VehicleFlight.canHover(train(world,counts,parallel,slab,false,true,false)),"lift cannot be borrowed across a connected coupler");
            require(VehicleFlight.canHover(train(world,new int[]{3,0},parallel,slab,false,false,false)),"disabled couplers do not require extra lift");
        }
        require(VehicleFlight.canHover(train(world,new int[]{3,0},false,false,true,true,false)),"a real block bridge makes one physical section");
        System.out.println("[vandorlabs][reprolab] coupler-section-hover PASS full/slab single/parallel links middle/root/trailing shortfall disabled inputs real block bypass");
    }
    private VehicleCouplerSectionChecks(){}
}
