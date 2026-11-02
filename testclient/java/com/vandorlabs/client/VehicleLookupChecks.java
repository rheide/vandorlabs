package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

final class VehicleLookupChecks {
    static void run(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(40,0,0),net.minecraft.init.Blocks.STONE.getDefaultState(),null));
        VehicleStructure structure=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
        EntityGroundVehicle a=new EntityGroundVehicle(world),b=new EntityGroundVehicle(world);
        a.install(structure);b.install(structure);a.setPosition(-17,100,-17);b.setPosition(18,100,-17);
        try{
            a.onAddedToWorld();AxisAlignedBB all=new AxisAlignedBB(-25,90,-25,75,110,10);
            require(VehicleLookup.near(world,all,a).isEmpty(),"lone craft excludes itself");require(VehicleLookup.near(world,all).equals(Collections.singletonList(a)),"ordinary query retains lone craft");
            b.onAddedToWorld();check(world,Arrays.asList(a,b),all,null);check(world,Arrays.asList(a,b),all,a);check(world,Arrays.asList(a,b),all,b);
            Random random=new Random(18349);for(int i=0;i<1000;i++){
                double x=random.nextInt(120)-40,z=random.nextInt(80)-40;AxisAlignedBB query=new AxisAlignedBB(x,98,z,x+random.nextInt(80)+.01,104,z+random.nextInt(40)+.01);
                check(world,Arrays.asList(a,b),query,i%3==0?a:i%3==1?b:null);
            }
            a.setPosition(100,100,100);check(world,Arrays.asList(a,b),all,null);AxisAlignedBB moved=new AxisAlignedBB(95,90,95,150,110,110);check(world,Arrays.asList(a,b),moved,b);
            b.setDead();check(world,Collections.singletonList(a),all,null);check(world,Collections.singletonList(a),moved,b);
            a.onRemovedFromWorld();require(VehicleLookup.near(world,moved).isEmpty(),"removed craft remains indexed");
            System.out.println("PASS: vehicle lookup 1000 reference queries, lone-craft exclusion, multiple craft, wide/negative buckets, movement, death and removal");
        }finally{VehicleLookup.remove(a);VehicleLookup.remove(b);VehicleLookup.unload(world);}
    }
    private static void check(NonRenderingChecks.MemoryWorld world,List<EntityGroundVehicle> craft,AxisAlignedBB bounds,EntityGroundVehicle excluded){
        Set<EntityGroundVehicle> expected=Collections.newSetFromMap(new IdentityHashMap<>()),actual=Collections.newSetFromMap(new IdentityHashMap<>());
        for(EntityGroundVehicle e:craft)if(e!=excluded&&!e.isDead&&e.getRenderBoundingBox().union(e.getEntityBoundingBox()).intersects(bounds))expected.add(e);
        List<EntityGroundVehicle> result=VehicleLookup.near(world,bounds,excluded);actual.addAll(result);require(actual.size()==result.size()&&actual.equals(expected),"indexed candidates differ from live bounds");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError("Vehicle lookup: "+message);}
}
