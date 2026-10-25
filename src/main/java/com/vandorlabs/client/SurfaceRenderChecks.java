package com.vandorlabs.client;

import com.vandorlabs.turntable.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import java.nio.ByteBuffer;

/** Actual emitted geometry retains atlas bounds and surface area under square/disc layouts. */
final class SurfaceRenderChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Surface mesh: "+why);}
    static void run(Minecraft mc){int cases=0;
        java.util.List<TextureAtlasSprite> sprites=new java.util.ArrayList<>();for(String id:TurntableTextures.IDS)sprites.add(mc.getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(id,true)));
        TextureAtlasSprite wide=new TextureAtlasSprite("surface-test-wide"){};wide.setIconWidth(64);wide.setIconHeight(32);wide.initSprite(128,128,16,32,false);sprites.add(wide);
        for(TextureAtlasSprite sprite:sprites)for(int size:new int[]{1,4})for(int mode=0;mode<3;mode++)for(boolean disc:new boolean[]{false,true})for(TurntableGeometry.Face face:TurntableGeometry.Face.values()){
            BufferBuilder buffer=new BufferBuilder(65536);VertexFormat format=BlockSurfaceFormat.get();buffer.begin(7,format);SurfaceTextureMesh.get(sprite,size,mode,disc).draw(buffer,face,sprite,.002,15728880);buffer.finishDrawing();ByteBuffer data=buffer.getByteBuffer();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);double area=0;
            require(buffer.getVertexCount()>0&&buffer.getVertexCount()%4==0,"degenerate-quad shader submission");
            for(int vertex=0;vertex<buffer.getVertexCount();vertex++){
                int color=vertex*stride+format.getColorOffset();for(int component=0;component<4;component++)require((data.get(color+component)&255)==255,"emitted white RGBA must stay opaque through TESR alpha test");
                float u=data.getFloat(vertex*stride+uv),v=data.getFloat(vertex*stride+uv+4);require(u>=sprite.getMinU()-1e-6&&u<=sprite.getMaxU()+1e-6&&v>=sprite.getMinV()-1e-6&&v<=sprite.getMaxV()+1e-6,"Tile never samples neighbouring atlas sprites");
            }
            for(int q=0;q<buffer.getVertexCount();q+=4){double[] a=new double[3],b=new double[3];for(int axis=0;axis<3;axis++){a[axis]=data.getFloat((q+1)*stride+axis*4)-data.getFloat(q*stride+axis*4);b[axis]=data.getFloat((q+2)*stride+axis*4)-data.getFloat(q*stride+axis*4);}double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];area+=Math.sqrt(x*x+y*y+z*z)/2;}
            double full=disc?64*Math.pow(size*.43,2)*Math.sin(Math.PI/64):size*size;
            if(mode!=2||Math.abs(UnifiedTextureSprites.aspect(sprite)-1)<1e-6)require(Math.abs(full-area)<1e-4*Math.max(1,full),"square/disc area preserved while tiling");
            else require(area>0&&area<full,"rectangular Fit leaves opaque margins without stretching");cases++;
        }
        System.out.println("[vandorlabs] programmable-surface-mesh PASS cases="+cases+"14 native finishes plus wide artwork six normals single/multi-cell Stretch/Tile/Fit square/disc area bounded atlas UVs and actual opaque-white emittedRGBA");
    }
    private SurfaceRenderChecks(){}
}
