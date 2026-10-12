package com.vandorlabs.ramp;

import com.vandorlabs.render.DiagonalCornerOutline;

/** Compare the emitted boundary to the actual pane/arm solid union. */
public final class DiagonalCornerOutlineTest {
    private DiagonalCornerOutlineTest() {}
    public static void main(String[] args) {run();}
    public static void run() {
        int cases=0;
        for(int fi=-1;fi<2;fi++)for(int bi=-1;bi<2;bi++)if(fi>=0||bi>=0)
        for(double near=-2;near<=14;near+=.25) {
            double front=fi<0?Double.NaN:fi==0?near:12-near;
            double back=bi<0?Double.NaN:bi==0?near:12-near;
            double left=16,right=0;
            if(fi>=0){left=Math.min(left,front);right=Math.max(right,front+4);}
            if(bi>=0){left=Math.min(left,back);right=Math.max(right,back+4);}
            if((fi<0||bi<0)&&(fi==0||bi==0))left=0;
            if((fi<0||bi<0)&&(fi==1||bi==1))right=16;
            left=DiagonalCornerOutline.left(left,near,front,back);
            right=DiagonalCornerOutline.right(right,near,front,back);
            double[][] outline=DiagonalCornerOutline.polygon(left,right,near,near+4,front,back);
            for(double x=-2.83;x<19;x+=.53)for(double z=-2.79;z<19;z+=.57) {
                boolean expected=inside(x,z,left,right,near,near+4)
                        || fi>=0 && near>0 && inside(x,z,front,front+4,0,near)
                        || bi>=0 && near+4<16 && inside(x,z,back,back+4,near+4,16);
                if(contains(outline,x,z)!=expected)throw new AssertionError("corner boundary differs from solid union: "+fi+"/"+bi+" near="+near+" at "+x+","+z);
            }
            cases++;
        }
        System.out.println("PASS: diagonal corner pane/arm union "+cases+" mirrored front/back cross-sections including slope overhangs");
    }
    private static boolean inside(double x,double z,double left,double right,double near,double far) {
        return x>left && x<right && z>near && z<far;
    }
    private static boolean contains(double[][] polygon,double x,double z) {
        boolean inside=false;
        for(int i=0,j=polygon.length-1;i<polygon.length;j=i++) {
            double[] a=polygon[i],b=polygon[j];
            if((a[1]>z)!=(b[1]>z) && x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])inside=!inside;
        }
        return inside;
    }
}
