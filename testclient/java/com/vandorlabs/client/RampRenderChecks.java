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
            BufferBuilder buffer=new BufferBuilder(4096);
            TEControlledRamp.FaceEmitter emitter=new TEControlledRamp.FaceEmitter(buffer);
            for(double thickness:new double[]{.001,.125,.5,1})for(EnumFacing face:EnumFacing.values())
                for(int tint:new int[]{0xFFFFFF,0x7BAF53}) {
                    RampGeometry.Box box=new RampGeometry.Box(.1,-.5,.2,.9,-.5+thickness,.95);
                    VertexFormat format=BlockSurfaceFormat.get();
                    buffer.begin(7,format);
                    emitter.emit(box,face,sprite,.17,.83,.03,.01,tint);
                    buffer.finishDrawing();require(buffer.getVertexCount()==4,"face disappeared");
                    ByteBuffer data=buffer.getByteBuffer();
                    BufferBuilder reference=new BufferBuilder(4096);reference.begin(7,format);
                    ReferenceRampEmitter.emitFace(reference,box,face,sprite,.17,.83,.03,.01,tint);
                    reference.finishDrawing();
                    ByteBuffer expectedBytes=reference.getByteBuffer();
                    for(int byteIndex=0;byteIndex<4*format.getNextOffset();byteIndex++)
                        require(data.get(byteIndex)==expectedBytes.get(byteIndex),"released vertex bytes changed");
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
                        require(Math.abs(data.getFloat(uv)-sprite.getInterpolatedU((u+.03)*16))<1E-6,"U vertex packing corrupt");
                        require(Math.abs(data.getFloat(uv+4)-sprite.getInterpolatedV((v+.01)*16))<1E-6,"V vertex packing corrupt");
                        int light=offset+format.getUvOffsetById(1);
                        require(data.getShort(light)==80 && data.getShort(light+2)==192,"lightmap channels corrupt");
                        int normal=offset+format.getNormalOffset();
                        require(data.get(normal)==127*face.getFrontOffsetX()
                                && data.get(normal+1)==127*face.getFrontOffsetY()
                                && data.get(normal+2)==127*face.getFrontOffsetZ(),"normal packing corrupt");
                    }
                    for(double shift:new double[]{-2.3,-1,0,.23,2.8})wrapped(emitter,box,face,sprite,format,tint,shift);
                }
        } finally { OpenGlHelper.lastBrightnessX=oldX;OpenGlHelper.lastBrightnessY=oldY; }
        System.out.println("PASS: production ramp emitter retains opaque colors, bounded repeating source UVs, preserved face area, normals and lightmaps for thin/moving slices");
    }
    private static void wrapped(TEControlledRamp.FaceEmitter unused,RampGeometry.Box box,EnumFacing face,TextureAtlasSprite sprite,VertexFormat format,int tint,double shift){
        BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,format);new TEControlledRamp.FaceEmitter(buffer).emit(box,face,sprite,.17,.83,shift,.11,tint);buffer.finishDrawing();require(buffer.getVertexCount()>0&&buffer.getVertexCount()%4==0,"wrapped face disappeared");
        ByteBuffer bytes=buffer.getByteBuffer();int stride=format.getNextOffset();double area=0;
        for(int vertex=0;vertex<buffer.getVertexCount();vertex++){
            int at=vertex*stride;double x=bytes.getFloat(at),y=bytes.getFloat(at+4),z=bytes.getFloat(at+8),rawU=(face.getAxis()==EnumFacing.Axis.X?z:x)+shift,rawV=(face.getAxis()==EnumFacing.Axis.Y?z:Math.abs(y-box.minY)<1e-6?.17:.83)+.11;
            // Clipping inserts vertices along side edges: their V is interpolated by Y.
            if(face.getAxis()!=EnumFacing.Axis.Y)rawV=.17+(y-box.minY)/(box.maxY-box.minY)*(.83-.17)+.11;
            int uv=at+format.getUvOffsetById(0);double u=(bytes.getFloat(uv)-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU()),v=(bytes.getFloat(uv+4)-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV());
            require(u>=-1e-4&&u<=1+1e-4&&v>=-1e-4&&v<=1+1e-4,"UV escaped source atlas sprite");require(periodic(u,rawU)&&periodic(v,rawV),"repeated UV changed texture phase");
            require(x>=box.minX-1e-6&&x<=box.maxX+1e-6&&y>=box.minY-1e-6&&y<=box.maxY+1e-6&&z>=box.minZ-1e-6&&z<=box.maxZ+1e-6,"repeat moved geometry");
            int normal=at+format.getNormalOffset();require(bytes.get(normal)==127*face.getFrontOffsetX()&&bytes.get(normal+1)==127*face.getFrontOffsetY()&&bytes.get(normal+2)==127*face.getFrontOffsetZ(),"wrapped face normal");
        }
        for(int vertex=0;vertex<buffer.getVertexCount();vertex+=4){area+=triangle(bytes,stride,vertex,vertex+1,vertex+2)+triangle(bytes,stride,vertex,vertex+2,vertex+3);}
        double expected=face.getAxis()==EnumFacing.Axis.Y?(box.maxX-box.minX)*(box.maxZ-box.minZ):(box.maxY-box.minY)*(face.getAxis()==EnumFacing.Axis.X?box.maxZ-box.minZ:box.maxX-box.minX);require(Math.abs(area-expected)<1e-5,"texture repeats changed emitted face area");
    }
    private static boolean periodic(double value,double raw){double difference=Math.abs(value-(raw-Math.floor(raw)));return difference<1e-4||Math.abs(difference-1)<1e-4;}
    private static double triangle(ByteBuffer bytes,int stride,int a,int b,int c){double ax=bytes.getFloat(a*stride),ay=bytes.getFloat(a*stride+4),az=bytes.getFloat(a*stride+8),x=bytes.getFloat(b*stride)-ax,y=bytes.getFloat(b*stride+4)-ay,z=bytes.getFloat(b*stride+8)-az,u=bytes.getFloat(c*stride)-ax,v=bytes.getFloat(c*stride+4)-ay,w=bytes.getFloat(c*stride+8)-az;return Math.sqrt((y*w-z*v)*(y*w-z*v)+(z*u-x*w)*(z*u-x*w)+(x*v-y*u)*(x*v-y*u))*.5;}
    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalStateException("Ramp renderer: "+message);
    }
}
