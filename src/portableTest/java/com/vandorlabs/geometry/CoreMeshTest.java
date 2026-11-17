package com.vandorlabs.geometry;

import java.util.HashSet;
import java.util.Set;

/** Geometry contracts follow the authored cuboids and retained cages, rather than an old prism count. */
public final class CoreMeshTest {
    public static void main(String[] args){
        int faces=0,imported=0,cages=0;Set<String> ids=new HashSet<String>();
        for(String id:CoreMesh.IDS){
            check(ids.add(id),"unique active Core identity "+id);
            check((CoreMesh.color(id)&0xff000000)==0,"24-bit artwork palette "+id);
            check(CoreMesh.color(id)==CoreMesh.palette(id).primary,"palette lookup "+id);
            boolean supplied=ImportedCoreData.model(id)!=null;
            if(supplied)imported++;else{cages++;check(id.equals("paladyne_navigation_computer")||id.equals("seyr_navigation_computer"),"retained navigation cage "+id);}
            int grid=supplied?64:CoreMesh.GRID;boolean[] occupied=occupancy(id,grid,supplied);long exposed=0,solid=0;
            for(int y=0;y<grid;y++)for(int z=0;z<grid;z++)for(int x=0;x<grid;x++)if(at(occupied,grid,x,y,z)){
                solid++;for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})if(!at(occupied,grid,x+(axis==0?sign:0),y+(axis==1?sign:0),z+(axis==2?sign:0)))exposed++;
            }
            check(solid>0&&solid<(long)grid*grid*grid,"nonempty authored structure with negative space "+id);
            check(!CoreMesh.faces(id).isEmpty()&&CoreMesh.faces(id).size()<=exposed,"bounded nonempty emitted surface "+id);
            double area=0;
            for(CoreMesh.Face face:CoreMesh.faces(id)){
                double length=face.nx*face.nx+face.ny*face.ny+face.nz*face.nz;
                check(Double.isFinite(length)&&Math.abs(length-1)<1e-9,"unit finite normal "+id);
                double[] a=new double[3],b=new double[3];
                for(int axis=0;axis<3;axis++){a[axis]=face.coordinate(1,axis)-face.coordinate(0,axis);b[axis]=face.coordinate(3,axis)-face.coordinate(0,axis);}
                for(int vertex=0;vertex<4;vertex++){
                    double radiusSquared=0;
                    for(int axis=0;axis<3;axis++){double value=face.coordinate(vertex,axis);check(Double.isFinite(value)&&value>=0&&value<=1,"finite block bounds "+id);radiusSquared+=(value-.5)*(value-.5);}
                    // A centered bounding sphere fits in the slot at every GUI rotation, with visible padding.
                    check(radiusSquared*CoreMesh.ITEM_GUI_SCALE*CoreMesh.ITEM_GUI_SCALE<.25,"rotated inventory padding "+id);
                    check(Double.isFinite(face.u(vertex))&&Double.isFinite(face.v(vertex))&&face.u(vertex)>=0&&face.u(vertex)<=1&&face.v(vertex)>=0&&face.v(vertex)<=1,"finite authored UV bounds "+id);
                    if(supplied)check(face.textured,"supplied Core preserves artwork material "+id);
                }
                double cx=a[1]*b[2]-a[2]*b[1],cy=a[2]*b[0]-a[0]*b[2],cz=a[0]*b[1]-a[1]*b[0];
                double faceArea=Math.sqrt(cx*cx+cy*cy+cz*cz);
                check(Double.isFinite(faceArea)&&faceArea>0,"nondegenerate face "+id);
                check(cx*face.nx+cy*face.ny+cz*face.nz>0,"outward face winding "+id);
                area+=faceArea;faces++;
            }
            check(Math.abs(area-exposed/(double)(grid*grid))<1e-8,"emitted surface equals authored exposed voxel area "+id);
            check(CoreMesh.faces(id,0D).isEmpty(),"empty fabrication begins empty "+id);
            check(CoreMesh.faces(id,1D).size()==CoreMesh.faces(id).size(),"completed fabrication matches Core "+id);
        }
        check(imported>0&&cages>0,"supplied artwork and retained cages both covered");
        int hits=0,total=0;
        for(int y=0;y<48;y++)for(int z=0;z<48;z++){
            double py=(y+.5)/48,pz=(z+.5)/48;boolean hit=false;
            for(CoreMesh.Face face:CoreMesh.faces("paladyne_navigation_computer"))if(intersects(face,py,pz)){hit=true;break;}
            if(hit)hits++;total++;
        }
        check(hits>total*.10&&hits<total*.80,"retained cage ray-visible negative space rejects solid cube hits="+hits+"/"+total);
        System.out.println("CoreMeshTest PASS types"+ids.size()+" imported"+imported+" cages"+cages+" faces"+faces+" authoredSurfaceArea/outwardWinding/finiteUVs/rotatedPadding/negativeSpace");
    }
    private static boolean[] occupancy(String id,int grid,boolean supplied){
        boolean[] values=new boolean[grid*grid*grid];
        if(supplied){for(String row:ImportedCoreData.boxes(ImportedCoreData.model(id))){String[] fields=row.split(",");int[] box=new int[6];for(int i=0;i<6;i++){double value=Double.parseDouble(fields[i]);check(Double.isFinite(value)&&value==Math.rint(value)&&value>=0&&value<=grid,"authored grid coordinate "+id);box[i]=(int)value;}check(box[0]<=box[3]&&box[1]<=box[4]&&box[2]<=box[5],"ordered authored cuboid bounds "+id);/* Authored zero-thickness decals contribute no occupied voxels. Emitted faces must still have positive area. */for(int y=box[1];y<box[4];y++)for(int z=box[2];z<box[5];z++)for(int x=box[0];x<box[3];x++)values[(y*grid+z)*grid+x]=true;}}
        else for(int y=0;y<grid;y++)for(int z=0;z<grid;z++)for(int x=0;x<grid;x++)values[(y*grid+z)*grid+x]=CoreMesh.material(x,y,z)!=CoreMesh.EMPTY;
        return values;
    }
    private static boolean at(boolean[] values,int grid,int x,int y,int z){return x>=0&&y>=0&&z>=0&&x<grid&&y<grid&&z<grid&&values[(y*grid+z)*grid+x];}
    private static boolean intersects(CoreMesh.Face face,double y,double z){
        if(Math.abs(face.nx)<1e-10)return false;double x=face.coordinate(0,0)-(face.ny*(y-face.coordinate(0,1))+face.nz*(z-face.coordinate(0,2)))/face.nx;
        for(int vertex=0;vertex<4;vertex++){int next=(vertex+1)%4;double ax=face.coordinate(next,0)-face.coordinate(vertex,0),ay=face.coordinate(next,1)-face.coordinate(vertex,1),az=face.coordinate(next,2)-face.coordinate(vertex,2),bx=x-face.coordinate(vertex,0),by=y-face.coordinate(vertex,1),bz=z-face.coordinate(vertex,2);double dot=(ay*bz-az*by)*face.nx+(az*bx-ax*bz)*face.ny+(ax*by-ay*bx)*face.nz;if(dot< -1e-9)return false;}return true;
    }
    private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
