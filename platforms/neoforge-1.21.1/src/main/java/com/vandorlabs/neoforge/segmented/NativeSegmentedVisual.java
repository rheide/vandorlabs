package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import net.minecraft.core.Direction;
import java.util.*;
/** Immutable client/server publication; contains no rendering, tile or world references. */
public record NativeSegmentedVisual(int width,int height,int column,Direction facing,int texture,NativeSegmentedDoorSettings settings,List<NativeSegmentedDoorSettings> rows,List<SegmentedWindowLayout> windows,boolean ready,boolean suppressed,boolean awaitingChunk,boolean terrainEligible,boolean headerTerrain){
  public NativeSegmentedVisual(int width,int height,int column,Direction facing,int texture,NativeSegmentedDoorSettings settings,List<NativeSegmentedDoorSettings> rows,List<SegmentedWindowLayout> windows,boolean ready,boolean suppressed,boolean awaitingChunk,boolean terrainEligible){this(width,height,column,facing,texture,settings,rows,windows,ready,suppressed,awaitingChunk,terrainEligible,false);}
  public NativeSegmentedVisual {if(width<1||width>32||height<1||height>32||column<0||column>=width||facing==null||facing.getAxis().isVertical()||settings==null||rows.size()!=height||windows.size()!=height)throw new IllegalArgumentException("Segmented column projection");rows=List.copyOf(rows);windows=List.copyOf(windows);}
 
 public record Motion(UUID identity,UUID motionIdentity,long sequence,boolean targetOpen,double initialPose,int ticksPerSection,long started,int duration){
  public Motion {if(identity==null||sequence<0||!Double.isFinite(initialPose)||initialPose<0||initialPose>1||ticksPerSection<1||ticksPerSection>960||duration<1||duration>960)throw new IllegalArgumentException("Segmented motion projection");}
  public Motion(UUID id,UUID group,long sequence,boolean target,double initial,int ticks){this(id,group,sequence,target,initial,ticks,0,Math.max(1,Math.min(960,ticks)));}
 }
}
