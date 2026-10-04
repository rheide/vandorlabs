package com.vandorlabs.client;

import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.render.CuboidMesh;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Frozen released ramp vertex submission for byte-exact comparisons. */
final class ReferenceRampEmitter {
    static void emitFace(BufferBuilder b,RampGeometry.Box a,EnumFacing face,TextureAtlasSprite sprite,
            double vBottom,double vTop,double uShift,double vShift,int tint) {
        float shade=face==EnumFacing.UP?1:face==EnumFacing.DOWN?.5F:face.getAxis()==EnumFacing.Axis.X?.6F:.8F;
        CuboidMesh.emitFace(a,CuboidMesh.Face.valueOf(face.getName().toUpperCase(java.util.Locale.ROOT)),vBottom,vTop,
                // Attribute order must match BlockSurfaceFormat: position, color, UV, lightmap, normal.
                (vx,vy,vz,u,v)->b.pos(vx,vy,vz)
                        .color(((tint>>16)&255)/255F*shade,((tint>>8)&255)/255F*shade,(tint&255)/255F*shade,1)
                        .tex(sprite.getInterpolatedU((u+uShift)*16),sprite.getInterpolatedV((v+vShift)*16))
                        .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                        .normal(face.getFrontOffsetX(),face.getFrontOffsetY(),face.getFrontOffsetZ()).endVertex());
    }
}
