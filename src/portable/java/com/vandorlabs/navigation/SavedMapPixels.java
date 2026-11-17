package com.vandorlabs.navigation;

import java.util.Arrays;

/** Immutable original map pixels. Coverage is its exact square, including original blank pixels. */
public final class SavedMapPixels {
    public static final int VERSION=1, WIDTH=128, PIXELS=WIDTH*WIDTH, PALETTE_SIZE=256;
    private final String dimension;
    private final int centerX,centerZ,scale,minX,minZ,span,hash;
    private final byte[] colors;
    private final int[] palette;
    public SavedMapPixels(String dimension,int centerX,int centerZ,int scale,byte[] colors,int[] palette){
        if(dimension==null||dimension.isEmpty()||dimension.length()>256||!dimension.equals(dimension.trim()))throw new IllegalArgumentException("Map dimension");
        if(Math.abs((long)centerX)>NavigationMap.LIMIT||Math.abs((long)centerZ)>NavigationMap.LIMIT||scale<0||scale>4)throw new IllegalArgumentException("Map coordinates or scale");
        if(colors==null||colors.length!=PIXELS||palette==null||palette.length!=PALETTE_SIZE)throw new IllegalArgumentException("Map pixel or palette length");
        this.dimension=dimension;this.centerX=centerX;this.centerZ=centerZ;this.scale=scale;this.colors=colors.clone();this.palette=palette.clone();span=WIDTH<<scale;minX=centerX-span/2;minZ=centerZ-span/2;
        int h=31*(31*centerX+centerZ)+scale;h=31*h+Arrays.hashCode(this.colors);hash=31*h+Arrays.hashCode(this.palette);
    }
    public String dimension(){return dimension;}public int centerX(){return centerX;}public int centerZ(){return centerZ;}public int scale(){return scale;}
    public int minX(){return minX;}public int minZ(){return minZ;}public int maxXExclusive(){return minX+span;}public int maxZExclusive(){return minZ+span;}public int span(){return span;}
    public boolean contains(int x,int z){return x>=minX&&x<maxXExclusive()&&z>=minZ&&z<maxZExclusive();}
    public int colorAt(int x,int z){if(!contains(x,z))throw new IllegalArgumentException("Outside saved map");return palette[colors[((z-minZ)>>scale)*WIDTH+((x-minX)>>scale)]&255];}
    public byte[] colors(){return colors.clone();}public int[] palette(){return palette.clone();}
    /** Stable persisted content hash; dimension is supplied by the chip's existing location payload. */
    public int hash(){return hash;}
    public int retainedBytes(){return 128+dimension.length()*2+colors.length+palette.length*4;}
    @Override public int hashCode(){return 31*hash+dimension.hashCode();}
    @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof SavedMapPixels))return false;SavedMapPixels p=(SavedMapPixels)other;return hash==p.hash&&centerX==p.centerX&&centerZ==p.centerZ&&scale==p.scale&&dimension.equals(p.dimension)&&Arrays.equals(colors,p.colors)&&Arrays.equals(palette,p.palette);}
}
