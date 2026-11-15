package com.vandorlabs.gear.core;
public final class LandingWheelLayoutTest {
    public static void main(String[] args){int[][] right={{-1,0},{0,-1},{1,0},{0,1}};int checks=0;for(int facing=0;facing<4;facing++)for(int part=0;part<4;part++){int x=LandingWheelLayout.x(facing,part),y=LandingWheelLayout.y(part),z=LandingWheelLayout.z(facing,part);if(x!=(part%2)*right[facing][0]||z!=(part%2)*right[facing][1]||y!=part/2)throw new AssertionError("Released metadata footprint");for(int rootX:new int[]{-30000000,-1,0,15,16,30000000})if((rootX+x)-x!=rootX)throw new AssertionError("Root round trip");checks++;}for(int[] invalid:new int[][]{{-1,0},{4,0},{0,-1},{0,4}}){boolean refused=false;try{LandingWheelLayout.x(invalid[0],invalid[1]);}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new AssertionError("Invalid cell accepted");checks++;}System.out.println("Landing wheel layout PASS checks="+checks);}
    private LandingWheelLayoutTest(){}
}
