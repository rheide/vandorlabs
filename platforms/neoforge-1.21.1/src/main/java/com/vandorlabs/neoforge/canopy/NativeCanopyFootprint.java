package com.vandorlabs.neoforge.canopy;

import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import java.util.*;
import net.minecraft.core.*;

/** Source reservation cells remain independent of both drawing clearances and broad window collision. */
public final class NativeCanopyFootprint {
    private NativeCanopyFootprint(){}
    public static List<BlockPos> cells(String kind,BlockPos pos,Direction facing,NativeCanopySettings settings,NativeCanopyLinks links){
        if(links.footprint()!=null)return links.footprint();
        var size=NativeCanopyModels.builtin().dimensions(kind);boolean large=CanopySettingsData.supports(kind);if(large&&links.hollow())return shell(kind,pos,facing,settings,links.centered());
        int width=kind.equals("space_visor_2x1")&&links.centered()?3:size.width(),depth=large?settings.data().length():size.length();List<BlockPos> out=new ArrayList<>();for(int x=0;x<width;x++)for(int z=0;z<depth;z++)for(int y=0;y<size.height();y++)out.add(pos.relative(facing.getClockWise(),x).relative(facing.getOpposite(),z).above(y));return List.copyOf(out);
    }
    public static List<BlockPos> shell(String kind,BlockPos pos,Direction facing,NativeCanopySettings settings,boolean backAnchored){
        var models=NativeCanopyModels.builtin();var size=models.dimensions(kind);var s=settings.data();var faces=CanopyTransform.faces(CanopyShapes.front(models,size.width(),s,backAnchored),kind,size.width(),s,0);var boxes=CanopyPhysics.boxes(faces,facing.get3DDataValue(),pos.getX(),pos.getY(),pos.getZ(),false);
        int height=s.mount>=2?(int)Math.ceil(Math.max(1D/64,s.depth/16D)+CanopyTransform.FRONT_MOUNT_CLEARANCE):size.height(),depth=s.mount>=2?size.height():s.length();List<BlockPos> candidates=new ArrayList<>();for(int x=0;x<size.width();x++)for(int y=0;y<height;y++)for(int z=0;z<depth;z++)candidates.add(pos.relative(facing.getClockWise(),x).above(s.mount==3?y-height+1:y).relative(facing.getOpposite(),z));Set<BlockPos> occupied=new HashSet<>();occupied.add(pos);
        for(BlockPos cell:candidates){double x0=cell.getX()+.00001,y0=cell.getY()+.00001,z0=cell.getZ()+.00001,x1=cell.getX()+1-.00001,y1=cell.getY()+1-.00001,z1=cell.getZ()+1-.00001;for(var box:boxes)if(box.maxX>x0&&box.minX<x1&&box.maxY>y0&&box.minY<y1&&box.maxZ>z0&&box.minZ<z1){occupied.add(cell);break;}}
        List<BlockPos> out=new ArrayList<>();for(BlockPos cell:candidates)if(occupied.contains(cell))out.add(cell);return List.copyOf(out);
    }
    public static List<BlockPos> reserved(List<BlockPos> cells,Set<Integer> released){Map<BlockPos,Integer> first=new HashMap<>();List<BlockPos> out=new ArrayList<>();for(int i=0;i<cells.size();i++){BlockPos cell=cells.get(i);Integer index=first.putIfAbsent(cell,i);if(!released.contains(index==null?i:index))out.add(cell);}return List.copyOf(out);}
}
