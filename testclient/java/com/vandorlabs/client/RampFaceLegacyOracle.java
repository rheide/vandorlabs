package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.ramp.RampGeometry;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Actual released FaceEmitter packed records, independent of portable UV emission. */
public final class RampFaceLegacyOracle {
    private static void integer(MessageDigest digest,int value){digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));digest.update((byte)(value>>>8));digest.update((byte)value);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int cases=0;long vertices=0;
        double[][] boxes={{0,0,0,1,1,1},{.1,-.5,.2,.9,-.499,.95},{0,.25,0,1,.75,1},{-.75,-1.5,-.25,1.5,1.25,1.75}};
        double[][] mapping={{1,0,0,0},{.17,.83,.03,.01},{-.25,1.25,-1.5,2.25},{1,0,1,1}};
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(double aspect:new double[]{.5,1,4})for(double[] a:boxes)for(EnumFacing face:EnumFacing.values())for(int mode=-1;mode<=2;mode++)for(double[] uv:mapping)for(int tint:new int[]{0xffffff,0x7baf53}){
                TextureAtlasSprite sprite=new TextureAtlasSprite("ramp-face-reference"){@Override public float getInterpolatedU(double u){return (float)u/16F;}@Override public float getInterpolatedV(double v){return (float)v/16F;}};sprite.setIconWidth((int)(16*aspect));sprite.setIconHeight(16);
                RampGeometry.Box box=new RampGeometry.Box(a[0],a[1],a[2],a[3],a[4],a[5]);BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,BlockSurfaceFormat.get());OpenGlHelper.lastBrightnessX=80;OpenGlHelper.lastBrightnessY=192;
                new TEControlledRamp.FaceEmitter(buffer).emit(box,face,sprite,uv[0],uv[1],uv[2],uv[3],tint,mode);buffer.finishDrawing();int count=buffer.getVertexCount();ByteBuffer packed=buffer.getByteBuffer();int stride=BlockSurfaceFormat.get().getNextOffset();if(stride!=32)throw new AssertionError("Source face format="+stride);MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int vertex=0;vertex<count;vertex++)for(int word=0;word<8;word++)integer(digest,packed.getInt(vertex*stride+word*4));
                StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));JsonObject row=new JsonObject();row.addProperty("aspect",aspect);row.add("box",gson.toJsonTree(a));row.addProperty("face",face.getIndex());row.addProperty("mode",mode);row.add("uv",gson.toJsonTree(uv));row.addProperty("tint",tint);row.addProperty("vertices",count);row.addProperty("sha256",hash.toString());out.println(gson.toJson(row));cases++;vertices+=count;
            }
        }
        SegmentedReferenceGolden.check(file,"78d400681f4bdf72ba73f3a49a3f26ab6e83937e2c364e96d23bb3d2add1cdec");System.out.println("Actual Ramp face records PASS cases="+cases+" vertices="+vertices+" packedPositionColorUVLightNormal/negativeNativeRepeat/tile/aspectFit");
    }
}
