package com.vandorlabs.neoforge.shape;

import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.Direction;

/** A T's two donor planes both use this cell's appearance; neighbours contribute geometry only. */
public record NativeFlatBarOwner(Direction facing,double panelNear,double barNear,
                                 boolean leftTail,double stemLeft,double stemWidth) {
    public static NativeFlatBarOwner from(NativeFlatWallGeometry.Geometry geometry){
        var join=geometry.join();
        if(join==null||join.frontArm()==null||join.backArm()==null
                ||!(Math.abs(join.frontArm()-join.backArm())<1e-7)
                ||join.straightLeft()==join.straightRight())return null;
        var corner=join.corner();
        return new NativeFlatBarOwner(geometry.facing(),PanelDepthPolicy.start(geometry.depth()),join.frontArm(),
                join.straightLeft(),corner.left(),(corner.right()-corner.left())/16);
    }
    public double stemCut(){return leftTail?barNear:barNear+4;}
}
