package com.vandorlabs.canopy;

import com.vandorlabs.render.GlassRenderPolicy;

/** Released polygon ordering and float vertex values, independent of the destination vertex format. */
public final class CanopyEmission {
    public interface Sink {
        void quad(int direction);
        void vertex(float x,float y,float z,float u,float v,int color,float nx,float ny,float nz);
    }
    public static final class Sprite {
        public final float minU,maxU,minV,maxV;
        public Sprite(float minU,float maxU,float minV,float maxV){this.minU=minU;this.maxU=maxU;this.minV=minV;this.maxV=maxV;}
        public float u(double normalized){return minU+(maxU-minU)*(float)(normalized*16)/16F;}
        public float v(double normalized){return minV+(maxV-minV)*(float)((1-normalized)*16)/16F;}
    }
    public static final class Prepared {
        public final CanopyFace source;
        public final CanopyPoint center,normal;
        public final float opacity;
        private final CanopyPoint[] vertices;
        public Prepared(CanopyFace source,int facing,float opacity) {
            this.source=source;this.opacity=opacity;vertices=new CanopyPoint[source.points()];
            CanopyPoint center=CanopyPoint.ZERO,normal=CanopyPoint.ZERO;
            for(int i=0;i<vertices.length;i++){vertices[i]=CanopyTransform.rotate(source.vertex(i),facing);center=center.add(vertices[i]);}
            this.center=center.scale(1D/vertices.length);
            for(int i=1;i<vertices.length-1;i++){CanopyPoint n=vertices[i].subtract(vertices[0]).crossProduct(vertices[i+1].subtract(vertices[0]));if(n.lengthSquared()>1E-16){normal=n.normalize();break;}}
            this.normal=normal;
        }
        public int vertexCount(){return (source.points()==4?4:(source.points()-2)*4)*(source.doubleSided?2:1);}
    }
    private static final int[] X={0,0,0,0,-1,1},Y={-1,1,0,0,0,0},Z={0,0,-1,1,0,0};
    private CanopyEmission(){}
    public static float opacity(CanopyFace face,int material,int tint){return face.glazing&&face.baseMaterial.equals("panel")?GlassRenderPolicy.opacity(material,tint):1F;}
    public static boolean translucent(CanopyFace face,int material,int tint){return face.baseMaterial.equals("glass")&&!GlassRenderPolicy.opaque(tint)||opacity(face,material,tint)<1F;}
    private static int direction(float x,float y,float z){int best=2;float distance=Float.MIN_VALUE;for(int i=0;i<6;i++){float value=x*X[i]+y*Y[i]+z*Z[i];if(value>distance){distance=value;best=i;}}return best;}
    public static void emit(Prepared draw,Sprite sprite,boolean chunk,Sink sink) {
        int n=draw.source.points();
        if(n==4){quad(draw,sprite,chunk,sink,0,1,2,3,1);if(draw.source.doubleSided)quad(draw,sprite,chunk,sink,3,2,1,0,-1);return;}
        for(int i=1;i<n-1;i++) {
            quad(draw,sprite,chunk,sink,0,i,i+1,i+1,1);
            if(draw.source.doubleSided){if(chunk)quad(draw,sprite,true,sink,i+1,i+1,i,0,-1);else quad(draw,sprite,false,sink,i+1,i,0,0,-1);}
        }
    }
    private static void quad(Prepared draw,Sprite sprite,boolean chunk,Sink sink,int a,int b,int c,int d,int sign) {
        CanopyPoint normal=draw.normal;
        if(chunk){normal=draw.vertices[b].subtract(draw.vertices[a]).crossProduct(draw.vertices[c].subtract(draw.vertices[a])).normalize();if(normal.lengthSquared()<1E-16)normal=draw.vertices[c].subtract(draw.vertices[a]).crossProduct(draw.vertices[d].subtract(draw.vertices[a])).normalize();}
        float nx=(float)normal.x*(chunk?1:sign),ny=(float)normal.y*(chunk?1:sign),nz=(float)normal.z*(chunk?1:sign);
        sink.quad(direction(nx,ny,nz));
        int alpha=chunk?Math.round(draw.opacity*255):(int)(draw.opacity*255),color=0xFFFFFF|(alpha<<24);
        vertex(draw,sprite,sink,a,color,nx,ny,nz);vertex(draw,sprite,sink,b,color,nx,ny,nz);vertex(draw,sprite,sink,c,color,nx,ny,nz);vertex(draw,sprite,sink,d,color,nx,ny,nz);
    }
    private static void vertex(Prepared draw,Sprite sprite,Sink sink,int index,int color,float nx,float ny,float nz) {
        CanopyPoint p=draw.vertices[index];sink.vertex((float)p.x,(float)p.y,(float)p.z,sprite.u(draw.source.u(index)),sprite.v(draw.source.v(index)),color,nx,ny,nz);
    }
}
