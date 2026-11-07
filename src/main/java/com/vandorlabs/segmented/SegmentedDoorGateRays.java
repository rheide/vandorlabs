package com.vandorlabs.segmented;

import net.minecraft.util.EnumFacing;

/** Action-scoped server preparation; clients reuse the renderer's bounded prepared context. */
public final class SegmentedDoorGateRays {
    private static final ThreadLocal<SegmentedGateContext.Scratch> SCRATCH=ThreadLocal.withInitial(SegmentedGateContext.Scratch::new);
    private SegmentedDoorGateRays(){}
    public static void masks(SegmentedGateContext context,double pose,double left,double right,int rotation,int[] out){
        context.prepare(pose,left,right,rotation,SCRATCH.get());
        for(int row=0;row<out.length;row++)out[row]=row<contextHeight(context)?context.preparedMask(row,SCRATCH.get()):0;
    }
    private static int contextHeight(SegmentedGateContext context){return context.height();}
    private static int[] styles(SegmentedDoorTile root){int[] result=new int[root.height];for(int row=0;row<result.length;row++){SegmentedDoorTile cell=SegmentedDoorTile.at(root.getWorld(),root.getPos().down(row));result[row]=cell!=null&&cell.topOffset==row&&cell.height==root.height?SegmentedArtwork.gate(cell.effectiveTexture()):-1;}return result;}
    private static boolean compatible(SegmentedDoorTile a,SegmentedDoorTile b,int offset,EnumFacing facing){return b!=null&&b.ready()&&b.isRoot()&&a.getBlockType()==b.getBlockType()&&a.height==b.height&&a.columns==b.columns&&a.column+offset==b.column&&b.facing()==facing&&a.settings.direction==b.settings.direction&&a.settings.straight==b.settings.straight&&a.settings.position==b.settings.position&&a.ticksPerSection==b.ticksPerSection;}
    private static boolean moving(SegmentedDoorTile a,SegmentedDoorTile b){return a.motionIdentity!=null&&a.motionIdentity.equals(b.motionIdentity)&&a.started==b.started&&a.duration==b.duration&&a.targetOpen==b.targetOpen&&Double.doubleToLongBits(a.from)==Double.doubleToLongBits(b.from);}
    public static void masks(SegmentedDoorTile root,double pose,int[] out){
        EnumFacing facing=root.facing(),right=facing.rotateY();SegmentedDoorTile left=root.column>0?SegmentedDoorTile.at(root.getWorld(),root.getPos().offset(right,-1)):null,next=root.column+1<root.columns?SegmentedDoorTile.at(root.getWorld(),root.getPos().offset(right)):null;
        boolean l=compatible(root,left,-1,facing),r=compatible(root,next,1,facing),lm=l&&moving(root,left),rm=r&&moving(root,next);
        SegmentedGateContext context=new SegmentedGateContext(root.height,root.columns,root.column,root.settings.direction,root.settings.straight,root.settings.depthOffset(),root.motionIdentity!=null,(l?1:0)|(r?2:0),(lm?1:0)|(rm?2:0),styles(root),l?styles(left):null,r?styles(next):null);
        // A validated client pose may lead the server: common-driver neighbours share that requested pose.
        masks(context,pose,l?(lm?pose:left.pose(0)):Double.NaN,r?(rm?pose:next.pose(0)):Double.NaN,((int)(180-facing.getHorizontalAngle())/90)&3,out);
    }
}
