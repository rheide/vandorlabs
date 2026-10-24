package com.vandorlabs.blocks;

import net.minecraft.util.EnumFacing;

/** Immutable texture-plane bounds relative to one block, sampled during chunk meshing. */
public final class HousingFaceLayouts {
    public static final HousingFaceLayouts NONE=new HousingFaceLayouts(new Plane[6]);
    private final Plane[] planes;
    public HousingFaceLayouts(Plane[] planes){this.planes=planes.clone();}
    public Plane face(EnumFacing face){return planes[face.getIndex()];}
    public static final class Plane extends com.vandorlabs.render.HousingFacePlane {
        public Plane(int[] min,int[] size){super(min,size);}
    }
}
