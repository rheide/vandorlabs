package com.vandorlabs.compat;

import com.vandorlabs.persistence.LegacyBlockStates;
import com.vandorlabs.ramp.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Retained source stair geometry, independent of neighbors after capture. */
public final class RampStairs {
    public static final String STATE="RampStairState";
    private static final java.util.concurrent.ConcurrentMap<IBlockState,List<RampGeometry.Box>> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    private RampStairs(){}
    public static boolean supported(IBlockState state){
        return state.getBlock().getClass()==BlockStairs.class
                || state.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs;
    }
    public static NBTTagCompound capture(World world,BlockPos pos){
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        NBTTagCompound data=tile==null?new NBTTagCompound():tile.writeToNBT(new NBTTagCompound());
        data.setString(STATE,LegacyBlockStates.encode(world.getBlockState(pos).getActualState(world,pos)));
        return data;
    }
    public static IBlockState state(IBlockState fallback,NBTTagCompound data){
        IBlockState saved=data!=null&&data.hasKey(STATE,8)?LegacyBlockStates.decode(data.getString(STATE)):null;
        return saved!=null&&supported(saved)?saved:fallback;
    }
    public static List<RampGeometry.Box> boxes(World world,IBlockState state){
        return SHAPES.computeIfAbsent(state,key->nativeBoxes(world,key));
    }
    private static List<RampGeometry.Box> nativeBoxes(World world,IBlockState state){
        List<AxisAlignedBB> nativeBoxes=new ArrayList<>();
        state.addCollisionBoxToList(world,BlockPos.ORIGIN,new AxisAlignedBB(-1,-1,-1,2,2,2),nativeBoxes,null,true);
        List<RampGeometry.Box> result=new ArrayList<>();
        for(AxisAlignedBB b:nativeBoxes)result.add(new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        return Collections.unmodifiableList(result);
    }
    private static RampGeometry.Box intersect(RampGeometry.Box a,RampGeometry.Box b){
        double x=Math.max(a.minX,b.minX),y=Math.max(a.minY,b.minY),z=Math.max(a.minZ,b.minZ),X=Math.min(a.maxX,b.maxX),Y=Math.min(a.maxY,b.maxY),Z=Math.min(a.maxZ,b.maxZ);
        return X-x>1e-8&&Y-y>1e-8&&Z-z>1e-8?new RampGeometry.Box(x,y,z,X,Y,Z):null;
    }
    public static List<RampGeometry.Box> treads(World world,IBlockState state,RampGeometry.Direction face,BlockPos source,int row,int length,double start,double end,int pixels,double pose,boolean elevator,int travel,boolean extend,boolean fast,int interpolation){
        List<RampGeometry.Box> result=new ArrayList<>();
        for(RampGeometry.Box shape:boxes(world,state))for(int step=0;step<(elevator?1:ControllerPlatform.treadCount(pixels));step++){
            RampGeometry.Box footprint=RampGeometry.footprintPixels(face,step,elevator?16:pixels,shape.minY,shape.maxY),piece=intersect(shape,footprint);if(piece==null)continue;
            RampGeometry.Box moved=RampGeometry.movingTread(face,source.getX(),source.getY(),source.getZ(),shape.minY,shape.maxY,row,length,start,end,pixels,step,pose,pose,elevator,travel,extend,fast,interpolation);
            result.add(new RampGeometry.Box(moved.minX+piece.minX-footprint.minX,moved.minY,moved.minZ+piece.minZ-footprint.minZ,
                    moved.maxX+piece.maxX-footprint.maxX,moved.maxY,moved.maxZ+piece.maxZ-footprint.maxZ));
        }
        return result;
    }
    public static List<RampGeometry.Box> moat(World world,IBlockState state,RampGeometry.Direction face,int hinge,int baseY,int length,int pixels,double start,double end,double pose,boolean fast,BlockPos source){
        List<RampGeometry.Box> result=new ArrayList<>();
        for(RampGeometry.Box shape:boxes(world,state))for(RampGeometry.Box piece:MoatGeometry.slices(shape,pixels))result.add(MoatGeometry.box(face,hinge,baseY,length,start,end,pose,fast,new RampGeometry.Box(source.getX()+piece.minX,source.getY()+piece.minY,source.getZ()+piece.minZ,source.getX()+piece.maxX,source.getY()+piece.maxY,source.getZ()+piece.maxZ)));
        return result;
    }
    public static List<RampGeometry.Box> legacyMoat(World world,IBlockState state,RampGeometry.Direction face,int hinge,int sourceY,double high,double start,double end,double pose,boolean fast,BlockPos source){
        List<RampGeometry.Box> result=new ArrayList<>();boolean alongX=face==RampGeometry.Direction.EAST||face==RampGeometry.Direction.WEST;
        for(RampGeometry.Box shape:boxes(world,state))for(int i=0;i<16;i++){
            RampGeometry.Box slice=new RampGeometry.Box(alongX?i/16D:0,0,alongX?0:i/16D,alongX?(i+1)/16D:1,1,alongX?1:(i+1)/16D),piece=intersect(shape,slice);if(piece==null)continue;
            result.add(LegacyMoatGeometry.box(face,hinge,sourceY,high,start,end,pose,fast,new RampGeometry.Box(source.getX()+piece.minX,source.getY()+piece.minY,source.getZ()+piece.minZ,source.getX()+piece.maxX,source.getY()+piece.maxY,source.getZ()+piece.maxZ)));
        }
        return result;
    }
    public static List<RampGeometry.Box> escalator(World world,IBlockState state,RampGeometry.Direction face,BlockPos source,int row,int length,int pixels,double rise,double distance,int interpolation){
        List<RampGeometry.Box> result=new ArrayList<>();double pitch=pixels/16D,shift=EscalatorGeometry.phase(distance,length*16);boolean axisX=face==RampGeometry.Direction.EAST||face==RampGeometry.Direction.WEST,reverse=face==RampGeometry.Direction.NORTH||face==RampGeometry.Direction.WEST;
        for(RampGeometry.Box shape:boxes(world,state))for(int step=0;step<ControllerPlatform.treadCount(pixels);step++){
            RampGeometry.Box piece=intersect(shape,RampGeometry.footprintPixels(face,step,pixels,0,1));if(piece==null)continue;
            double anchor=EscalatorGeometry.phase(row+step*pitch+shift,length*16);
            for(int copy=0;copy<2;copy++){
                double at=anchor-copy*length,delta=(at-row-step*pitch)*(reverse?-1:1),offset=EscalatorGeometry.height(at,length,pixels,rise,interpolation);
                double a=(axisX?piece.minX:piece.minZ)+delta,b=(axisX?piece.maxX:piece.maxZ)+delta;
                double laneLow=reverse?row+1-length:-row,laneHigh=reverse?row+1:length-row;a=Math.max(a,laneLow);b=Math.min(b,laneHigh);if(b-a<1e-8)continue;
                result.add(new RampGeometry.Box(source.getX()+(axisX?a:piece.minX),source.getY()+piece.minY+offset,source.getZ()+(axisX?piece.minZ:a),source.getX()+(axisX?b:piece.maxX),source.getY()+piece.maxY+offset,source.getZ()+(axisX?piece.maxZ:b)));
            }
        }
        return result;
    }
}
