package com.vandorlabs.gear.track;

import java.util.*;
import com.vandorlabs.gear.GearBlock;
import com.vandorlabs.gear.track.core.TrackTopology;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/** Cached conservative cuboid bounds from the same native parts emitted by the track renderer. */
public final class TrackGeometry {
    private static final Map<String,List<AxisAlignedBB>> MODELS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,List<AxisAlignedBB>> SHAPES=Collections.synchronizedMap(new LinkedHashMap<String,List<AxisAlignedBB>>(64,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<AxisAlignedBB>> entry){return size()>512;}});
    /** The arm's lower swivel replaces the standalone track socket, avoiding overlapping joint faces. */
    public static TrackAppearance onArm(TrackAppearance appearance){return appearance.mount==TrackAppearance.Mount.SOCKET?new TrackAppearance(appearance.beltMask,TrackAppearance.Mount.NONE,appearance.sideMask,appearance.bridgeMask,appearance.lowerSideMask):appearance;}
    public static List<AxisAlignedBB> boxes(TrackTopology.Style style,EnumFacing facing,TrackAppearance appearance){
        String key=style+":"+facing+":"+appearance.key();List<AxisAlignedBB> cached=SHAPES.get(key);if(cached!=null)return cached;List<AxisAlignedBB> result=new ArrayList<>();
        for(TrackParts.Part part:TrackParts.forCell(style,facing,appearance))for(AxisAlignedBB box:MODELS.computeIfAbsent(part.model.getResourcePath(),GearBlock::nativeModelParts)){
            double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=-minX,maxZ=-minX;for(double x:new double[]{box.minX,box.maxX})for(double z:new double[]{box.minZ,box.maxZ}){double px=x-.5,pz=z-.5,rx=part.quarterTurns==1?-pz:part.quarterTurns==2?-px:part.quarterTurns==3?pz:px,rz=part.quarterTurns==1?px:part.quarterTurns==2?-pz:part.quarterTurns==3?-px:pz;minX=Math.min(minX,rx+.5);maxX=Math.max(maxX,rx+.5);minZ=Math.min(minZ,rz+.5);maxZ=Math.max(maxZ,rz+.5);}result.add(new AxisAlignedBB(minX,box.minY,minZ,maxX,box.maxY,maxZ));
        }cached=Collections.unmodifiableList(result);SHAPES.put(key,cached);return cached;
    }
    public static AxisAlignedBB bounds(TrackTopology.Style style,EnumFacing facing,TrackAppearance appearance){List<AxisAlignedBB> boxes=boxes(style,facing,appearance);AxisAlignedBB bounds=boxes.get(0);for(int i=1;i<boxes.size();i++)bounds=bounds.union(boxes.get(i));return bounds;}
    private TrackGeometry(){}
}
