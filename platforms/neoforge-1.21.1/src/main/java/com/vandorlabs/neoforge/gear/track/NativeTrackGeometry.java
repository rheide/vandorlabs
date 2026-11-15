package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.gear.track.core.*;
import com.vandorlabs.neoforge.gear.NativeGearGeometry;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Atlas-independent authored collision. Weighted bounds include the retained cuboids and shape nodes. */
public final class NativeTrackGeometry {
    private record Key(int style,int facing,int appearance){}
    private record Entry(List<AABB> boxes,VoxelShape shape,int bytes){}
    private static final LinkedHashMap<Key,Entry> CACHE=new LinkedHashMap<>(64,.75F,true);
    private static int retained;
    private static synchronized Entry entry(int style,int facing,int appearance){var key=new Key(style,facing,appearance);var found=CACHE.get(key);if(found!=null)return found;var boxes=new ArrayList<AABB>();TrackMeshPolicy.emit(TrackTopology.Style.values()[style],Math.floorMod(facing+2,4),appearance,part->{for(var raw:NativeGearGeometry.model("vandorlabs:block/track/"+part.name,false))boxes.add(NativeGearGeometry.yaw(raw,part.quarterTurns));});VoxelShape shape=com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(boxes);var xs=new TreeSet<Double>();var ys=new TreeSet<Double>();var zs=new TreeSet<Double>();for(var box:boxes){xs.add(box.minX);xs.add(box.maxX);ys.add(box.minY);ys.add(box.maxY);zs.add(box.minZ);zs.add(box.maxZ);}long cells=(long)Math.max(0,xs.size()-1)*Math.max(0,ys.size()-1)*Math.max(0,zs.size()-1);int bytes=Math.toIntExact(512+boxes.size()*96L+(xs.size()+ys.size()+zs.size())*16L+(cells+7)/8);found=new Entry(List.copyOf(boxes),shape,bytes);while(!CACHE.isEmpty()&&(CACHE.size()>=1024||retained+bytes>4*1024*1024)){var it=CACHE.entrySet().iterator();var old=it.next();retained-=old.getValue().bytes;it.remove();}if(bytes<=4*1024*1024){CACHE.put(key,found);retained+=bytes;}return found;}
    public static List<AABB> boxes(int style,int facing,int appearance){return entry(style,facing,appearance).boxes;}
    public static List<AABB> boxes(NativeTrackEntity tile,boolean onArm){int key=onArm?tile.appearance().onArm().key():tile.appearance().key();return entry(tile.style().ordinal(),tile.getBlockState().getValue(com.vandorlabs.neoforge.gear.NativeGearBlock.FACING).get2DDataValue(),key).boxes;}
    public static VoxelShape shape(NativeTrackEntity tile){return entry(tile.style().ordinal(),tile.getBlockState().getValue(com.vandorlabs.neoforge.gear.NativeGearBlock.FACING).get2DDataValue(),tile.appearance().key()).shape;}
    private NativeTrackGeometry(){}
}
