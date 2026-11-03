package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Exact controller/collision steps on native ground, with forward/reverse and Sprint held. */
final class UnpoweredTaxiSpeedChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("unpowered Taxi: "+why);}
    static void run(WorldServer world,VehicleStructure scanned)throws Exception{
        require(!scanned.activeThruster(),"native scan should contain no active propulsion");
        BlockPos base=new BlockPos(1000,180,1000);
        for(BlockPos at:BlockPos.getAllInBox(base.add(-35,-1,-35),base.add(35,-1,35)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        java.lang.reflect.Method step=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);step.setAccessible(true);
        java.lang.reflect.Field sprint=EntityGroundVehicle.class.getDeclaredField("flightSprint"),inputAt=EntityGroundVehicle.class.getDeclaredField("verticalAt");sprint.setAccessible(true);inputAt.setAccessible(true);
        try{
            for(boolean powered:new boolean[]{false,true})for(boolean sprinting:new boolean[]{false,true})for(byte direction:new byte[]{-1,1}){
                List<VehicleStructure.Cell> cells=new ArrayList<>(scanned.cells);
                if(powered){VehicleStructure.Cell candidate=cells.stream().filter(c->c.state.getBlock()==Blocks.STONE).findFirst().get();cells.remove(candidate);cells.add(new VehicleStructure.Cell(candidate.pos,Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));}
                VehicleStructure source=new VehicleStructure(cells,scanned.seat,scanned.facing,scanned.gearCount);
                EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(VehicleStructure.read(source.write()));craft.setPosition(base.getX(),base.getY(),base.getZ());
                try{
                    double limit=powered?EntityGroundVehicle.CRUISE_SPEED:EntityGroundVehicle.UNPOWERED_TAXI_SPEED;require(craft.cruiseSpeed()==limit,"cruise speed after snapshot reload");require(powered||!craft.setHover(true),"unpowered craft cannot Hover");
                    sprint.setBoolean(craft,sprinting);inputAt.setLong(craft,world.getTotalWorldTime());craft.autopilotControls(direction,(byte)0,false);
                    for(int tick=0;tick<45;tick++){step.invoke(craft,true);require(Math.abs(craft.driveSpeed)<=limit+1e-8,"Sprint must not exceed Taxi speed cap");}
                    require(Math.abs(craft.driveSpeed-direction*limit)<1e-8,"reaches usable top speed in both directions");require(Math.hypot(craft.posX-base.getX(),craft.posZ-base.getZ())>6,"native ground movement advances");
                    craft.driveSpeed=direction*.56;step.invoke(craft,true);require(Math.abs(craft.driveSpeed)<=limit+1e-8,"restored overspeed is clamped");
                }finally{craft.signals.close();craft.setDead();VehicleLookup.remove(craft);}
            }
        }finally{for(BlockPos at:BlockPos.getAllInBox(base.add(-35,-1,-35),base.add(35,-1,35)))world.setBlockToAir(at);}
        System.out.println("[vandorlabs][reprolab] unpowered-taxi-speed PASS cases=8 native-ground forward/reverse Sprint-on/off snapshot-reload unpowered=.23 powered=.28 no-Hover no-boost overspeed-clamp");
    }
    private UnpoweredTaxiSpeedChecks(){}
}
