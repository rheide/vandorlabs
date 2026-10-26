package com.vandorlabs.gear;

import com.vandorlabs.gear.core.JoinedPlatform;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Loaded-only searches in the mounting plane; rotating groups share a single hinge axis. */
final class JoinedArms {
    static List<BlockPos> columns(World world,BlockPos root,int height){
        GearTileEntity first=(GearTileEntity)world.getTileEntity(root);
        if(!first.joinEnabled())return Collections.singletonList(root);
        Set<BlockPos> seen=new LinkedHashSet<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();seen.add(root);pending.add(root);
        while(!pending.isEmpty()){
            BlockPos current=pending.removeFirst();GearTileEntity source=(GearTileEntity)world.getTileEntity(current);
            for(EnumFacing local:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST}){
                if(first.isSwingArmController()&&(local.getAxis()==EnumFacing.Axis.X?first.direction().axisX()==0:first.direction().axisZ()==0))continue;
                BlockPos step=ArmOrientation.vector(first.mountDirection(),new BlockPos(local.getDirectionVec()));
                for(int distance=1;distance<=source.joinRange();distance++){
                    BlockPos at=current.add(step.getX()*distance,step.getY()*distance,step.getZ()*distance);
                    if(!GearAssembly.loaded(world,at))break; // A boundary never loads chunks just to look for a partner.
                    IBlockState state=world.getBlockState(at);if(state.getBlock()!=world.getBlockState(root).getBlock())continue;
                    GearBlock block=(GearBlock)state.getBlock();if(!block.componentRoot(at,state).equals(at))continue;
                    if(!(world.getTileEntity(at) instanceof GearTileEntity))continue;GearTileEntity partner=(GearTileEntity)world.getTileEntity(at);
                    GearTileEntity control=partner.loadedController();if(control==null)control=partner;
                    if(!partner.joinEnabled()||distance>partner.joinRange()||partner.mountDirection()!=first.mountDirection()||!at.equals(GearAssembly.topOfColumn(world,at)))continue;
                    GearTileEntity firstControl=first.loadedController();if(firstControl==null)firstControl=first;
                    if(!matchingSettings(firstControl,control))continue;
                    boolean lengthMatches=true;for(int n=0;n<height;n++){BlockPos part=at.offset(first.mountDirection(),n);if(!GearAssembly.loaded(world,part)||world.getBlockState(part).getBlock()!=state.getBlock()||GearAssembly.mountDirection(world,part)!=first.mountDirection()){lengthMatches=false;break;}}
                    if(!lengthMatches)continue;
                    BlockPos tip=at.offset(first.mountDirection(),height);if(!GearAssembly.loaded(world,tip)||GearAssembly.isFoot(world.getBlockState(tip)))continue;if(GearAssembly.isExtender(world.getBlockState(tip))&&GearAssembly.mountDirection(world,tip)==first.mountDirection())continue;
                    if(seen.add(at)){if(seen.size()>64)throw new IllegalArgumentException("Join at most 64 arms");pending.addLast(at);}
                    break;
                }
            }
        }
        List<BlockPos> result=new ArrayList<>(seen);Collections.sort(result);int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
        for(BlockPos at:result){BlockPos local=ArmOrientation.inverse(first.mountDirection(),at.subtract(root));minX=Math.min(minX,local.getX());maxX=Math.max(maxX,local.getX());minZ=Math.min(minZ,local.getZ());maxZ=Math.max(maxZ,local.getZ());}
        if((long)maxX-minX>JoinedPlatform.MAX_SPAN||(long)maxZ-minZ>JoinedPlatform.MAX_SPAN)throw new IllegalArgumentException("A joined platform may span at most 64 blocks per axis");
        return result;
    }
    private static boolean matchingSettings(GearTileEntity first,GearTileEntity candidate){
        // The current target is runtime state; all authored configuration must agree.
        return first.sameJoinConfiguration(candidate);
    }
    static Set<BlockPos> platform(GearTileEntity arm){return platform(arm,BlockPos.ORIGIN);}
    static Set<BlockPos> platform(GearTileEntity arm,BlockPos shift){return platform(arm,at->at.add(shift));}
    static Set<BlockPos> platform(GearTileEntity arm,java.util.function.Function<BlockPos,BlockPos> position){
        if(arm.columns().size()<2)throw new IllegalArgumentException("Enable a compatible neighbouring arm first");
        List<JoinedPlatform.Cell> supports=new ArrayList<>();int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
        for(BlockPos column:arm.columns()){BlockPos local=ArmOrientation.inverse(arm.mountDirection(),column);supports.add(new JoinedPlatform.Cell(local.getX(),local.getZ()));minX=Math.min(minX,local.getX());maxX=Math.max(maxX,local.getX());minZ=Math.min(minZ,local.getZ());maxZ=Math.max(maxZ,local.getZ());}
        Set<JoinedPlatform.Cell> occupied=new LinkedHashSet<>();int y=-arm.assembly().columnHeight();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){BlockPos at=position.apply(arm.localCell(x,y,z));if(!GearAssembly.loaded(arm.getWorld(),at))throw new IllegalArgumentException("Load the entire joined platform");if(!arm.getWorld().isAirBlock(at)&&(!arm.members().contains(at)||arm.storedSections!=null&&arm.storedSections.at(at.subtract(arm.getPos()))!=null))occupied.add(new JoinedPlatform.Cell(x,z));}
        Set<BlockPos> result=new LinkedHashSet<>();if(occupied.isEmpty())return result;for(JoinedPlatform.Cell cell:JoinedPlatform.connected(supports,occupied))result.add(position.apply(arm.localCell(cell.u,y,cell.v)));return result;
    }
    static final class Volume {
        final Set<BlockPos> platform;final boolean carry;final int floor,height;int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
        Volume(GearTileEntity arm,Set<BlockPos> platform,BlockPos shift){this.platform=platform;carry=arm.carryLoad()&&arm.canCarryLoad();floor=platform.iterator().next().getY();height=arm.loadHeight();for(BlockPos column:arm.columns()){int x=arm.getPos().getX()+column.getX()+shift.getX(),z=arm.getPos().getZ()+column.getZ()+shift.getZ();minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}}
        boolean contains(BlockPos at){return platform.contains(at)||carry&&at.getY()>floor&&at.getY()<=floor+height&&at.getX()>=minX&&at.getX()<=maxX&&at.getZ()>=minZ&&at.getZ()<=maxZ;}
    }
    static boolean acceptsVehicle(GearTileEntity arm,net.minecraft.entity.Entity vehicle,double progress){
        if(!arm.joinEnabled()||arm.columns().size()<2||!arm.carryLoad()||!arm.canCarryLoad()||arm.payload()==null)return false;
        int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
        for(BlockPos column:arm.columns()){BlockPos at=arm.getPos().add(column);minX=Math.min(minX,at.getX());maxX=Math.max(maxX,at.getX());minZ=Math.min(minZ,at.getZ());maxZ=Math.max(maxZ,at.getZ());}
        double dy=arm.payload().transform(arm,progress,0,0,0).y-arm.payload().offset.getY();
        double top=arm.localCell(0,-arm.assembly().columnHeight(),0).getY()+1+dy;
        net.minecraft.util.math.AxisAlignedBB body=vehicle.getEntityBoundingBox();
        return body.minX>=minX-1e-6&&body.maxX<=maxX+1+1e-6&&body.minZ>=minZ-1e-6&&body.maxZ<=maxZ+1+1e-6&&body.minY>=top-.15&&body.maxY<=top+arm.loadHeight()+1e-6;
    }
    private JoinedArms(){}
}
