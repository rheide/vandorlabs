package com.vandorlabs.gear.core;

/** Maps a clicked posed normal back to one adjacent cell in the shared rest-plane mesh. */
public final class GearFootPlacement {
    public static GearTopology.Cell neighbor(GearMotion.Pose pose,double x,double y,double z){
        GearMotion.Vec origin=pose.transformFootVertex(0,0,0);
        GearMotion.Vec ex=pose.transformFootVertex(1,0,0),ey=pose.transformFootVertex(0,1,0),ez=pose.transformFootVertex(0,0,1);
        // The foot transform is rigid, including the counter-rotating level mode.
        // Its inverse direction matrix is its transpose; translations cancel.
        double a=x*(ex.x-origin.x)+y*(ex.y-origin.y)+z*(ex.z-origin.z);
        double b=x*(ey.x-origin.x)+y*(ey.y-origin.y)+z*(ey.z-origin.z);
        double c=x*(ez.x-origin.x)+y*(ez.y-origin.y)+z*(ez.z-origin.z);
        int ix=(int)Math.rint(a),iz=(int)Math.rint(c);
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||Math.abs(a-ix)>1e-7||Math.abs(b)>1e-7||Math.abs(c-iz)>1e-7||Math.abs(ix)+Math.abs(iz)!=1)
            throw new IllegalArgumentException("Extend the landing gear sideways in its current foot plane");
        return new GearTopology.Cell(ix,0,iz);
    }
    private GearFootPlacement(){}
}
