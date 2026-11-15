package com.vandorlabs.ports;

/** Dynamic charge markings projected onto an existing rectangular shell face. No world or renderer state. */
public final class BatteryChargeBars {
    public interface Sink {
        void beginQuad(int rgb,float nx,float ny,float nz);
        void vertex(double x,double y,double z);
    }
    private BatteryChargeBars(){}
    public static double tiledSideU(double u,boolean hexagonal){return hexagonal?4+u*.5:u;}
    /** Corners are top-left, bottom-left, bottom-right, top-right, matching the authored Battery shell. */
    public static void emit(double[] corners,float nx,float ny,float nz,double charge,Sink sink){
        rect(corners,.08,.92,.77,.95,.0015,nx,ny,nz,0x18221f,sink);
        for(int i=0;i<10;i++){
            double left=.11+i*.078,right=left+.064;
            rect(corners,left,right,.80,.92,.002,nx,ny,nz,0x233f2b,sink);
            double fraction=Math.max(0,Math.min(1,charge*10-i));
            if(fraction>0)rect(corners,left,left+(right-left)*fraction,.80,.92,.0025,nx,ny,nz,0x62ed82,sink);
        }
    }
    private static void rect(double[] q,double left,double right,double top,double bottom,double offset,float nx,float ny,float nz,int color,Sink sink){sink.beginQuad(color,nx,ny,nz);point(q,left,top,offset,nx,ny,nz,sink);point(q,left,bottom,offset,nx,ny,nz,sink);point(q,right,bottom,offset,nx,ny,nz,sink);point(q,right,top,offset,nx,ny,nz,sink);}
    private static void point(double[] q,double u,double v,double offset,float nx,float ny,float nz,Sink sink){double a=(1-u)*(1-v),b=(1-u)*v,c=u*v,d=u*(1-v);sink.vertex(q[0]*a+q[3]*b+q[6]*c+q[9]*d+nx*offset,q[1]*a+q[4]*b+q[7]*c+q[10]*d+ny*offset,q[2]*a+q[5]*b+q[8]*c+q[11]*d+nz*offset);}
}
