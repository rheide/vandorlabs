package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Point;

public final class StairTextureMappingTest {
    private static void close(double a,double b){if(Math.abs(a-b)>1e-8)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        Point[] points={new Point(0,0,0),new Point(1,0,0),new Point(1,.5,0),new Point(0,.5,0)};
        float[] u={0,16,16,0},v={16,16,8,8};
        double[][] stretch=StairTextureMapping.map(points,u,v,0,1,2);close(stretch[0][0],0);close(stretch[1][0],16);close(stretch[0][1],16);close(stretch[2][1],0);
        double[][] tile=StairTextureMapping.map(points,u,v,1,1,2);close(tile[0][1],16);close(tile[2][1],8);
        double[][] fit=StairTextureMapping.map(points,u,v,2,2,2);close(fit[0][1],16);close(fit[2][1],0);
        double[][] wide=StairTextureMapping.map(points,u,v,1,2,2);close(wide[1][0],8);close(wide[2][1],8);
        int cases=0;for(int facing:new int[]{2,3,4,5}){int opposite=facing==2?3:facing==3?2:facing==4?5:4;if(StairTextureMapping.localFace(opposite,facing)!=2)throw new AssertionError("front must be low riser");for(int face=0;face<6;face++){int local=StairTextureMapping.localFace(face,facing);if(local<0||local>5)throw new AssertionError();if(face<2&&local!=face)throw new AssertionError("top/bottom stable");cases++;}}
        System.out.println("StairTextureMapping PASS authored half-riser Stretch/Tile/Fit/aspect and "+cases+" front-relative material faces");
    }
}
