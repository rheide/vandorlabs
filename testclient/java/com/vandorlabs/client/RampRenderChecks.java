package com.vandorlabs.client;

import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import java.nio.ByteBuffer;

/** Exercises the production ramp emitter, including attribute packing and thin moving slices. */
final class RampRenderChecks {
    static void run() {
        TextureAtlasSprite sprite=new TextureAtlasSprite("ramp-check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
        try {
            OpenGlHelper.lastBrightnessX=80;OpenGlHelper.lastBrightnessY=192;
            for(double thickness:new double[]{.001,.125,.5,1})for(EnumFacing face:EnumFacing.values())
                for(int tint:new int[]{0xFFFFFF,0x7BAF53}) {
                    RampGeometry.Box box=new RampGeometry.Box(.1,-.5,.2,.9,-.5+thickness,.95);
                    BufferBuilder buffer=new BufferBuilder(4096);VertexFormat format=BlockSurfaceFormat.get();
                    buffer.begin(7,format);
                    TEControlledRamp.emitFace(buffer,box,face,sprite,.17,.83,.23,.11,tint);
                    buffer.finishDrawing();require(buffer.getVertexCount()==4,"face disappeared");
                    ByteBuffer data=buffer.getByteBuffer();
                    float shade=face==EnumFacing.UP?1:face==EnumFacing.DOWN?.5F:face.getAxis()==EnumFacing.Axis.X?.6F:.8F;
                    for(int i=0;i<4;i++) {
                        int offset=i*format.getNextOffset(),color=offset+format.getColorOffset();
                        for(int channel=0;channel<3;channel++) {
                            int expected=(int)(((tint>>(16-channel*8))&255)*shade);
                            require(Math.abs((data.get(color+channel)&255)-expected)<=1,"RGB vertex packing corrupt");
                        }
                        require((data.get(color+3)&255)==255,"ramp face became transparent");
                        double x=data.getFloat(offset),y=data.getFloat(offset+4),z=data.getFloat(offset+8);
                        double u=face.getAxis()==EnumFacing.Axis.X?z:x;
                        double v=face.getAxis()==EnumFacing.Axis.Y?z:Math.abs(y-box.minY)<1E-6?.17:.83;
                        int uv=offset+format.getUvOffsetById(0);
                        require(Math.abs(data.getFloat(uv)-sprite.getInterpolatedU((u+.23)*16))<1E-6,"U vertex packing corrupt");
                        require(Math.abs(data.getFloat(uv+4)-sprite.getInterpolatedV((v+.11)*16))<1E-6,"V vertex packing corrupt");
                        int light=offset+format.getUvOffsetById(1);
                        require(data.getShort(light)==80 && data.getShort(light+2)==192,"lightmap channels corrupt");
                        int normal=offset+format.getNormalOffset();
                        require(data.get(normal)==127*face.getFrontOffsetX()
                                && data.get(normal+1)==127*face.getFrontOffsetY()
                                && data.get(normal+2)==127*face.getFrontOffsetZ(),"normal packing corrupt");
                    }
                }
        } finally { OpenGlHelper.lastBrightnessX=oldX;OpenGlHelper.lastBrightnessY=oldY; }
        System.out.println("PASS: production ramp emitter retains opaque colors, source UVs, normals and lightmaps for thin/moving slices");
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalStateException("Ramp renderer: "+message);
    }
}
