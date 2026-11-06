package com.vandorlabs.render;

public final class SpaceDoorBoundsTest {
    private static int calls;
    private static final SpaceDoorBounds.Factory<double[]> BOXES=(x,y,z,r,t,b)->{calls++;return new double[]{x,y,z,r,t,b};};
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        double[] north=SpaceDoorBounds.closed(BOXES,2,false),east=SpaceDoorBounds.closed(BOXES,5,true);need(north[2]==0&&north[5]==.35,"released near-face rotating box");need(east[0]==.30&&east[3]==.65,"released asymmetric sliding centre box");
        calls=0;double[] open=SpaceDoorBounds.rotatingOpen(BOXES,3,false,true,false);need(calls==1,"one result factory call without point wrappers");need(open[0]>.125&&open[0]<.125001,"float pivot rounding remains visible");need(open[2]<0,"rotating open leaf may overhang its cell");
        calls=0;double[] positioned=SpaceDoorBounds.positioned(BOXES,open[0],open[1],open[2],open[3],open[4],open[5],3,0,true);need(calls==1&&positioned[2]<0,"open rotation retains overhang and one output box");
        double[] clamped=SpaceDoorBounds.positioned(BOXES,north[0],north[1],north[2],north[3],north[4],north[5],2,.1,false);need(clamped[2]==0&&clamped[5]==.35-.1,"unrotated depth box keeps exact clamp");
        double[] pad=SpaceDoorControlPanel.bounds(BOXES,3,SpaceDoorControlPanel.Side.RIGHT,.375,true,false),mirror=SpaceDoorControlPanel.mirror(BOXES,pad[0],pad[1],pad[2],pad[3],pad[4],pad[5],3);need(pad[1]==16.5/16&&pad[4]==19.5/16,"fixed pad lies across upper half boundary");need(mirror[2]==1-pad[5]&&mirror[5]==1-pad[2],"rear pad mirrors normal axis only");
        double z=(SpaceDoorControlPanel.z0(true,false)+1.5)/16;need(SpaceDoorControlPanel.containsConfigured(true,false,1,0,0,z,18D/16,1),"ordinary pad midpoint");need(!SpaceDoorControlPanel.containsConfigured(true,false,1,0,0,1-z,18D/16,1),"single pad excludes rear midpoint");need(SpaceDoorControlPanel.containsConfigured(true,false,2,0,0,1-z,18D/16,1),"dual pad includes mirrored midpoint");need(SpaceDoorControlPanel.containsConfigured(true,false,1,1,0,z,15.8/16,1),"security pad includes expanded lower boundary");need(!SpaceDoorControlPanel.containsConfigured(true,false,1,1,0,z,(15.8-1e-8)/16,1),"security lower boundary stays strict");need(SpaceDoorControlPanel.containsConfigured(true,false,1,0,0,z,18D/16,1.5),"large pad keeps regular world-space centre height");
        System.out.println("PASS: ordinary door pivots, depth clamps, fixed/mirrored pads and security hit regions");
    }
}
