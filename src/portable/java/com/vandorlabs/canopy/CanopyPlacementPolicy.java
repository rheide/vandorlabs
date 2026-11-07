package com.vandorlabs.canopy;

import com.vandorlabs.persistence.CanopySettingsData;

/** World-free placement after the caller resolves the original clicked cell and its admission. */
public final class CanopyPlacementPolicy {
    private static final int[] X={0,0,0,0,-1,1},Y={-1,1,0,0,0,0},Z={0,0,-1,1,0,0};
    public static final class Plan {
        public final int facing,mount,x,y,z,occupiedWidth;
        Plan(int facing,int mount,int x,int y,int z,int occupiedWidth) {
            this.facing=facing;this.mount=mount;this.x=x;this.y=y;this.z=z;this.occupiedWidth=occupiedWidth;
        }
    }
    public static final class Cell {
        public final int x,y,z;
        Cell(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
    }
    private CanopyPlacementPolicy(){}
    private static boolean opening(String kind){return "canopy".equals(kind)||"long_canopy".equals(kind);}
    private static boolean visor(String kind){return "space_visor_2x1".equals(kind)||"space_visor_3x1".equals(kind)||"space_visor_5x1".equals(kind)||"space_visor_5x1_double_height".equals(kind);}
    private static int ccw(int facing){switch(facing){case 2:return 4;case 3:return 5;case 4:return 3;case 5:return 2;default:throw new IllegalArgumentException("Canopy horizontal facing");}}
    /** Face numbers retain the Source DOWN, UP, NORTH, SOUTH, WEST, EAST ordering. */
    public static Plan initial(String kind,int width,int length,int height,int playerFacing,int side,boolean replaceable) {
        java.util.Objects.requireNonNull(kind);
        if(playerFacing<2||playerFacing>5||side<0||side>5||width<1||length<1||height<1)throw new IllegalArgumentException("Canopy placement");
        boolean large=CanopySettingsData.supports(kind),moving=opening(kind),angled=kind.startsWith("angled_");
        int facing=moving||angled?playerFacing^1:playerFacing;
        if(side>=2&&(large||moving||angled))facing=large?side^1:side;
        int mount=large?(side==1?2:side==0?3:0):side==0?1:0;
        return anchored(kind,width,length,height,facing,mount,side,replaceable);
    }
    /** The released item accepts surface hits outside its originally addressed root/helper cell. */
    public static Cell existingCell(int x,int y,int z,int side,float hitX,float hitY,float hitZ){
        if(side<0||side>5||!Float.isFinite(hitX)||!Float.isFinite(hitY)||!Float.isFinite(hitZ))throw new IllegalArgumentException("Canopy surface hit");
        return new Cell(floor((x+(double)hitX)-X[side]*.0001),floor((y+(double)hitY)-Y[side]*.0001),floor((z+(double)hitZ)-Z[side]*.0001));
    }
    private static int floor(double value){int integer=(int)value;return value<integer?integer-1:integer;}
    /** Resolve the owner before calling; returned offsets are relative to existingCell(), not the old clicked cell. */
    public static Plan existing(String kind,int width,int length,int height,int ownerFacing,int ownerMount,int side,boolean replaceable){
        java.util.Objects.requireNonNull(kind);if(ownerFacing<2||ownerFacing>5||ownerMount<0||ownerMount>3||side<0||side>5||width<1||length<1||height<1)throw new IllegalArgumentException("Existing canopy placement");
        boolean large=CanopySettingsData.supports(kind);int mount=large?(side==1?2:side==0?3:0):ownerMount<2?ownerMount:side==0?1:0;
        return anchored(kind,width,length,height,ownerFacing,mount,side,replaceable);
    }
    private static Plan anchored(String kind,int width,int length,int height,int facing,int mount,int side,boolean replaceable){
        boolean large=CanopySettingsData.supports(kind),moving=opening(kind),angled=kind.startsWith("angled_");
        int x=replaceable?0:X[side],y=replaceable?0:Y[side],z=replaceable?0:Z[side];
        int cross=ccw(facing),half=width/2;
        if(large&&mount>=2){x+=X[cross]*half+X[facing]*half;z+=Z[cross]*half+Z[facing]*half;}
        else if(large||visor(kind)){x+=X[cross]*half;z+=Z[cross]*half;if(large)y-=height/2;}
        if(!large&&(angled||moving)&&side>=2&&side==facing){x+=X[facing]*(length-1);z+=Z[facing]*(length-1);}
        return new Plan(facing,mount,x,y,z,"space_visor_2x1".equals(kind)?3:width);
    }
}
