package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.*;
import net.minecraft.core.Direction;

/** One immutable owning-thread publication; no tile, level, position or raw saved tag reaches render workers. */
public record NativeFlatGlassVisual(NativeGlassSettings settings,FlatGlassPolicy.Pose pose,NativePanelLayout layout,int authoredMask,int frameMask,Contact contact,Direction contactFacing,double contactNear,boolean inert) {
 public record Contact(Double frontArm,Double backArm,Boolean frontRight,Boolean backRight,boolean straightLeft,boolean straightRight){public PanelCornerGeometry.FlatCorner corner(){return new PanelCornerGeometry.FlatCorner(frontArm,backArm,frontRight,backRight,straightLeft,straightRight);}}
 public NativeFlatGlassVisual {java.util.Objects.requireNonNull(settings);java.util.Objects.requireNonNull(pose);java.util.Objects.requireNonNull(layout);java.util.Objects.requireNonNull(contactFacing);if((authoredMask&~255)!=0||(frameMask&~255)!=0||!contactFacing.getAxis().isHorizontal()||!Double.isFinite(contactNear))throw new IllegalArgumentException("Ordinary glass publication");}
 public static NativeFlatGlassVisual single(NativeGlassSettings settings,FlatGlassPolicy.Pose pose){return new NativeFlatGlassVisual(settings,pose,NativePanelLayout.UNIT,15,15,null,pose.rotated?Direction.EAST:Direction.NORTH,PanelDepthPolicy.start(pose.depth),false);}
 public Direction facing(){return pose.rotated?Direction.EAST:Direction.NORTH;}
 /** Source bent surfaces map in actual contact orientation, independently of canonical metadata orientation. */
 public NativePanelLayout materialLayout(){return contact!=null&&settings.glass().material>=0&&contactFacing!=facing()?new NativePanelLayout(layout.columns()-1-layout.column(),layout.row(),layout.columns(),layout.rows(),false,false):layout;}
 public boolean selectedFrame(){var f=settings.frames();return f.front>=0||f.interior>=0||f.exteriorChoice()>=0||f.tile;}
 public boolean authoredFrame(){return settings.glass().framed&&!selectedFrame()&&contact==null;}
}
