package com.vandorlabs.render;

/** Released GUI projection and padding, without a platform vertex format or matrix class. */
public final class ItemIconBounds {
    private final float rx,ry,rz,sx,sy,sz,tz;
    private final double sinX,cosX,sinY,cosY,sinZ,cosZ;
    private double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
    public ItemIconBounds(float rx,float ry,float rz,float sx,float sy,float sz,float tz){
        this.rx=rx;this.ry=ry;this.rz=rz;this.sx=sx;this.sy=sy;this.sz=sz;this.tz=tz;
        sinX=Math.sin(Math.toRadians(rx));cosX=Math.cos(Math.toRadians(rx));
        sinY=Math.sin(Math.toRadians(ry));cosY=Math.cos(Math.toRadians(ry));
        sinZ=Math.sin(Math.toRadians(rz));cosZ=Math.cos(Math.toRadians(rz));
    }
    public void include(double x,double y,double z){
        x=(x-.5)*sx;y=(y-.5)*sy;z=(z-.5)*sz;
        double next=x*cosZ-y*sinZ;y=x*sinZ+y*cosZ;x=next;
        next=x*cosY+z*sinY;z=z*cosY-x*sinY;x=next;
        y=y*cosX-z*sinX;
        minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
    }
    /** Rotation, block-space translation and scale; null retains the original empty model transform. */
    public float[] fit(){
        if(!Double.isFinite(minX))return null;
        float scale=(float)Math.min(.75/Math.max(maxX-minX,maxY-minY),.85);
        return new float[]{rx,ry,rz,(float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),tz,sx*scale,sy*scale,sz*scale};
    }
}
