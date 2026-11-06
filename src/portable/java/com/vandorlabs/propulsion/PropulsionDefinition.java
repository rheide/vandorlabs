package com.vandorlabs.propulsion;

import java.util.*;

/** Fixed released placed-engine shapes. Unknown registry paths are never silently mapped to a cube. */
public final class PropulsionDefinition {
    private static final Map<String,PropulsionDefinition> DEFINITIONS;
    public final String id,family;public final int shape,corner,maxConnected,defaultFacing;public final float depth;
    private PropulsionDefinition(String id,String family,int shape,int corner,int maxConnected,int defaultFacing,float depth){this.id=id;this.family=family;this.shape=shape;this.corner=corner;this.maxConnected=maxConnected;this.defaultFacing=defaultFacing;this.depth=depth;}
    static {Map<String,PropulsionDefinition> all=new LinkedHashMap<>();for(String family:new String[]{"rocket_thruster","ion_drive","plasma_vent","impulse_engine"}){add(all,new PropulsionDefinition(family,family,0,0,8,2,1));add(all,new PropulsionDefinition(family+"_hexagonal",family,1,0,3,2,1));for(int corner=0;corner<4;corner++)add(all,new PropulsionDefinition(wedgeId(family,corner),family,2,corner,0,2,1));}add(all,new PropulsionDefinition("antigravity_plate","",0,0,4,1,.25F));add(all,new PropulsionDefinition("repulsor_array","",0,0,4,1,.5F));add(all,new PropulsionDefinition("vertical_hover_thruster","",0,0,4,1,1));DEFINITIONS=Collections.unmodifiableMap(all);}
    private static void add(Map<String,PropulsionDefinition> all,PropulsionDefinition value){all.put(value.id,value);}
    public static PropulsionDefinition of(String id){PropulsionDefinition result=DEFINITIONS.get(id);if(result==null)throw new IllegalArgumentException("Unknown propulsion block "+id);return result;}
    public static Collection<PropulsionDefinition> values(){return DEFINITIONS.values();}
    public static String wedgeId(String family,int corner){if(corner<0||corner>3)throw new IllegalArgumentException("Engine wedge corner");return family+"_wedge"+(corner==1?"_bottom_right":corner==2?"_top_right":corner==3?"_top_left":"");}
}
