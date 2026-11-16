package com.vandorlabs.geometry;

public final class CoreMeshTest {
    public static void main(String[] args){int faces=0;java.util.Set<Integer> colors=new java.util.HashSet<>();
        for(String id:CoreMesh.IDS){colors.add(CoreMesh.color(id));double area=0;check(CoreMesh.faces(id).size()==174,"29 actual solid prism components");
            for(CoreMesh.Face face:CoreMesh.faces(id)){double length=face.nx*face.nx+face.ny*face.ny+face.nz*face.nz;check(Math.abs(length-1)<1e-9,"unit normal");double[] a=new double[3],b=new double[3];for(int axis=0;axis<3;axis++){a[axis]=face.coordinate(1,axis)-face.coordinate(0,axis);b[axis]=face.coordinate(3,axis)-face.coordinate(0,axis);for(int vertex=0;vertex<4;vertex++){double x=face.coordinate(vertex,axis);check(Double.isFinite(x)&&x>.1&&x<.9,"padded finite block bounds");}}
                double cx=a[1]*b[2]-a[2]*b[1],cy=a[2]*b[0]-a[0]*b[2],cz=a[0]*b[1]-a[1]*b[0];area+=Math.sqrt(cx*cx+cy*cy+cz*cz);faces++;
            }check(area>.5&&area<2,"sparse scaffold surface, not an enclosing cube");
        }int hits=0,total=0;for(int y=0;y<48;y++)for(int z=0;z<48;z++){double py=.14+(y+.5)*.72/48,pz=.14+(z+.5)*.72/48;boolean hit=false;for(CoreMesh.Face face:CoreMesh.faces("synthesis_tower"))if(intersects(face,py,pz)){hit=true;break;}if(hit)hits++;total++;}check(hits>total*.10&&hits<total*.55,"actual ray-visible negative space differs from full solid cube stub hits="+hits+"/"+total);check(colors.size()==13,"distinct per-Core palette");System.out.println("CoreMeshTest PASS types13 faces"+faces+" rods/corners/innerCube finiteNormals realSparseGeometry paddedBounds");
    }
    private static boolean intersects(CoreMesh.Face face,double y,double z){
        if(Math.abs(face.nx)<1e-10)return false;double x=face.coordinate(0,0)-(face.ny*(y-face.coordinate(0,1))+face.nz*(z-face.coordinate(0,2)))/face.nx;
        for(int vertex=0;vertex<4;vertex++){int next=(vertex+1)%4;double ax=face.coordinate(next,0)-face.coordinate(vertex,0),ay=face.coordinate(next,1)-face.coordinate(vertex,1),az=face.coordinate(next,2)-face.coordinate(vertex,2),bx=x-face.coordinate(vertex,0),by=y-face.coordinate(vertex,1),bz=z-face.coordinate(vertex,2);double dot=(ay*bz-az*by)*face.nx+(az*bx-ax*bz)*face.ny+(ax*by-ay*bx)*face.nz;if(dot< -1e-9)return false;}return true;
    }
    private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
