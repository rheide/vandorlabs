package com.vandorlabs.light;
import com.vandorlabs.surface.JoinedRectangles;
import java.util.*;
import java.util.function.ToIntFunction;
/** Source rectangle partition and local face coverage; no world, tiles or renderer dependency. */
public final class ProgrammableLightArtwork {
 private ProgrammableLightArtwork(){}
 public static final class Region {
  public final int minRight,minUp,columns,rows;
  Region(int right,int up,int width,int height){minRight=right;minUp=up;columns=width;rows=height;}
  public double left(int axis){return 16.0*(axis-minRight)/columns;}public double right(int axis){return 16.0*(axis-minRight+1)/columns;}
  public double top(int axis){return 16.0*(rows-1-(axis-minUp))/rows;}public double bottom(int axis){return 16.0*(rows-(axis-minUp))/rows;}
 }
 public static <P> Map<P,Region> regions(List<P> members,ToIntFunction<P> right,ToIntFunction<P> up){
  if(members.isEmpty()||members.size()>=ProgrammableLightJoining.LIMIT)throw new IllegalArgumentException("Invalid bounded Light artwork membership");int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE;Map<Long,P> positions=new HashMap<>();
  for(P pos:members){int x=right.applyAsInt(pos),y=up.applyAsInt(pos);x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);if(positions.put(JoinedRectangles.cell(x,y),pos)!=null)throw new IllegalArgumentException("Duplicate Light plane coordinate");}
  Map<P,Region> result=new HashMap<>();if((long)(x1-x0+1)*(y1-y0+1)==members.size()){Region region=new Region(x0,y0,x1-x0+1,y1-y0+1);for(P pos:members)result.put(pos,region);}else for(JoinedRectangles.Rect rect:JoinedRectangles.partition(positions.keySet())){Region region=new Region(rect.x,rect.y,rect.width,rect.height);for(int y=rect.y;y<rect.y+rect.height;y++)for(int x=rect.x;x<rect.x+rect.width;x++)result.put(positions.get(JoinedRectangles.cell(x,y)),region);}if(result.size()!=members.size()||result.containsKey(null))throw new IllegalStateException("Incomplete Light artwork partition");return result;
 }
 public static int visibleFaces(boolean small,int loaded,int sameBlock,int smallNeighbors,int mismatchedHalf){if(((loaded|sameBlock|smallNeighbors|mismatchedHalf)&~63)!=0)throw new IllegalArgumentException("Invalid Light coverage bits");return small?63:63&~(loaded&sameBlock&~smallNeighbors&~mismatchedHalf);}
}
