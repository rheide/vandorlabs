package com.vandorlabs.render;

/** Conservative chunk containment for every possible diagonal join, including future neighbours. */
public final class DiagonalChunkPolicy {
    private DiagonalChunkPolicy() { }
    /** Facing uses the stable Minecraft DOWN/UP/NORTH/SOUTH/WEST/EAST direction indices. */
    public static boolean contained(int worldX,int worldY,int worldZ,int facing,boolean inverted,boolean halfHeight,boolean fullWidth) {
        int x=worldX&15,y=worldY&15,z=worldZ&15;
        if(halfHeight)return inverted?y<15:y>0;
        if(fullWidth)return x>0&&x<15&&z>0&&z<15;
        switch(facing){
            case 2:return x>0&&x<15&&z>0;
            case 3:return x>0&&x<15&&z<15;
            case 4:return z>0&&z<15&&x>0;
            case 5:return z>0&&z<15&&x<15;
            default:return false;
        }
    }
}
