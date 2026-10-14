package com.vandorlabs.canopy;

import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Placement and mesh variants for complete fixed cockpit shells. */
public final class CanopyVariants {
    private static List<CanopyMesh.Face> centeredVisor;
    private static final Map<Integer,List<CanopyMesh.Face>> GLASS_ROOFS=new HashMap<>();
    public static boolean visor(String kind){return kind.equals("space_visor_2x1") || kind.equals("space_visor_3x1") || kind.equals("space_visor_5x1") || kind.equals("space_visor_5x1_double_height");}
    public static boolean centered(String kind){return LargeCanopySettings.supports(kind) || visor(kind);}
    public static List<CanopyMesh.Face> faces(TileEntityCanopy tile,List<CanopyMesh.Face> original){
        if(tile.block().kind.equals("regular_canopy_glass")){
            return fixedGlassFaces(tile.mask(),tile.roofMask());
        }
        return faces(tile.centeredPlacement,tile.block().kind,original);
    }
    public static List<CanopyMesh.Face> fixedGlassFaces(int mask,int roof){
        mask&=15;roof&=mask;List<CanopyMesh.Face> original=CanopyMesh.MODELS.get(String.format(Locale.ROOT,"regular_1x1_%02x",mask));
        if(mask==roof)return original;
        int key=mask|(roof<<4);
        synchronized(GLASS_ROOFS){
            List<CanopyMesh.Face> cached=GLASS_ROOFS.get(key);
            if(cached==null){cached=Collections.unmodifiableList(OpeningCanopyGeometry.withRoof(original,CanopyMesh.MODELS.get(String.format(Locale.ROOT,"regular_1x1_%02x",roof))));GLASS_ROOFS.put(key,cached);}
            return cached;
        }
    }
    public static List<CanopyMesh.Face> faces(boolean centered,String kind,List<CanopyMesh.Face> original){return centered && kind.equals("space_visor_2x1")?centeredVisor:original;}
    public static void init() {
        for(String id:new String[]{"space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}) {
            List<CanopyMesh.Face> faces=new ArrayList<>(CanopyMesh.MODELS.get(id));double width=CanopyMesh.DIMENSIONS.get(id)[0];
            for(CanopyMesh.Face glass:CanopyMesh.MODELS.get(id))if(glass.baseMaterial.equals("glass")) {
                double top=0;for(Vec3d vertex:glass.vertices)top=Math.max(top,vertex.y);
                if(Math.abs(top-CanopyMesh.DIMENSIONS.get(id)[2])>1e-8)continue;
                List<Vec3d> edge=new ArrayList<>();for(Vec3d vertex:glass.vertices)if(Math.abs(vertex.y-top)<1e-8)edge.add(vertex);
                if(edge.size()==2)rail(faces,edge.get(0),edge.get(1),width);
            }
            CanopyMesh.MODELS.put(id,Collections.unmodifiableList(faces));
        }
        centeredVisor=shift(CanopyMesh.MODELS.get("space_visor_2x1"),.5,0);

    }
    private static CanopyMesh.Face copy(CanopyMesh.Face source){CanopyMesh.Face face=new CanopyMesh.Face();face.material=source.material;face.baseMaterial=source.baseMaterial;face.group=source.group;face.doubleSided=source.doubleSided;face.uv=source.uv;return face;}
    private static List<CanopyMesh.Face> shift(List<CanopyMesh.Face> source,double x,double z){List<CanopyMesh.Face> result=new ArrayList<>();for(CanopyMesh.Face original:source){CanopyMesh.Face face=copy(original);face.vertices=new Vec3d[original.vertices.length];for(int i=0;i<face.vertices.length;i++)face.vertices[i]=original.vertices[i].addVector(x,0,z);result.add(face);}return Collections.unmodifiableList(result);}
    private static void rail(List<CanopyMesh.Face> result,Vec3d a,Vec3d b,double width) {
        Vec3d direction=b.subtract(a);double length=Math.hypot(direction.x,direction.z);if(length<1e-8)return;
        double nx=-direction.z/length/32,nz=direction.x/length/32;Vec3d[] v=new Vec3d[8];
        for(int y=0;y<2;y++)for(int end=0;end<2;end++)for(int side=0;side<2;side++) {
            Vec3d p=end==0?a:b;double sign=side==0?-1:1;
            v[y*4+end*2+side]=new Vec3d(Math.max(0,Math.min(width,p.x+sign*nx)),p.y-(y==0?1D/16:0),Math.max(0,Math.min(1,p.z+sign*nz)));
        }
        for(int[] indices:new int[][]{{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}}) {
            CanopyMesh.Face face=new CanopyMesh.Face();face.vertices=new Vec3d[4];for(int i=0;i<4;i++)face.vertices[i]=v[indices[i]];
            face.uv=new double[][]{{0,0},{1,0},{1,1},{0,1}};face.material="canopy_canopy_steel";face.baseMaterial="steel";face.group="top_border";face.doubleSided=true;result.add(face);
        }
    }
    private CanopyVariants(){}
}
