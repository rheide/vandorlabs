package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.coupler.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Actual vertical topology, persisted joints, and server-driver movement callbacks. */
public final class VehicleVerticalCouplerChecks {
    private static int checks;
    private static final BlockPos LOWER=new BlockPos(1,1,1),UPPER=LOWER.up();
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("vertical coupler: "+why);}
    private static VehicleStructure structure(WorldServer world){
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(int y:new int[]{0,3})for(int x=0;x<3;x++)for(int z=0;z<3;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,y,z),net.minecraft.init.Blocks.STONE.getDefaultState(),null));
        for(int y:new int[]{1,4})for(int[] at:new int[][]{{0,1},{2,1},{1,2}})cells.add(new VehicleStructure.Cell(new BlockPos(at[0],y,at[1]),Block.getBlockFromName("vandorlabs:repulsor_array").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),null));
        IBlockState head=Block.getBlockFromName("vandorlabs:square_coupler").getDefaultState().withProperty(BlockCoupler.LOCKED,true);NBTTagCompound data=new TileCoupler().writeToNBT(new NBTTagCompound());data.setBoolean("CouplerRequested",true);data.setBoolean("CouplerReceived",true);data.setBoolean("CouplerArticulation",true);
        cells.add(new VehicleStructure.Cell(LOWER,head.withProperty(BlockCoupler.FACING,EnumFacing.UP),data));cells.add(new VehicleStructure.Cell(UPPER,head.withProperty(BlockCoupler.FACING,EnumFacing.DOWN),data));
        cells.add(new VehicleStructure.Cell(new BlockPos(0,1,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));BlockPos seat=new BlockPos(1,1,0);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));VehicleStructure result=new VehicleStructure(cells,seat,EnumFacing.NORTH,3);new VehicleWorld(world,result);return result;
    }
    private static void seed(EntityGroundVehicle craft,double yaw){NBTTagCompound root=new NBTTagCompound(),pose=new NBTTagCompound(),joint=new NBTTagCompound();pose.setInteger("Version",1);joint.setLong("A",LOWER.toLong());joint.setLong("B",UPPER.toLong());joint.setDouble("Yaw",yaw);joint.setDouble("Pitch",0);NBTTagList joints=new NBTTagList();joints.appendTag(joint);pose.setTag("Joints",joints);root.setTag("CouplerJointPose",pose);craft.articulation.read(root);}
    public static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos origin=new BlockPos(1700,120,1700);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunkFromBlockCoords(origin.add(x*16,0,z*16));VehicleStructure original=structure(world);need(VehicleFlight.canHover(original),"three lift sources on both vertical sections");
        VehicleCouplerTopology topology=VehicleCouplerTopology.of(original);need(topology.sections.size()==2&&topology.links.size()==1&&topology.links.get(0).articulation,"opposing UP/DOWN heads create two sections and one articulated connection");
        for(double seed:new double[]{-20,20})for(float turn:new float[]{-4,4}){
            EntityGroundVehicle craft=new EntityGroundVehicle(world),loaded=new EntityGroundVehicle(world);try{
                craft.install(original);craft.setPosition(origin.getX()+.25,origin.getY(),origin.getZ()+.75);craft.hover=true;craft.hoverTarget=craft.posY;seed(craft,seed);need(craft.articulation.jointCount()==1&&craft.articulation.validPose(),"saved seeded vertical joint remains valid yaw="+seed);
                NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());loaded.readFromNBT(saved);need(loaded.structure!=null&&loaded.structure.write().equals(craft.structure.write())&&loaded.articulation.jointYaw(0)==seed,"version-one vertical pose roundtrip retains exact snapshot and yaw");
                need(craft.articulation.begin(),"begin real articulated frame");craft.rotationYaw+=turn;craft.articulation.end();double expected=seed-turn,actual=craft.articulation.jointYaw(0);
                need(Math.abs(actual-expected)<1e-5,"centered vertical axle preserves child heading under parent turn seed="+seed+" turn="+turn+" expected="+expected+" actual="+actual);
                double[] a=craft.articulation.body(0,1).point(1.5,2,1.5),b=craft.articulation.body(1,1).point(1.5,2,1.5);need(Math.abs(a[0]-b[0])+Math.abs(a[1]-b[1])+Math.abs(a[2]-b[2])<1e-8,"locked vertical connection centers remain coincident");need(craft.articulation.validPose(),"turned vertical body remains collision safe");
            }finally{craft.setDead();loaded.setDead();}
        }
        EntityGroundVehicle driven=new EntityGroundVehicle(world);try{driven.install(original);driven.setPosition(origin.getX(),origin.getY(),origin.getZ());driven.hover=true;driven.hoverTarget=driven.posY;world.spawnEntity(driven);need(driven.board(player)&&driven.getControllingPassenger()==player,"actual integrated-server player occupies pilot seat");for(int n=1;n<=8;n++){driven.input(player,n,(byte)1,(byte)1,false);driven.onUpdate();need(driven.articulation.validPose(),"actual driver movement collision safe step="+n);}need(Math.abs(driven.rotationYaw)>.1,"actual driver callback turns vertical articulated owner");}finally{player.dismountRidingEntity();driven.setDead();}
        System.out.println("[vandorlabs][reprolab] vertical-coupler PASS checks="+checks+" actual topology/version1/centered heading/pivot/collision/server-driver callbacks; packet/arm coverage separate");
    }
    private VehicleVerticalCouplerChecks(){}
}
