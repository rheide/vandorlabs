package com.vandorlabs.screen;

import com.vandorlabs.render.InputSurfaceLayout;
import java.util.Arrays;

/** Released role-specific pixel-to-owner transform; physical block bounds use a separate policy. */
public final class ScreenRenderPose {
    private ScreenRenderPose(){}
    /** Writes a column-major affine matrix into caller-owned storage, without allocation. */
    public static void write(ScreenFamily family,ScreenFamily.State state,boolean ceilingMounted,int ceilingPosition,double[] out){
        if(family==null||state==null||out==null||out.length<16||ceilingPosition < -1||ceilingPosition>2
                ||family.horizontal&&state.facing<2||state.keyboard&&!family.keyboard
                ||state.upper&&!(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)
                ||state.inverted&&family!=ScreenFamily.DIAGONAL)throw new IllegalArgumentException("Invalid Screen render pose");
        Arrays.fill(out,0,16,0);out[15]=1;
        boolean vertical=family==ScreenFamily.SCREEN||family==ScreenFamily.CONSOLE||family==ScreenFamily.DIAGONAL;
        int facing=state.facing;
        if(vertical&&facing==0){out[0]=1;out[6]=-1;out[9]=1;}
        else if(vertical&&facing==1){out[0]=-1;out[6]=-1;out[9]=-1;}
        else {
            // beginLocalTransform uses only EnumFacing.getHorizontalAngle(). Its saved UP/DOWN
            // cases have horizontal index -1 and therefore retain the source EAST pose.
            if(facing<2)facing=5;out[5]=1;
            if(facing==2){out[0]=1;out[10]=1;}
            else if(facing==3){out[0]=-1;out[10]=-1;}
            else if(facing==4){out[2]=-1;out[8]=1;}
            else {out[2]=1;out[8]=-1;}
        }
        out[12]=.5-(out[0]+out[4]+out[8])*.5;out[13]=.5-(out[1]+out[5]+out[9])*.5;out[14]=.5-(out[2]+out[6]+out[10])*.5;
        for(int column=0;column<3;column++)for(int row=0;row<3;row++)out[column*4+row]/=16;
        if(family==ScreenFamily.HALF_CONSOLE&&ceilingMounted){double shift=InputSurfaceLayout.ceilingStart(16,ceilingPosition<0?1:ceilingPosition);for(int row=0;row<3;row++)out[12+row]+=out[8+row]*shift;}
        if(family==ScreenFamily.HALF_DIAGONAL){for(int row=0;row<3;row++){out[12+row]+=out[8+row]*8;out[4+row]*=.5;out[8+row]*=.5;}}
    }
}
