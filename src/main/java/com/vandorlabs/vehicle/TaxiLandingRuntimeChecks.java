package com.vandorlabs.vehicle;

import java.util.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real-world collision and exact native parking footprints for matched telescopic supports. */
public final class TaxiLandingRuntimeChecks {
    private TaxiLandingRuntimeChecks(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Taxi telescopic gear: "+why);}
    private static List<BlockPos> roots=Arrays.asList(new BlockPos(0,2,0),new BlockPos(4,2,0),new BlockPos(2,2,4));
    public static void run(WorldServer world)throws Exception{
        BlockPos origin=new BlockPos(512,180,512);BlockTelescopicLandingGear block=(BlockTelescopicLandingGear)net.minecraft.block.Block.getBlockFromName("vandorlabs:landing_gear");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromBlockCoords(origin.add(x*16,0,z*16));
        for(BlockPos at:BlockPos.getAllInBox(origin.add(-2,0,-2),origin.add(8,0,8)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        try{for(int turn=0;turn<4;turn++){
            EntityGroundVehicle craft=new EntityGroundVehicle(world);List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,3,z),x==1&&z==1?ModBlocks.PILOT_SEAT.getDefaultState():Blocks.STONE.getDefaultState(),null));
            for(BlockPos at:roots){TileEntityLandingGear tile=new TileEntityLandingGear();tile.setPos(at);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger("ExtensionPixels",16);tag.setFloat("Progress",1);tag.setInteger("RedstoneMode",0);cells.add(new VehicleStructure.Cell(at,block.getDefaultState().withProperty(BlockTelescopicLandingGear.EXTENDED,true),tag));}
            VehicleStructure source=new VehicleStructure(cells,new BlockPos(1,3,1),EnumFacing.NORTH,3);source=VehicleLandingGear.parking(source,BlockPos.ORIGIN).structure;
            craft.install(source);craft.setPosition(origin.getX(),origin.getY(),origin.getZ());craft.rotationYaw=turn*90;craft.view.origin=origin;
            try{
                double[] feet=bottoms(craft,block);
                if(turn==0){BlockPos obstacle=origin.add(2,2,2);world.setBlockState(obstacle,Blocks.STONE.getDefaultState(),2);NBTTagCompound before=craft.structure.write();require(!VehicleLandingGear.setExtended(craft,roots.get(0),false),"blocked chassis retraction accepted");require(before.equals(craft.structure.write())&&craft.posY==origin.getY(),"refused move changed snapshot");world.setBlockToAir(obstacle);}
                require(VehicleLandingGear.setExtended(craft,roots.get(0),false),"clear matched supports refused turn="+turn);
                settle(craft,origin.getY()-1);require(Arrays.equals(feet,bottoms(craft,block)),"feet moved while chassis lowered");
                VehicleRotation.Plan parked=VehicleRotation.plan(craft);require(parked.origin.getY()==origin.getY()-1,"parking re-extended lowered supports");
                for(VehicleStructure.Cell cell:parked.structure.cells)require(world.isAirBlock(parked.origin.add(cell.pos)),"native parking footprint overlaps ground");
                VehicleStructure decoded=VehicleStructure.read(craft.structure.write());VehicleWorld reloaded=new VehicleWorld(world,decoded);
                for(BlockPos at:roots)require(((TileEntityLandingGear)reloaded.getTileEntity(at)).taxiGrounded,"grounded endpoint lost after save/reload");
                require(VehicleLandingGear.setExtended(craft,roots.get(0),true),"raising matched supports refused");settle(craft,origin.getY());require(Arrays.equals(feet,bottoms(craft,block)),"feet moved while chassis raised");
                if(turn==0){
                    craft.rotationYaw=17;craft.setPosition(craft.posX+.25,craft.posY+.25,craft.posZ+.375);craft.motionX=.005;craft.motionZ=.003;
                    double initial=craft.posY;double[] grounded=bottoms(craft,block);
                    require(VehicleLandingGear.setExtended(craft,roots.get(0),false),"moving noncardinal fractional-position matched supports refused");settle(craft,initial-1);
                    require(Arrays.equals(grounded,bottoms(craft,block)),"moving lowering changes grounded feet height");
                    require(VehicleLandingGear.setExtended(craft,roots.get(0),true),"moving raising refused");settle(craft,initial);
                    require(Arrays.equals(grounded,bottoms(craft,block)),"moving raising changes grounded feet height");
                }
            }finally{craft.signals.close();craft.setDead();}
        }}finally{for(BlockPos at:BlockPos.getAllInBox(origin.add(-2,0,-2),origin.add(8,4,8)))world.setBlockToAir(at);}
        System.out.println("[vandorlabs][reprolab] taxi-telescopic PASS four-cardinals matched-supports grounded-lower-raise swept-obstruction safe-refusal native-parking saved-marker moving-noncardinal-fractional-pose");
    }
    private static double[] bottoms(EntityGroundVehicle craft,BlockTelescopicLandingGear block){double[] result=new double[roots.size()];for(int i=0;i<result.length;i++)result[i]=Math.rint((craft.posY+block.vehicleBottomY(craft.view,roots.get(i)))*1000000)/1000000;return result;}
    private static void settle(EntityGroundVehicle craft,double target)throws Exception{
        for(int tick=0;tick<2000;tick++){if(craft.motionX!=0||craft.motionZ!=0)craft.setPosition(craft.posX+craft.motionX,craft.posY,craft.posZ+craft.motionZ);VehicleLandingGear.tick(craft);if(Math.abs(craft.posY-target)<1e-5)return;Thread.sleep(1);}
        throw new IllegalStateException("Taxi support animation timed out y="+craft.posY+" target="+target);
    }
}
