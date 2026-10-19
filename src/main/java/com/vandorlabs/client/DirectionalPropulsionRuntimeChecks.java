package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Exercise the rendered power and brightness overlay in six directions at arbitrary craft yaw. */
final class DirectionalPropulsionRuntimeChecks {
    static void run(World world){
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        int index=0;
        for(Block block:Block.REGISTRY)if(block instanceof BlockPropulsionLight&&!VehicleFlight.lift(block))for(EnumFacing facing:EnumFacing.values()){
            BlockPos at=new BlockPos(index%16,1+(index/16)%16,index/256);index++;
            cells.add(new VehicleStructure.Cell(at,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,facing),null));
        }
        VehicleWorld view=new VehicleWorld(world,new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));int checks=0;
        for(boolean hover:new boolean[]{false,true})for(float yaw:new float[]{0,90,-90,37})for(EnumFacing travel:EnumFacing.values()){
            view.hover=hover;view.propulsionLevel=12;double angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
            view.recordMotion((travel.getFrontOffsetX()*c-travel.getFrontOffsetZ()*s)*.2,travel.getFrontOffsetY()*.2,(travel.getFrontOffsetX()*s+travel.getFrontOffsetZ()*c)*.2,yaw);
            for(VehicleStructure.Cell cell:cells)if(cell.state.getBlock() instanceof BlockPropulsionLight){
                boolean expected=cell.state.getValue(BlockPropulsionLight.FACING)==travel.getOpposite();
                if(view.getBlockState(cell.pos).getValue(BlockPropulsionLight.POWERED)!=expected||view.propulsionBrightness(cell.pos,15)!=(expected?12:0)||view.getBlockState(cell.pos).getValue(BlockPropulsionLight.PARTICLES))throw new IllegalStateException("Directional propulsion mismatch: "+cell.state+" travel="+travel+" yaw="+yaw+" hover="+hover);
                checks++;
            }
        }
        view.recordMotion(0,0,.2,0);int key=view.propulsionAppearance();view.recordMotion(0,0,.4,0);
        if(view.propulsionAppearance()!=key)throw new IllegalStateException("Velocity magnitude needlessly invalidates propulsion mesh");
        view.recordMotion(0,0,-.2,0);if(view.propulsionAppearance()==key)throw new IllegalStateException("Reverse travel must invalidate propulsion mesh");
        view.propulsionLevel=0;
        for(VehicleStructure.Cell cell:cells)if(cell.state.getBlock() instanceof BlockPropulsionLight&&view.propulsionBrightness(cell.pos,15)!=0)throw new IllegalStateException("Stopped propulsion stays lit");
        System.out.println("[vandorlabs][reprolab] directional-propulsion PASS checks="+checks+" six directions four yaws Taxi/Hover all propulsion shapes brightness and power");
    }
}
