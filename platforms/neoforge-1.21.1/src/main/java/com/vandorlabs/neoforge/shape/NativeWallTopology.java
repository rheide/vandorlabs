package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.PanelCornerGeometry;
import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded local panel topology. Invoked by the deferred owning-thread geometry publication path. */
public final class NativeWallTopology {
    public static void refresh(NativeWallEntity host){
        if(!NativeFluidWorld.installed(host))return;Level level=host.getLevel();BlockPos pos=host.getBlockPos();BlockState state=host.getBlockState();
        if(state.getBlock() instanceof NativeDiagonalWallBlock){NativeDiagonalWallTopology.refresh(host);return;}
        if(!(state.getBlock() instanceof NativeFlatWallBlock))return;
        var corner=corner(level,pos,state);
        var geometry=corner==null?NativeFlatWallGeometry.straight(state.getValue(NativeFlatWallBlock.FACING),state.getValue(NativeFlatWallBlock.DEPTH)):NativeFlatWallGeometry.build(state.getValue(NativeFlatWallBlock.FACING),state.getValue(NativeFlatWallBlock.DEPTH),corner);
        host.publishGeometry(geometry.withLayout(NativeWallLayouts.layout(host)),host.fluidGeometryRevision()+1);
        host.publishApertureLayout(NativeWallLayouts.aperture(host));
    }
    private static boolean enabled(Level level,BlockPos pos){return NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity tile&&(!tile.isPorthole()||tile.wallSettings().joinPortholes);}
    private static PanelCornerGeometry.FlatCorner corner(Level level,BlockPos pos,BlockState state){
        if(!enabled(level,pos))return null;Direction facing=state.getValue(NativeFlatWallBlock.FACING);Double frontArm=null,backArm=null;Boolean frontRight=null,backRight=null;
        for(boolean front:new boolean[]{true,false}){
            BlockPos neighborPos=pos.relative(front?facing:facing.getOpposite());if(!enabled(level,neighborPos))continue;
            BlockState neighbor=level.getBlockState(neighborPos);if(!(neighbor.getBlock() instanceof NativeFlatWallBlock))continue;
            Direction nf=neighbor.getValue(NativeFlatWallBlock.FACING);Boolean right=nf==facing.getClockWise()?Boolean.TRUE:nf==facing.getCounterClockWise()?Boolean.FALSE:null;if(right==null)continue;
            double near=PanelDepthPolicy.start(neighbor.getValue(NativeFlatWallBlock.DEPTH)),arm=right?12-near:near;
            if(front){frontArm=arm;frontRight=right;}else{backArm=arm;backRight=right;}
        }
        if(frontArm==null&&backArm==null)return null;
        return new PanelCornerGeometry.FlatCorner(frontArm,backArm,frontRight,backRight,connected(level,pos,state,facing.getCounterClockWise()),connected(level,pos,state,facing.getClockWise()));
    }
    private static boolean connected(Level level,BlockPos pos,BlockState state,Direction side){
        BlockPos next=pos.relative(side);if(!enabled(level,next))return false;BlockState other=level.getBlockState(next);if(!(other.getBlock() instanceof NativeFlatWallBlock))return false;
        Direction facing=state.getValue(NativeFlatWallBlock.FACING),nf=other.getValue(NativeFlatWallBlock.FACING);if(facing.getAxis()!=nf.getAxis())return false;
        int near=PanelDepthPolicy.start(other.getValue(NativeFlatWallBlock.DEPTH));if(nf!=facing)near=12-near;
        return near==PanelDepthPolicy.start(state.getValue(NativeFlatWallBlock.DEPTH));
    }
    private NativeWallTopology(){}
}
