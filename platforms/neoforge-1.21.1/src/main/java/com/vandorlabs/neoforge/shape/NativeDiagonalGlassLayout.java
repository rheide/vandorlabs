package com.vandorlabs.neoforge.shape;

import com.vandorlabs.render.DiagonalPanelContact;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;

/** Owned worker projection. Artwork partitions and direct cap contacts are independent. */
public record NativeDiagonalGlassLayout(NativePanelLayout group,int hideMask,int rowAxis) {
    public NativeDiagonalGlassLayout {
        if(group==null||(hideMask&~63)!=0||(rowAxis!=1&&rowAxis!=2))throw new IllegalArgumentException("Diagonal glass layout");
    }
    public static NativeDiagonalGlassLayout unit(int mode){return new NativeDiagonalGlassLayout(NativePanelLayout.UNIT,0,mode==2?2:1);}
    static NativeDiagonalGlassLayout capture(NativeDiagonalWallTopology.Access access,BlockPos pos,BlockState state,NativePanelLayout group){
        if(!access.loaded(pos))throw new IllegalArgumentException("Loaded diagonal glass root required");
        var settings=access.settings(pos);int mode=com.vandorlabs.persistence.WallSettings.geometry(settings.halfHeight,settings.fullWidth),mask=0;
        Direction face=state.getValue(NativePanelBlock.FACING),right=face.getClockWise();boolean inverted=state.getValue(NativeDiagonalWallBlock.INVERTED);
        for(int side=0;side<2;side++){
            BlockPos next=pos.relative(side==0?right.getOpposite():right);if(!access.loaded(next))continue;var other=access.state(next);
            if(other.getBlock()!=state.getBlock())continue;var os=access.settings(next);if(com.vandorlabs.persistence.WallSettings.geometry(os.halfHeight,os.fullWidth)!=mode||os.fill!=settings.fill)continue;
            Direction of=other.getValue(NativePanelBlock.FACING);var delta=next.subtract(pos);
            if(of.getAxis()==face.getAxis()&&DiagonalPanelContact.samePlane(mode,inverted,other.getValue(NativeDiagonalWallBlock.INVERTED),of!=face,delta.getY(),NativeDiagonalWallTopology.axis(delta,face.getOpposite())))mask|=1<<side;
        }
        if(mode!=2){Direction[] steps={right.getOpposite(),right,face,face.getOpposite()};for(int side=0;side<4;side++){
            BlockPos next=pos.relative(steps[side]);if(!access.loaded(next))continue;var other=access.state(next);if(other.getBlock()!=state.getBlock())continue;var os=access.settings(next);
            if(other.getValue(NativePanelBlock.FACING).getAxis()!=face.getAxis()&&other.getValue(NativeDiagonalWallBlock.INVERTED)==inverted&&com.vandorlabs.persistence.WallSettings.geometry(os.halfHeight,os.fullWidth)==mode&&os.fill==settings.fill)mask|=1<<side;
        }}
        if(NativeDiagonalWallTopology.coveredEnd(access,pos,state,mode,false))mask|=16;
        if(NativeDiagonalWallTopology.coveredEnd(access,pos,state,mode,true))mask|=32;
        return new NativeDiagonalGlassLayout(group,mask,mode==2?2:1);
    }
}
