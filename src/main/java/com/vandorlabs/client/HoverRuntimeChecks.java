package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Server-world flight safety and controller contracts with real collision terrain. */
final class HoverRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(56,101,40),SEAT=new BlockPos(1,1,0);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("hover: "+what);}
    private static VehicleStructure fixture(int lifts,boolean thrust){
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        String[] lift={"antigravity_plate","repulsor_array","vertical_hover_thruster"};
        for(int i=0;i<lifts;i++){Block block=Block.getBlockFromName("vandorlabs:"+lift[i%3]);require(block instanceof BlockPropulsionLight,"lift registered");cells.add(new VehicleStructure.Cell(new BlockPos(i,0,1),block.getDefaultState(),null));}
        if(thrust)cells.add(new VehicleStructure.Cell(new BlockPos(3,0,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
        return new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1);
    }
    static void check(WorldServer world,EntityPlayerMP player)throws Exception{
        for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(7,12,5))){world.getChunkFromBlockCoords(pos);world.setBlockState(pos,pos.getY()==100?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
        require(!VehicleFlight.canHover(fixture(2,true)) && VehicleFlight.canHover(fixture(3,true)),"three mixed lift blocks required");
        boolean denied=false;try{VehicleFlight.requireThruster(fixture(3,false));}catch(IllegalArgumentException expected){denied=true;}require(denied,"lift blocks cannot substitute propulsion");VehicleFlight.requireThruster(fixture(3,true));
        EntityGroundVehicle insufficient=new EntityGroundVehicle(world);insufficient.install(fixture(2,true));insufficient.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(!insufficient.setHover(true),"two lifts reject Hover");insufficient.setDead();
        EntityGroundVehicle noThrust=new EntityGroundVehicle(world);noThrust.install(fixture(3,false));noThrust.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(!noThrust.setHover(true),"Hover rejects missing forward thruster");noThrust.setDead();
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(fixture(3,true));craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());
        double px=player.posX,py=player.posY,pz=player.posZ;
        try{
            require(Math.abs(VehicleFlight.clearance(craft))<1e-6,"ground contact clearance");
            for(VehicleStructure.Cell cell:craft.structure.cells)if(VehicleFlight.lift(cell.state.getBlock()))require(!craft.view.getBlockState(cell.pos).getValue(BlockPropulsionLight.POWERED) && craft.view.propulsionBrightness(cell.pos,15)==0,"Taxi suppresses lift light");
            require(craft.setHover(true) && craft.view.hover,"activate Hover");
            for(VehicleStructure.Cell cell:craft.structure.cells)if(VehicleFlight.lift(cell.state.getBlock())){IBlockState state=craft.view.getBlockState(cell.pos);state=state.getBlock().getActualState(state,craft.view,cell.pos);require(state.getValue(BlockPropulsionLight.POWERED) && !state.getValue(BlockPropulsionLight.PARTICLES) && craft.view.propulsionBrightness(cell.pos,0)==15,"Hover illuminates lift without ambient particle spray");require(state.getWeakPower(craft.view,cell.pos,EnumFacing.UP)==0,"lift does not emit redstone");}
            denied=false;try{VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),null);}catch(Exception expected){denied=expected.getMessage()!=null && expected.getMessage().contains("Taxi");}require(denied && !VehicleTransfer.pending(world),"Hover cannot convert directly to blocks");
            craft.groundClearance();craft.setPosition(BASE.getX(),BASE.getY()+2,BASE.getZ());require(!craft.setHover(false),"Taxi rejects exactly two blocks despite prior cached ground contact");
            craft.setPosition(BASE.getX(),BASE.getY()+1.9,BASE.getZ());require(craft.setHover(false) && !craft.view.hover,"Taxi accepts below two blocks");
            craft.setPosition(BASE.getX(),BASE.getY()+4,BASE.getZ());require(craft.setHover(true),"Hover activates aloft");
            java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
            double altitude=craft.posY;for(int i=0;i<20;i++)advance.invoke(craft,false);require(Math.abs(craft.posY-altitude)<1e-6,"unoccupied Hover holds altitude");
            double turnX=craft.posX,turnZ=craft.posZ;float turnYaw=craft.rotationYaw;
            craft.autopilotControls((byte)0,(byte)1,false);for(int i=0;i<20;i++)advance.invoke(craft,true);
            require(Math.abs(craft.rotationYaw-turnYaw)>20 && Math.abs(craft.posX-turnX)<1e-8 && Math.abs(craft.posZ-turnZ)<1e-8 && Math.abs(craft.posY-altitude)<1e-8,"stationary Hover rotates without translation");
            craft.autopilotControls((byte)0,(byte)0,false);for(int i=0;i<10;i++)advance.invoke(craft,false);
            require(player.startRiding(craft),"pilot boards fixture");
            craft.flightInput(player,(byte)1,false);for(int i=0;i<10;i++)advance.invoke(craft,true);require(craft.posY>altitude+1,"vertical input ascends");
            craft.flightInput(player,(byte)0,false);for(int i=0;i<40;i++)advance.invoke(craft,true);require(Math.abs(craft.posY-craft.hoverTarget)<.001,"released vertical input holds selected height");
            double raised=craft.posY;craft.flightInput(player,(byte)-1,false);for(int i=0;i<10;i++)advance.invoke(craft,true);require(craft.posY<raised-1,"vertical input descends");
            craft.flightInput(player,(byte)0,false);
            NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());EntityGroundVehicle restored=new EntityGroundVehicle(world);restored.readFromNBT(saved);require(restored.hover && restored.view.hover && Math.abs(restored.hoverTarget-craft.hoverTarget)<1e-6,"entity save restores Hover and altitude target");restored.setDead();
            craft.autopilotControls((byte)1,(byte)1,false);craft.flightInput(player,(byte)1,false);for(int i=0;i<8;i++)advance.invoke(craft,true);
            double heldX=craft.posX,heldY=craft.posY,heldZ=craft.posZ;float heldYaw=craft.rotationYaw;require(craft.driveSpeed>0 && craft.hoverTarget>craft.posY,"exit fixture is translating and climbing");
            craft.exitFlight(player);require(craft.hover && !craft.taxiReturnPending && craft.hoverTarget==heldY && craft.driveSpeed==0 && craft.motionY==0,"explicit Hover exit stops and latches present height");
            for(int i=0;i<40;i++)advance.invoke(craft,false);require(craft.posX==heldX && craft.posY==heldY && craft.posZ==heldZ && craft.rotationYaw==heldYaw,"unoccupied craft holds exact exit pose");
            require(player.startRiding(craft),"pilot reboards hold fixture");craft.autopilotControls((byte)1,(byte)-1,false);craft.flightInput(player,(byte)-1,false);for(int i=0;i<4;i++)advance.invoke(craft,true);
            heldX=craft.posX;heldY=craft.posY;heldZ=craft.posZ;player.dismountRidingEntity();for(int i=0;i<20;i++)advance.invoke(craft,false);require(craft.posX==heldX && craft.posY==heldY && craft.posZ==heldZ,"ordinary manual Hover dismount also holds position");
            EntityGroundVehicle taxi=new EntityGroundVehicle(world);taxi.install(fixture(3,true));taxi.setPosition(BASE.getX(),BASE.getY()+4,BASE.getZ());taxi.autopilotControls((byte)0,(byte)1,false);
            try{float taxiYaw=taxi.rotationYaw;for(int i=0;i<10;i++)advance.invoke(taxi,true);require(taxi.rotationYaw==taxiYaw,"Taxi retains speed-dependent steering at rest");}finally{taxi.setDead();}
            autopilotExit(world,player);
        }finally{craft.transferInProgress=true;if(player.getRidingEntity()==craft)player.dismountRidingEntity();craft.setDead();player.setPositionAndUpdate(px,py,pz);for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-2,-1,-2),BASE.add(7,12,5)))world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] hover-world PASS mixed-lift-count thruster-required Taxi-boundary fresh-clearance no-direct-parking lift-visuals no-redstone hold-height stationary-yaw ascend descend save-reload explicit-normal-exit-hold autopilot-exit-continues");
    }
    private static void autopilotExit(WorldServer world,EntityPlayerMP player){
        List<VehicleStructure.Cell> cells=new ArrayList<>(fixture(3,true).cells);BlockPos control=new BlockPos(4,0,1);com.vandorlabs.tiles.TileEntityAutopilot tile=new com.vandorlabs.tiles.TileEntityAutopilot();tile.setPos(control);cells.add(new VehicleStructure.Cell(control,ModBlocks.AUTOPILOT.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        EntityGroundVehicle automatic=new EntityGroundVehicle(world);automatic.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1));automatic.setPosition(BASE.getX(),BASE.getY()+5,BASE.getZ());require(automatic.setHover(true),"autopilot exit Hover fixture");automatic.autopilot.controller().setEnabled(true);
        try{require(player.startRiding(automatic),"pilot boards autopilot fixture");automatic.driveSpeed=.2;automatic.motionX=.1;automatic.motionY=.1;automatic.hoverTarget=automatic.posY+2;double target=automatic.hoverTarget;automatic.exitFlight(player);require(automatic.autopilot.enabled() && automatic.driveSpeed==.2 && automatic.motionX==.1 && automatic.motionY==.1 && automatic.hoverTarget==target,"autopilot retains movement and height target when pilot exits");}finally{automatic.transferInProgress=true;if(player.getRidingEntity()==automatic)player.dismountRidingEntity();automatic.setDead();}
    }
    private HoverRuntimeChecks(){}
}
