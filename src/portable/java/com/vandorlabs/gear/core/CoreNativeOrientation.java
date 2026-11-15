package com.vandorlabs.gear.core;

/** Native Core states can encode only proper, upright cardinal yaw. */
public final class CoreNativeOrientation {
    public static boolean yaw(int[] m){return m!=null&&m.length==9&&m[1]==0&&m[3]==0&&m[4]==1&&m[5]==0&&m[7]==0&&m[0]==m[8]&&m[2]==-m[6]&&(Math.abs(m[0])==1&&m[2]==0||m[0]==0&&Math.abs(m[2])==1);}
    public static boolean endpoint(GearMotion.Mode mode,int mount,GearMotion.Direction fold,double angle){
        if(mode==null||mount<0||mount>5||fold==null||!Double.isFinite(angle)||Math.abs(angle)>180)throw new IllegalArgumentException("Invalid Core endpoint orientation");
        if(mode!=GearMotion.Mode.ROTATE)return true;
        double quarter=angle/90;int turns=(int)Math.rint(quarter);if(Math.abs(quarter-turns)>1e-8)return false;
        int ax=(int)GearMountFrame.x(mount,fold.axisX(),0,fold.axisZ()),ay=(int)GearMountFrame.y(mount,fold.axisX(),0,fold.axisZ()),az=(int)GearMountFrame.z(mount,fold.axisX(),0,fold.axisZ());
        int sin=turns==1?1:turns==-1?-1:0,cos=turns==0?1:Math.abs(turns)==2?-1:0;int[] m=new int[9];
        for(int col=0;col<3;col++){int x=col==0?1:0,y=col==1?1:0,z=col==2?1:0,dot=ax*x+ay*y+az*z;m[col]=x*cos+(ay*z-az*y)*sin+ax*dot*(1-cos);m[3+col]=y*cos+(az*x-ax*z)*sin+ay*dot*(1-cos);m[6+col]=z*cos+(ax*y-ay*x)*sin+az*dot*(1-cos);}return yaw(m);
    }
    /** Immutable snapshot hosts keep one cache; no arrays, tiles or NBT are sampled on a cache hit. */
    public static final class Cache {
        private GearMotion.Mode mode;private GearMotion.Direction fold;private int mount=-1;private double start,end;private boolean startAllowed,endAllowed;
        public boolean allows(GearMotion.Mode mode,int mount,GearMotion.Direction fold,double start,double end,boolean active){
            if(mode==null||fold==null||mount<0||mount>5||!Double.isFinite(start)||!Double.isFinite(end)||Math.abs(start)>180||Math.abs(end)>180)throw new IllegalArgumentException("Invalid Core endpoint cache input");
            if(this.mode!=mode||this.mount!=mount||this.fold!=fold||this.start!=start||this.end!=end){this.mode=mode;this.mount=mount;this.fold=fold;this.start=start;this.end=end;startAllowed=endpoint(mode,mount,fold,start);endAllowed=endpoint(mode,mount,fold,end);}return active?endAllowed:startAllowed;
        }
    }
    private CoreNativeOrientation(){}
}
