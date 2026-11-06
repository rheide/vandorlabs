package com.vandorlabs.render;

/** Current ordinary door leaf and fixed jamb bounds; world/half/pair resolution remains platform-owned. */
public final class SpaceDoorBounds {
    public interface Factory<B>{B create(double minX,double minY,double minZ,double maxX,double maxY,double maxZ);}
    public static final float PIN_X=1F,PIN_Z=11.24F;
    private SpaceDoorBounds(){}
    /** Horizontal facing IDs follow the saved Minecraft convention: N2/S3/W4/E5. */
    public static <B> B closed(Factory<B> boxes,int facing,boolean sliding){
        if(sliding)return facing==2||facing==3?boxes.create(0,0,.35,1,1,.70):boxes.create(.30,0,0,.65,1,1);
        switch(facing){case 3:return boxes.create(0,0,.65,1,1,1);case 5:return boxes.create(.65,0,0,1,1,1);case 4:return boxes.create(0,0,0,.35,1,1);default:return boxes.create(0,0,0,1,1,.35);}
    }
    public static <B> B rotatingOpen(Factory<B> boxes,int facing,boolean hingeLeft,boolean framed,boolean paired){
        double x0=framed?1:0,x1=framed&&!paired?15:16;
        double a=(PIN_X+12.24-PIN_Z)/16,b=(PIN_X+14.24-PIN_Z)/16;
        double c=(PIN_Z-x1+PIN_X)/16,d=(PIN_Z-x0+PIN_X)/16;
        if(hingeLeft){double oldA=a;a=1-b;b=1-oldA;}
        switch(facing){case 2:return boxes.create(1-b,0,1-d,1-a,1,1-c);case 5:return boxes.create(c,0,1-b,d,1,1-a);case 4:return boxes.create(1-d,0,a,1-c,1,b);default:return boxes.create(a,0,c,b,1,d);}
    }
    /** Apply the saved depth offset; unrotated near/far boxes retain the released cell clamp. */
    public static <B> B positioned(Factory<B> boxes,double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int facing,double offset,boolean rotatedOpen){
        double dx=(facing==5?1:facing==4?-1:0)*offset,dz=(facing==3?1:facing==2?-1:0)*offset;
        double x0=minX+dx,y0=minY+0D,z0=minZ+dz,x1=maxX+dx,y1=maxY+0D,z1=maxZ+dz;
        if(rotatedOpen)return boxes.create(x0,y0,z0,x1,y1,z1);
        if(facing==4||facing==5)return boxes.create(Math.max(0,x0),y0,z0,Math.min(1,x1),y1,z1);
        return boxes.create(x0,y0,Math.max(0,z0),x1,y1,Math.min(1,z1));
    }
}
