package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.canopy.OpeningCanopyGeometry;
import java.util.List;
import net.minecraft.util.math.Vec3d;

/** The rear opening stays clear while the side glazing remains continuous. */
public final class CanopyRearGeometryChecks {
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void verify(){
        for(int width:new int[]{1,2,3,9})for(int depth=2;depth<=3;depth++)for(int angle=0;angle<=(depth==3?3:2);angle++){
            List<CanopyMesh.Face> faces=OpeningCanopyGeometry.faces(width,depth,angle,false);
            for(CanopyMesh.Face face:faces){
                double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxY=0;
                for(Vec3d p:face.vertices){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxY=Math.max(maxY,p.y);}
                if(!face.group.equals("mount"))require(!(maxX-minX>.25&&minZ>=depth-3D/32&&maxY<3D/16),"moving rear crossbar remains");
            }
        }
        for(CanopyMesh.Face face:CanopyMesh.MODELS.get("angled_front_1x2")){
            if(!face.baseMaterial.equals("glass"))continue;
            boolean rear=true;for(Vec3d p:face.vertices)rear&=p.z>=1.875;
            require(!rear,"long angled canopy rear pane remains");
        }
        for(double x:new double[]{.0625,.9375}){
            boolean found=false;
            for(CanopyMesh.Face face:CanopyMesh.MODELS.get("canopy")){
                if(!face.baseMaterial.equals("glass"))continue;
                double minY=1,maxY=0,minZ=2,maxZ=0;boolean side=true;
                for(Vec3d p:face.vertices){side&=p.x==x;minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}
                if(side&&minY<=.1953125&&maxY>=.1953125&&minZ<=1.84375&&maxZ>=1.84375)found=true;
            }
            require(found,"rear side glazing sliver missing");
        }
        System.out.println("canopy-rear-geometry PASS opening widths/angles/depths/side slivers/long angled rear");
    }
    public static void main(String[] args){verify();}
}
