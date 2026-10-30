package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.nio.ByteBuffer;

/** Inspect submitted UVs, outward normals, winding, and shader lightmap data. */
final class DiagonalSurfaceChecks {
    static void run() {
        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
        try {
            OpenGlHelper.lastBrightnessX = 80; OpenGlHelper.lastBrightnessY = 192;
            TextureAtlasSprite wall = new TextureAtlasSprite("test_wall") { };
            TextureAtlasSprite trim = new TextureAtlasSprite("test_trim") { };
            wall.setIconWidth(16); wall.setIconHeight(16); wall.initSprite(256,256,32,48,false);
            trim.setIconWidth(16); trim.setIconHeight(16); trim.initSprite(256,256,128,160,false);
            java.lang.reflect.Method draw = TEAnimatedScreenSelector.class.getDeclaredMethod("renderDiagonalWall",
                    BufferBuilder.class, TextureAtlasSprite.class, TextureAtlasSprite.class, boolean.class,
                    BlockProgrammableWall.Corner.class, double.class, int.class, double.class, double.class, double.class);
            draw.setAccessible(true);
            int measuredSlopeEdges=0;
            for (boolean shallow : new boolean[]{false,true}) for (boolean inverted : new boolean[]{false,true})
                for (double span : new double[]{8,16}) for (int fill = 0; fill <= 3; fill++) {
                    if (shallow && span != 8) continue;
                    BufferBuilder buf = new BufferBuilder(4096);
                    net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();
                    buf.begin(7, format);
                    double low = shallow && inverted ? -8 : 0;
                    draw.invoke(null,buf,wall,trim,inverted,null,span,fill,low,low+16,
                            shallow ? (inverted ? 8D : 0D) : Double.NaN);
                    buf.finishDrawing();
                    ByteBuffer data = buf.getByteBuffer();
                    require(buf.getVertexCount() > 0, "empty diagonal panel");
                    int stride = format.getNextOffset();
                    int uv = format.getUvOffsetById(0);
                    int lm = format.getUvOffsetById(1);
                    int normal = format.getNormalOffset();
                    for (int i=0;i<buf.getVertexCount();i++) {
                        float u=data.getFloat(i*stride+uv),v=data.getFloat(i*stride+uv+4);
                        if (fill != 0)
                            require(u>=wall.getMinU()-1e-6 && u<=wall.getMaxU()+1e-6
                                    && v>=wall.getMinV()-1e-6 && v<=wall.getMaxV()+1e-6,
                                    "filled face escaped main texture");
                        require(data.getShort(i*stride+lm)==80 && data.getShort(i*stride+lm+2)==192,
                                "missing vertex lightmap");
                        double nx=data.get(i*stride+normal)/127D,ny=data.get(i*stride+normal+1)/127D,
                                nz=data.get(i*stride+normal+2)/127D;
                        require(Math.abs(nx*nx+ny*ny+nz*nz-1)<.03,"invalid face normal");
                        if (i%4==0) {
                            double[] a=position(data,i*stride),b=position(data,(i+1)*stride),c=position(data,(i+2)*stride);
                            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
                            double vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
                            require((uy*vz-uz*vy)*nx+(uz*vx-ux*vz)*ny+(ux*vy-uy*vx)*nz>0,
                                    "shader-derived normal opposes supplied normal");
                            if (fill == 0) {
                                // Measure the submitted broad-panel edges, rather than
                                // inferring stretch from a block bounding box. Cardinal
                                // rotations are isometries; inversion and the half-height
                                // axis swap are exercised by the enclosing matrix.
                                double[][] points={a,b,c,position(data,(i+3)*stride)};
                                for(int edge=0;edge<4;edge++) {
                                    int next=(edge+1)%4;
                                    double[] from=points[edge],to=points[next];
                                    double dy=to[1]-from[1],dz=to[2]-from[2];
                                    if(Math.abs(nx)>.01 || Math.abs(to[0]-from[0])>1e-5 || Math.abs(dy)<1e-5 || Math.abs(dz)<1e-5)continue;
                                    double du=(data.getFloat((i+next)*stride+uv)-data.getFloat((i+edge)*stride+uv))/(wall.getMaxU()-wall.getMinU())*16;
                                    double dv=(data.getFloat((i+next)*stride+uv+4)-data.getFloat((i+edge)*stride+uv+4))/(wall.getMaxV()-wall.getMinV())*16;
                                    // End caps use another sprite and move in X/Z, not Y/Z.
                                    double physical=Math.sqrt(dy*dy+dz*dz),mapped=Math.sqrt(du*du+dv*dv);
                                    require(Math.abs(physical/mapped-Math.sqrt(1+span*span/256))<1e-4,
                                            "unexpected diagonal broad-face UV metric");
                                    measuredSlopeEdges++;
                                }
                                double[] center={8,8,(inverted?span:0)+(inverted?-span:span)*.5};
                                if(shallow){double y=center[1];center[1]=center[2]+(inverted?8:0);center[2]=y;}
                                require((a[0]-center[0])*nx+(a[1]-center[1])*ny+(a[2]-center[2])*nz>0,
                                        "inward face normal");
                            }
                        }
                    }
                }
            require(measuredSlopeEdges>0,"no emitted sloped UV edges measured");
            System.out.println("[vandorlabs][reprolab] diagonal-square-uv-metric PASS edges="+measuredSlopeEdges+" shallow=1.118034 steep=1.414214 unchanged-full-artwork");
            System.out.println("[vandorlabs][reprolab] diagonal-fill-textures PASS");
            System.out.println("[vandorlabs][reprolab] diagonal-lighting-data PASS");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        finally { OpenGlHelper.lastBrightnessX=oldX; OpenGlHelper.lastBrightnessY=oldY; }
    }
    private static double[] position(ByteBuffer data,int offset) {
        return new double[]{data.getFloat(offset),data.getFloat(offset+4),data.getFloat(offset+8)};
    }
    private static void require(boolean value,String message) {
        if(!value)throw new IllegalStateException(message);
    }
}
