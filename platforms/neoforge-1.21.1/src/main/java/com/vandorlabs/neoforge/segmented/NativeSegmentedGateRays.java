package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.segmented.*;

/** Loaded action preparation on servers; the client binds its prepared renderer context. */
public final class NativeSegmentedGateRays {
    @FunctionalInterface public interface ClientMasks {void fill(NativeSegmentedDoorEntity root,double pose,int[] out);}
    private static volatile ClientMasks client=(root,pose,out)->java.util.Arrays.fill(out,0);
    private static final ThreadLocal<SegmentedGateContext.Scratch> SCRATCH=ThreadLocal.withInitial(SegmentedGateContext.Scratch::new);
    private NativeSegmentedGateRays(){}
    public static void bindClient(ClientMasks provider){client=java.util.Objects.requireNonNull(provider);}
    public static void masks(SegmentedGateContext context,double pose,double left,double right,int rotation,int[] out){context.prepare(pose,left,right,rotation,SCRATCH.get());for(int row=0;row<out.length;row++)out[row]=row<context.height()?context.preparedMask(row,SCRATCH.get()):0;}
    private static int[] styles(NativeSegmentedDoorEntity owner){var visual=owner.publication().visual();int[] result=new int[visual.height()];for(int row=0;row<result.length;row++)result[row]=NativeSegmentedArtwork.gate(visual.texture(row));return result;}
    private static boolean compatible(NativeSegmentedDoorEntity a,NativeSegmentedDoorEntity b,int offset){if(b==null||b.topOffset()!=0||!b.formed()||a.getBlockState().getBlock()!=b.getBlockState().getBlock()||a.height()!=b.height()||a.columnsCount()!=b.columnsCount()||a.column()+offset!=b.column()||a.facing()!=b.facing())return false;var x=a.settings();var y=b.settings();return x.direction==y.direction&&x.straight==y.straight&&x.position==y.position&&a.ticksPerSection()==b.ticksPerSection();}
    private static boolean moving(NativeSegmentedDoorEntity a,NativeSegmentedDoorEntity b){var motion=a.publication().motion().motionIdentity();return motion!=null&&motion.equals(b.publication().motion().motionIdentity())&&a.started()==b.started()&&a.duration()==b.duration()&&a.targetOpen()==b.targetOpen()&&Double.doubleToLongBits(a.initialPose())==Double.doubleToLongBits(b.initialPose());}
    public static void masks(NativeSegmentedDoorEntity root,double pose,int[] out){
        if(root.getLevel().isClientSide){client.fill(root,pose,out);return;}
        var right=root.facing().getClockWise();var pos=root.getBlockPos();var left=root.column()>0?NativeSegmentedTargets.loaded(root.getLevel(),pos.relative(right,-1)):null;var next=root.column()+1<root.columnsCount()?NativeSegmentedTargets.loaded(root.getLevel(),pos.relative(right)):null;
        boolean l=compatible(root,left,-1),r=compatible(root,next,1),lm=l&&moving(root,left),rm=r&&moving(root,next);var settings=root.settings();
        var context=new SegmentedGateContext(root.height(),root.columnsCount(),root.column(),settings.direction,settings.straight,settings.depthOffset(),root.publication().motion().motionIdentity()!=null,(l?1:0)|(r?2:0),(lm?1:0)|(rm?2:0),styles(root),l?styles(left):null,r?styles(next):null);
        int rotation=switch(root.facing()){case NORTH->0;case EAST->3;case SOUTH->2;case WEST->1;default->throw new IllegalArgumentException("Horizontal gate facing");};
        masks(context,pose,l?(lm?pose:left.pose(0)):Double.NaN,r?(rm?pose:next.pose(0)):Double.NaN,rotation,out);
    }
}
