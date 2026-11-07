package com.vandorlabs.render;

/** Released ordinary glass plane/metadata policy; saved detail size remains independent of depth. */
public final class FlatGlassPolicy {
    public static final class Pose {
        public final boolean rotated;public final int depth,size;
        public Pose(boolean rotated,int depth,int size){if(depth<0||depth>2||size<0||size>2)throw new IllegalArgumentException("Flat glass pose");this.rotated=rotated;this.depth=depth;this.size=size;}
        public int metadata(){return depth==0?(rotated?1:0)|(size<<1):4+depth*2+(rotated?1:0);}
        public double coordinate(int index){double start=PanelDepthPolicy.start(depth)/16D;switch(index){case 0:return rotated?1D-start-.25D:0;case 1:return 0;case 2:return rotated?0:start;case 3:return rotated?1D-start:1;case 4:return 1;case 5:return rotated?1:start+.25D;default:throw new IndexOutOfBoundsException();}}
    }
    private FlatGlassPolicy(){}
    public static Pose metadata(int meta){int size=meta<6?(meta>>1)&3:1;int depth=meta>=6&&meta<10?(meta-4)/2:0;return new Pose((meta&1)!=0,depth,size<3?Math.min(size,1):1);}
    /** The nearest endpoint uses center/near/far priority for exact distance ties. */
    public static int endpointDepth(double pixels){int result=0;double distance=Double.POSITIVE_INFINITY;for(int depth=0;depth<3;depth++){double delta=Math.abs(PanelDepthPolicy.start(depth)-pixels);if(delta<distance){result=depth;distance=delta;}}return result;}
    /** inheritedRotation is -1 when the clicked block supplies no ordinary-glass plane. */
    public static Pose placement(boolean fallbackRotation,int inheritedRotation,float hitX,float hitZ){if(inheritedRotation<-1||inheritedRotation>1)throw new IllegalArgumentException("Glass plane inheritance");boolean rotated=inheritedRotation<0?fallbackRotation:inheritedRotation!=0;return new Pose(rotated,PanelDepthPolicy.fromHit(rotated?1F-hitX:hitZ),1);}
    /** Called only for a full-height diagonal support clicked on its vertical endpoint. */
    public static Pose diagonalEndpoint(int facing,double pixels){boolean rotated=facing==4||facing==5;int depth=endpointDepth(pixels);if(facing!=(rotated?5:2))depth=depth==1?2:depth==2?1:0;return new Pose(rotated,depth,1);}
}
