package com.vandorlabs.segmented;

/** Immutable prepared row styles. World adapters establish loaded identity and common motion/settings first. */
public final class SegmentedGateContext {
    private final int height,width,column,direction,compatible;
    private final boolean straight,motionKnown;
    private final double depth;
    private final int[] own,left,right;
    public SegmentedGateContext(int height,int width,int column,int direction,boolean straight,double depth,boolean motionKnown,int compatible,int[] own,int[] left,int[] right){
        if(height<1||height>32||width<1||width>32||column<0||column>=width||direction<0||direction>3||Double.isNaN(depth)||Double.isInfinite(depth)||Math.abs(depth)>.5||(compatible&~3)!=0)throw new IllegalArgumentException("Gate row context");
        this.height=height;this.width=width;this.column=column;this.direction=direction;this.straight=straight;this.depth=depth;this.motionKnown=motionKnown;this.compatible=compatible;this.own=styles(own,height);this.left=left==null?null:styles(left,height);this.right=right==null?null:styles(right,height);
        if((compatible&1)!=0&&(column==0||left==null)||(compatible&2)!=0&&(column==width-1||right==null))throw new IllegalArgumentException("Gate context neighbour boundary");
    }
    private static int[] styles(int[] source,int height){if(source==null||source.length!=height)throw new IllegalArgumentException("Gate row style count");int[] copy=source.clone();for(int style:copy)if(style< -1||style>2)throw new IllegalArgumentException("Gate row style");return copy;}
    public int kind(int row){return own[row];}
    public int retainedBytes(){return 128+16+own.length*4+(left==null?0:16+left.length*4)+(right==null?0:16+right.length*4);}
    public static final class Scratch {
        private final double[][] frames=new double[5][9];
        private final double[] pose=new double[6];
        private final int[] kinds=new int[5],masks=new int[32];
        private SegmentedGateContext prepared;
    }
    /** Pose calculations are arithmetic only; no model/world access or allocation. */
    public int mask(int row,double progress,int rotation,Scratch scratch){
        if(row<0||row>=height||rotation<0||rotation>3||scratch==null||Double.isNaN(progress)||Double.isInfinite(progress))throw new IllegalArgumentException("Gate row pose");
        if(own[row]<0||progress>0&&progress<1&&!motionKnown)return 0;int sides=compatible;
        scratch.kinds[0]=own[row];scratch.kinds[1]=left==null?-1:left[row];scratch.kinds[2]=right==null?-1:right[row];scratch.kinds[3]=row+1<height?own[row+1]:-1;scratch.kinds[4]=row>0?own[row-1]:-1;
        write(row,column,0,progress,rotation,scratch,0);if((sides&1)!=0)write(row,column-1,-1,progress,rotation,scratch,1);if((sides&2)!=0)write(row,column+1,1,progress,rotation,scratch,2);
        if(row+1<height){sides|=4;write(row+1,column,0,progress,rotation,scratch,3);}if(row>0){sides|=8;write(row-1,column,0,progress,rotation,scratch,4);}
        return SegmentedGateJoin.mask(scratch.kinds,sides,scratch.frames);
    }
    public void prepare(double progress,int rotation,Scratch scratch){if(scratch==null)throw new IllegalArgumentException("Gate context scratch");scratch.prepared=null;for(int row=0;row<height;row++)scratch.masks[row]=mask(row,progress,rotation,scratch);scratch.prepared=this;}
    public int preparedMask(int row,Scratch scratch){if(row<0||row>=height||scratch==null||scratch.prepared!=this)throw new IllegalStateException("Gate context was not prepared");return scratch.masks[row];}
    private void write(int row,int col,int offset,double progress,int rotation,Scratch scratch,int slot){
        SegmentedDoorGeometry.panel(row,height,col,width,progress,direction,straight,scratch.pose);scratch.pose[2]+=depth;frame(scratch.pose,offset,rotation,scratch.frames[slot]);
    }
    /** Same rigid transform as SegmentedPanelTransform, evaluated at the local midplane origin and basis. */
    public static void frame(double[] pose,int columnOffset,int rotation,double[] out){
        if(pose==null||pose.length<6||out==null||out.length<9||rotation<0||rotation>3)throw new IllegalArgumentException("Gate frame output");boolean vertical=pose[5]!=0;double sin=pose[3],cos=pose[4],x=(vertical?0:pose[0]-.5*cos)+columnOffset,y=pose[1]-.5*(vertical?cos:1),z=pose[2]-.5*sin;
        out[0]=rotation==1?z:rotation==2?1-x:rotation==3?1-z:x;out[1]=y;out[2]=rotation==1?1-x:rotation==2?1-z:rotation==3?x:z;
        basis(vertical?1:cos,0,vertical?0:sin,rotation,out,3);basis(0,vertical?cos:1,vertical?sin:0,rotation,out,6);
    }
    private static void basis(double x,double y,double z,int rotation,double[] out,int at){out[at]=rotation==1?z:rotation==2?-x:rotation==3?-z:x;out[at+1]=y;out[at+2]=rotation==1?-x:rotation==2?-z:rotation==3?x:z;}
}
