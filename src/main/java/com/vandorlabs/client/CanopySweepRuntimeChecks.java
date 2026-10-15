package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import java.util.*;

/** Dense independent trajectory samples verify analytic bounds and touching-block clearance. */
final class CanopySweepRuntimeChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("canopy sweep: "+text);}
    static void run(TileEntityCanopy owner){
        List<CanopyMesh.Face> source=owner.baseFaces();require(!source.isEmpty(),"real joined mesh available");
        List<CanopyMesh.Face> faces=Arrays.asList(source.get(0),source.get(source.size()/2),source.get(source.size()-1));int checked=0;
        for(String kind:new String[]{"canopy","large_front_canopy_glass"})for(int motion=0;motion<2;motion++)for(int hinge=0;hinge<4;hinge++)for(int mount=0;mount<4;mount++)for(boolean forward:new boolean[]{false,true}){
            LargeCanopySettings settings=new LargeCanopySettings();NBTTagCompound original=new NBTTagCompound();owner.large.write(original);settings.read(original);settings.openable=true;settings.motion=motion;settings.hinge=hinge;settings.mount=mount;settings.slideForward=forward;
            for(int poseStep=0;poseStep<=8;poseStep++){
                double pose=poseStep/8D;List<CanopyMesh.Face> optimized=CanopyPose.faces(faces,kind,owner.width(),settings,pose);
                for(int f=0;f<faces.size();f++)for(int v=0;v<faces.get(f).vertices.length;v++){
                    int originalIndex=settings.mount==1?faces.get(f).vertices.length-1-v:v;Vec3d expected=CanopyPose.vertex(faces.get(f).vertices[originalIndex],faces.get(f).group,kind,owner.width(),settings,pose);
                    require(expected.squareDistanceTo(optimized.get(f).vertices[v])<1e-20,"rendered rigid transform differs from independent vertex reference");
                    require(Arrays.equals(faces.get(f).uv[originalIndex],optimized.get(f).uv[v]),"rendered rigid transform changes UVs");
                }
            }
            for(int interval=0;interval<8;interval++)for(CanopyMesh.Face face:faces){double from=interval/8D,to=(interval+1)/8D;AxisAlignedBB bounds=CanopyPose.sweptBounds(face,kind,owner.width(),settings,from,to);
                for(int step=0;step<=24;step++)for(Vec3d vertex:face.vertices){Vec3d point=CanopyPose.vertex(vertex,face.group,kind,owner.width(),settings,from+(to-from)*step/24D);double epsilon=1e-9;require(point.x>=bounds.minX-epsilon&&point.x<=bounds.maxX+epsilon&&point.y>=bounds.minY-epsilon&&point.y<=bounds.maxY+epsilon&&point.z>=bounds.minZ-epsilon&&point.z<=bounds.maxZ+epsilon,"dense trajectory escapes bounds: "+kind+" motion="+motion+" hinge="+hinge+" mount="+mount+" interval="+interval);checked++;}
            }
        }
        BlockPos root=owner.getPos();List<AxisAlignedBB> wholeBlocks=Arrays.asList(new AxisAlignedBB(root.add(-3,-1,-1)),new AxisAlignedBB(root.add(1,-1,-1)),new AxisAlignedBB(root.add(-2,-1,-1),root.add(1,0,1)));
        for(AxisAlignedBB swept:CanopyReservations.closingSweep(owner))for(AxisAlignedBB block:wholeBlocks)require(!swept.intersects(block.shrink(1e-7)),"full-block floor/side touching boundary falsely obstructs closing: sweep="+swept+" block="+block);
        System.out.println("[vandorlabs][reprolab] canopy-sweep PASS dense-vertices="+checked+" all-hinges mounts slide-directions intervals exact-rotation-extrema full-block-floor-side-clearance");
    }
}
