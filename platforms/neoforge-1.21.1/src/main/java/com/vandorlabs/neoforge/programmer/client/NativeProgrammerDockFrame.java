package com.vandorlabs.neoforge.programmer.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.programmer.NativeProgrammerLayout;
import net.minecraft.core.Direction;
import org.joml.*;
/** Released retained-token poses relative to the sole root; prepared once, independent of inventory. */
public final class NativeProgrammerDockFrame {
 private static final Matrix4f[][][] FRAMES=new Matrix4f[6][3][2];
 static{for(Direction face:Direction.Plane.HORIZONTAL){var layout=NativeProgrammerLayout.of(face);var a=layout.transform();for(int slot=0;slot<3;slot++)for(int chip=0;chip<2;chip++){double x=slot==0?(chip==1?14.54:16.3):slot==1?28:4,z=slot==0?10.05:chip==1?10.26:8.75;var poses=new PoseStack();poses.translate(layout.originX()+a.x(x/16,z/16),16.28/16,layout.originZ()+a.z(x/16,z/16));poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-face.toYRot()+180));poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));if(slot==0)poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));float scale=chip==1?.29F:.45F;poses.scale(scale,scale,.45F);FRAMES[face.get3DDataValue()][slot][chip]=new Matrix4f(poses.last().pose());}}}
 private NativeProgrammerDockFrame(){}
 private static Matrix4f frame(Direction face,int slot,boolean chip){NativeProgrammerLayout.of(face);if(slot<0||slot>2)throw new IllegalArgumentException("Programmer dock slot");return FRAMES[face.get3DDataValue()][slot][chip?1:0];}
 public static Matrix4fc matrix(Direction face,int slot,boolean chip){return frame(face,slot,chip);}
 public static void apply(Direction face,int slot,boolean chip,PoseStack target){target.mulPose(frame(face,slot,chip));}
}
