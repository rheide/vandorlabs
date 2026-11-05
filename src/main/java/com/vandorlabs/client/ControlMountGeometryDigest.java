package com.vandorlabs.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.state.IBlockState;

/** Original actual emitted position/GUI-transform bits, independent of atlas layout and vertex stride. */
final class ControlMountGeometryDigest {
    private final MessageDigest digest;
    ControlMountGeometryDigest(){try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private void value(int bits){digest.update((byte)(bits>>>24));digest.update((byte)(bits>>>16));digest.update((byte)(bits>>>8));digest.update((byte)bits);}
    void quad(BakedQuad quad){int[] data=quad.getVertexData();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++)value(data[vertex*stride+axis]);}
    void item(IBakedModel model){for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,side,0))quad(quad);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;for(float v:new float[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})value(Float.floatToRawIntBits(v));}
    void finish(String label,String expected){StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));if(!expected.equals(hash.toString()))throw new IllegalStateException(label+" original emitted geometry bits changed: "+hash);System.out.println("[vandorlabs][reprolab] "+label+"-bits PASS sha256="+hash);}
}
