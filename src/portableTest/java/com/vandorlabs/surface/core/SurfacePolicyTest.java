package com.vandorlabs.surface.core;

import com.vandorlabs.turntable.TurntableGeometry;
import java.util.*;

/** Exact coordinate/loaded-boundary and emitted-area oracles, independent of engine adapters. */
public final class SurfacePolicyTest {
    private static int checks;
    private static void require(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    private static void refused(Runnable work,String description){checks++;try{work.run();throw new AssertionError(description);}catch(IllegalArgumentException expected){}}
    private static double area(List<SurfaceUvMesh.Vertex[]> triangles){double sum=0;for(SurfaceUvMesh.Vertex[] triangle:triangles){SurfaceUvMesh.Vertex a=triangle[0],b=triangle[1],c=triangle[2];sum+=Math.abs((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x))/2;for(SurfaceUvMesh.Vertex v:triangle)require(v.u>=-1e-9&&v.u<=1+1e-9&&v.v>=-1e-9&&v.v<=1+1e-9,"UV atlas isolation");}return sum;}
    public static void main(String[] ignored){
        for(TurntableGeometry.Face face:TurntableGeometry.Face.values())for(int width=1;width<=8;width++)for(int height=1;height<=8;height++){
            List<SurfaceLayout.Cell> cells=new ArrayList<SurfaceLayout.Cell>();for(int v=0;v<height;v++)for(int u=0;u<width;u++)cells.add(new SurfaceLayout.Cell(17+u*face.ux+v*face.vx,91+u*face.uy+v*face.vy,-13+u*face.uz+v*face.vz));
            SurfaceLayout layout=new SurfaceLayout(cells,face,true);require(layout.width==width&&layout.height==height,"all-face rectangle dimensions");require(new HashSet<SurfaceLayout.Cell>(layout.cells).equals(new HashSet<SurfaceLayout.Cell>(cells)),"no topology translation");
            for(int v=0;v<height;v++)for(int u=0;u<width;u++)require(layout.cell(u,v,1).equals(cells.get(v*width+u).offset(face.x,face.y,face.z)),"six-face cell/depth basis");
            if(width!=height)refused(()->new SurfaceLayout(cells,face,false),"turntable square guard");
        }
        for(int width=1;width<=8;width++)for(int height=1;height<=8;height++)for(int mode=0;mode<3;mode++)for(double aspect:new double[]{.5,1,2}){
            double complete=area(SurfaceUvMesh.build(width,height,mode,aspect,false)),members=0;
            for(int v=0;v<height;v++)for(int u=0;u<width;u++)members+=area(SurfaceUvMesh.buildCell(width,height,mode,aspect,u,v));
            require(Math.abs(complete-members)<1e-8,"per-cell terrain exactly covers the shared face");
            if(mode!=2)require(Math.abs(complete-width*height)<1e-8,"stretch/repeat keep the full solid drawing area");
        }
        final Map<SurfaceLayout.Cell,TurntableGeometry.Face> world=new HashMap<SurfaceLayout.Cell,TurntableGeometry.Face>();final Set<SurfaceLayout.Cell> unavailable=new HashSet<SurfaceLayout.Cell>();
        SurfaceDiscovery.Access<SurfaceLayout.Cell> access=new SurfaceDiscovery.Access<SurfaceLayout.Cell>(){public SurfaceLayout.Cell offset(SurfaceLayout.Cell p,int x,int y,int z){return p.offset(x,y,z);}public boolean loaded(SurfaceLayout.Cell p){return !unavailable.contains(p);}public boolean surface(SurfaceLayout.Cell p){return world.containsKey(p);}public boolean hasTile(SurfaceLayout.Cell p){return world.containsKey(p);}public TurntableGeometry.Face face(SurfaceLayout.Cell p){return world.get(p);}public boolean sameArtwork(SurfaceLayout.Cell a,SurfaceLayout.Cell b){return true;}};
        SurfaceLayout.Cell seed=new SurfaceLayout.Cell(0,0,0);for(int z=0;z<3;z++)for(int x=0;x<3;x++)world.put(new SurfaceLayout.Cell(x,0,z),TurntableGeometry.Face.UP);
        require(SurfaceDiscovery.connected(access,seed,p->true,true).size()==9,"complete component");world.remove(new SurfaceLayout.Cell(1,0,1));Set<SurfaceLayout.Cell> holed=SurfaceDiscovery.connected(access,seed,p->true,true);require(holed.size()==8,"hole retains all surviving members");refused(()->new SurfaceLayout(holed,TurntableGeometry.Face.UP,true),"holes cannot retain a false drawing root");
        unavailable.add(new SurfaceLayout.Cell(3,0,1));refused(()->SurfaceDiscovery.connected(access,seed,p->true,true),"unloaded boundary never scanned or silently truncated");unavailable.clear();world.put(new SurfaceLayout.Cell(2,0,2),TurntableGeometry.Face.NORTH);refused(()->SurfaceDiscovery.connected(access,seed,p->true,false),"mixed normals refused");
        System.out.println("SurfacePolicyTest PASS "+checks+" checks");
    }
}
