package com.vandorlabs.surface.core;

import com.vandorlabs.turntable.TurntableGeometry;
import java.util.*;

/** Engine-neutral exact planar topology shared by drawing pads and both controllers. */
public final class SurfaceLayout {
    public static final int MAX_SIZE=32,MAX_CELLS=MAX_SIZE*MAX_SIZE;
    public static final class Cell implements Comparable<Cell> {
        public final int x,y,z;
        public Cell(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public Cell offset(int x,int y,int z){return new Cell(Math.addExact(this.x,x),Math.addExact(this.y,y),Math.addExact(this.z,z));}
        @Override public int compareTo(Cell other){int order=Integer.compare(y,other.y);if(order==0)order=Integer.compare(z,other.z);return order==0?Integer.compare(x,other.x):order;}
        @Override public boolean equals(Object value){return value instanceof Cell&&x==((Cell)value).x&&y==((Cell)value).y&&z==((Cell)value).z;}
        @Override public int hashCode(){return (y*31+z)*31+x;}
    }
    public final TurntableGeometry.Face face;
    public final List<Cell> cells;
    public final Cell root;
    public final int width,height,minU,minV,plane;
    public final double pivotX,pivotY,pivotZ;
    public SurfaceLayout(Collection<Cell> members,TurntableGeometry.Face face,boolean rectangle){
        if(members.isEmpty())throw new IllegalArgumentException("Surface area is empty");
        if(members.size()>MAX_CELLS)throw new IllegalArgumentException("Programmable Surfaces are limited to 32 x 32 blocks");
        this.face=Objects.requireNonNull(face);List<Cell> ordered=new ArrayList<Cell>(members);Collections.sort(ordered);cells=Collections.unmodifiableList(ordered);root=ordered.get(0);
        int lowU=Integer.MAX_VALUE,lowV=lowU,highU=Integer.MIN_VALUE,highV=highU;
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        plane=face.plane(root.x,root.y,root.z);
        for(Cell p:ordered){
            int u=face.u(p.x,p.y,p.z),v=face.v(p.x,p.y,p.z);if(face.plane(p.x,p.y,p.z)!=plane)throw new IllegalArgumentException("Programmable Surfaces must lie in one plane");
            lowU=Math.min(lowU,u);highU=Math.max(highU,u);lowV=Math.min(lowV,v);highV=Math.max(highV,v);
            minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);
        }
        long w=(long)highU-lowU+1,h=(long)highV-lowV+1;
        if(w<1||h<1||w>MAX_SIZE||h>MAX_SIZE||w*h!=ordered.size()||new HashSet<Cell>(ordered).size()!=ordered.size()||!rectangle&&w!=h)throw new IllegalArgumentException("Surface must be a filled "+(rectangle?"rectangle":"square")+" (found "+w+" x "+h+", "+ordered.size()+" cells; maximum 32 x 32)");
        width=(int)w;height=(int)h;minU=lowU;minV=lowV;
        pivotX=((double)minX+maxX+1)/2+face.x*.5;pivotY=((double)minY+maxY+1)/2+face.y*.5;pivotZ=((double)minZ+maxZ+1)/2+face.z*.5;
    }
    public Cell cell(int u,int v,int depth){
        int a=Math.addExact(minU,u),b=Math.addExact(minV,v),c=Math.addExact(plane,depth);
        return position(face,a,b,c);
    }
    /** Inclusive bounds of a prism extending outwards from the textured surface. */
    public static int[] volume(TurntableGeometry.Face face,int minU,int minV,int plane,int width,int height,int depth){
        if(width<1||width>MAX_SIZE||height<1||height>MAX_SIZE||depth<1||depth>16)
            throw new IllegalArgumentException("Surface volume bounds");
        int maxU=Math.addExact(minU,width-1),maxV=Math.addExact(minV,height-1);
        Cell first=position(face,minU,minV,Math.addExact(plane,1));
        Cell last=position(face,maxU,maxV,Math.addExact(plane,depth));
        return new int[]{
            Math.min(first.x,last.x),Math.min(first.y,last.y),Math.min(first.z,last.z),
            Math.max(first.x,last.x),Math.max(first.y,last.y),Math.max(first.z,last.z)
        };
    }
    private static Cell position(TurntableGeometry.Face face,int u,int v,int normal){
        return new Cell(face.ux*u+face.vx*v+face.x*normal,face.uy*u+face.vy*v+face.y*normal,face.uz*u+face.vz*v+face.z*normal);
    }
    /** Cropped artwork is independent on unequal rectangles, preserving the Source drawing rule. */
    public boolean independentArtwork(int mode){return mode==2&&width!=height;}
}
