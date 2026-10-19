package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;

/** Immutable texture-plane bounds relative to one block, sampled during chunk meshing. */
public final class HousingFaceLayouts {
    public static final HousingFaceLayouts NONE=new HousingFaceLayouts(new Plane[6]);
    private final Plane[] planes;
    public HousingFaceLayouts(Plane[] planes){this.planes=planes.clone();}
    public Plane face(EnumFacing face){return planes[face.getIndex()];}
    public static final class Plane {
        private final int[] min,size;
        public Plane(int[] min,int[] size){this.min=min.clone();this.size=size.clone();}
        public int min(int axis){return min[axis];}public int size(int axis){return size[axis];}
        @Override public String toString(){return java.util.Arrays.toString(min)+java.util.Arrays.toString(size);}
    }
}
