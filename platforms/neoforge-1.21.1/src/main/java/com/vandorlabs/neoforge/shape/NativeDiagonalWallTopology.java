package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.persistence.WallSettings;
import com.vandorlabs.render.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded local diagonal joins and end contacts; invoked only by deferred topology publication. */
public final class NativeDiagonalWallTopology {
    private static final WallSettings DEFAULT=new WallSettings(false,false,0,0,false,0);
    public static int axis(BlockPos delta,Direction direction){return delta.getX()*direction.getStepX()+delta.getY()*direction.getStepY()+delta.getZ()*direction.getStepZ();}
    private static WallSettings settings(BlockGetter level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeWallEntity host?host.wallSettings():DEFAULT;}
    public static int mode(BlockGetter level,BlockPos pos){var value=settings(level,pos);return WallSettings.geometry(value.halfHeight,value.fullWidth);}
    public static NativeDiagonalWallGeometry.Pose initial(BlockState state,int mode){return new NativeDiagonalWallGeometry.Pose(state.getValue(NativePanelBlock.FACING),state.getValue(NativeDiagonalWallBlock.INVERTED),mode,0,null,Double.NaN,Double.NaN,false,false,NativeDiagonalWallGeometry.Clip.NONE,NativePanelLayout.UNIT);}
    public static void refresh(NativeWallEntity host){
        if(!NativeFluidWorld.installed(host))return;Level level=host.getLevel();BlockPos pos=host.getBlockPos();BlockState state=host.getBlockState();if(!(state.getBlock() instanceof NativeDiagonalWallBlock))return;
        int mode=mode(level,pos),fill=host.wallSettings().fill;
        double lower=flatEnd(level,pos,state,mode,false),upper=flatEnd(level,pos,state,mode,true);
        var pose=new NativeDiagonalWallGeometry.Pose(state.getValue(NativePanelBlock.FACING),state.getValue(NativeDiagonalWallBlock.INVERTED),mode,fill,corner(level,pos,state,mode),lower,upper,
                coveredEnd(level,pos,state,mode,false)||fill==0&&!Double.isNaN(lower),coveredEnd(level,pos,state,mode,true)||fill==0&&!Double.isNaN(upper),clip(level,pos,state,mode),NativePanelLayout.UNIT);
        if(host.wallAppearance().geometry() instanceof NativeDiagonalWallGeometry.Geometry current&&current.pose().equals(pose))return;
        host.publishGeometry(NativeDiagonalWallGeometry.build(pose),host.fluidGeometryRevision()+1);
    }
    private static boolean flat(BlockState state){return state.getBlock() instanceof NativeFlatWallBlock;}
    private static boolean diagonal(BlockState state){return state.getBlock() instanceof NativeDiagonalWallBlock;}
    private static boolean plainDiagonal(BlockState state){return state.getBlock() instanceof NativeDiagonalWallBlock block&&!block.isPorthole();}
    private static boolean enabled(Level level,BlockPos pos){return NativeFluidWorld.loaded(level,pos);}
    private static boolean cornerParticipant(Level level,BlockPos pos,BlockState state){return diagonal(state)&&mode(level,pos)!=2&&(!(state.getBlock() instanceof NativeDiagonalWallBlock block)||!block.isPorthole()||mode(level,pos)==1);}
    private static boolean cornerEnabled(Level level,BlockPos pos){
        if(!enabled(level,pos))return false;if(mode(level,pos)!=1)return true;var settings=settings(level,pos);return settings.join!=1&&(!(level.getBlockState(pos).getBlock() instanceof NativeDiagonalWallBlock block)||!block.isPorthole()||settings.joinPortholes);
    }
    private static NativeDiagonalWallGeometry.Join corner(Level level,BlockPos pos,BlockState state,int mode){
        if(!cornerParticipant(level,pos,state)||!cornerEnabled(level,pos))return null;
        int override=settings(level,pos).join;if(override==1)return null;
        if(override>1){boolean right=(override&1)!=0,front=override<4||override==6||override==7;return override>=10?new NativeDiagonalWallGeometry.Join(right,right,true,true,null,null):new NativeDiagonalWallGeometry.Join(front?right:null,front?null:right,override>=6,override>=6,null,null);}
        Direction facing=state.getValue(NativePanelBlock.FACING);Boolean frontRight=null,backRight=null;Double frontFlat=null,backFlat=null;
        for(boolean front:new boolean[]{true,false}){
            BlockPos next=pos.relative(front?facing:facing.getOpposite());if(!cornerEnabled(level,next))continue;BlockState other=level.getBlockState(next);
            if(flat(other)){
                Direction nf=other.getValue(NativePanelBlock.FACING);if(nf.getAxis()==facing.getAxis())continue;boolean right=nf==facing.getClockWise();double near=PanelDepthPolicy.start(other.getValue(NativeFlatWallBlock.DEPTH)),arm=right?12-near:near;
                if(front){frontRight=right;frontFlat=arm;}else{backRight=right;backFlat=arm;}continue;
            }
            if(!cornerParticipant(level,next,other)||mode(level,next)!=mode||other.getValue(NativeDiagonalWallBlock.INVERTED)!=state.getValue(NativeDiagonalWallBlock.INVERTED))continue;
            Direction nf=other.getValue(NativePanelBlock.FACING);Boolean right=nf==facing.getClockWise()?Boolean.TRUE:nf==facing.getCounterClockWise()?Boolean.FALSE:null;if(front)frontRight=right;else backRight=right;
        }
        if(frontRight==null&&backRight==null)return null;
        return new NativeDiagonalWallGeometry.Join(frontRight,backRight,straight(level,pos,state,mode,facing.getCounterClockWise()),straight(level,pos,state,mode,facing.getClockWise()),frontFlat,backFlat);
    }
    private static boolean straight(Level level,BlockPos pos,BlockState state,int mode,Direction direction){
        BlockPos next=pos.relative(direction);if(!enabled(level,next))return false;BlockState other=level.getBlockState(next);
        if(mode==1){if(!cornerParticipant(level,next,other)||!cornerEnabled(level,pos)||!cornerEnabled(level,next))return false;}
        else if(other.getBlock()!=state.getBlock()&&!(plainDiagonal(state)&&plainDiagonal(other)))return false;
        return diagonal(other)&&other.getValue(NativePanelBlock.FACING)==state.getValue(NativePanelBlock.FACING)&&other.getValue(NativeDiagonalWallBlock.INVERTED)==state.getValue(NativeDiagonalWallBlock.INVERTED)&&mode(level,next)==mode;
    }
    private static boolean coveredEnd(Level level,BlockPos pos,BlockState state,int mode,boolean upper){
        Direction facing=state.getValue(NativePanelBlock.FACING),row=mode==2?facing.getOpposite():Direction.UP,normal=mode==2?Direction.UP:facing.getOpposite();
        for(int offset=-1;offset<=1;offset++){
            BlockPos next=pos.relative(row,upper?1:-1).relative(normal,offset);if(!enabled(level,next))continue;BlockState other=level.getBlockState(next);
            if(mode==1&&(!cornerEnabled(level,pos)||!cornerEnabled(level,next)))continue;
            boolean compatible=other.getBlock()==state.getBlock()||plainDiagonal(state)&&plainDiagonal(other)||mode==1&&cornerParticipant(level,pos,state)&&cornerParticipant(level,next,other);
            if(!compatible||mode(level,next)!=mode||settings(level,pos).fill!=settings(level,next).fill)continue;
            Direction nf=other.getValue(NativePanelBlock.FACING);if(nf.getAxis()!=facing.getAxis())continue;
            if(DiagonalPanelContact.endsMeet(mode,state.getValue(NativeDiagonalWallBlock.INVERTED),other.getValue(NativeDiagonalWallBlock.INVERTED),nf!=facing,next.getY()-pos.getY(),axis(next.subtract(pos),facing.getOpposite()),upper))return true;
        }return false;
    }
    private static double flatEnd(Level level,BlockPos pos,BlockState state,int mode,boolean upper){
        if(mode==2||coveredEnd(level,pos,state,mode,upper))return Double.NaN;
        Direction facing=state.getValue(NativePanelBlock.FACING);double expected=DiagonalWallGeometry.near(mode,state.getValue(NativeDiagonalWallBlock.INVERTED),upper?1:0),best=Double.NaN,distance=.1250001;
        for(int offset=-1;offset<=1;offset++){
            BlockPos next=pos.relative(upper?Direction.UP:Direction.DOWN).relative(facing.getOpposite(),offset);if(!enabled(level,next))continue;BlockState other=level.getBlockState(next);if(!flat(other))continue;
            Direction nf=other.getValue(NativePanelBlock.FACING);if(nf.getAxis()!=facing.getAxis())continue;double near=PanelDepthPolicy.start(other.getValue(NativeFlatWallBlock.DEPTH))/16D;if(nf!=facing)near=.75-near;near+=offset;
            if(Math.abs(near-expected)<distance){best=near;distance=Math.abs(near-expected);}
        }return best;
    }
    private static NativeDiagonalWallGeometry.Clip clip(Level level,BlockPos pos,BlockState state,int mode){
        double[] bounds={-2,-2,-2,3,3,3};List<double[]> obstacles=new ArrayList<>();Direction facing=state.getValue(NativePanelBlock.FACING);boolean inverted=state.getValue(NativeDiagonalWallBlock.INVERTED);Direction[] positive={facing.getClockWise(),Direction.UP,facing.getOpposite()};
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){
            Direction direction=side==0?positive[axis].getOpposite():positive[axis];BlockPos next=pos.relative(direction);if(!enabled(level,next))continue;BlockState other=level.getBlockState(next);boolean opposing=false;
            if(diagonal(other)&&mode(level,next)==mode&&(mode==2?axis==1:axis==2)){
                Direction nf=other.getValue(NativePanelBlock.FACING);if(nf.getAxis()==facing.getAxis())opposing=(other.getValue(NativeDiagonalWallBlock.INVERTED)^(nf!=facing))!=inverted;
            }
            if(opposing)bounds[axis+side*3]=side==0?0:1;
            if(other.isCollisionShapeFullBlock(level,next)){double[] box={0,0,0,1,1,1};box[axis]=side==0?-1:1-1D/4096;box[axis+3]=side==0?1D/4096:2;obstacles.add(box);}
        }
        double[] result=Arrays.copyOf(bounds,6+6*obstacles.size());for(int i=0;i<obstacles.size();i++)System.arraycopy(obstacles.get(i),0,result,6+6*i,6);return new NativeDiagonalWallGeometry.Clip(result);
    }
    private NativeDiagonalWallTopology(){}
}
