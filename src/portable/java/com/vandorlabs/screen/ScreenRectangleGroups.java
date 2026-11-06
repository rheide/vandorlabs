package com.vandorlabs.screen;
import com.vandorlabs.surface.JoinedRectangles;
import java.util.*;
/** Bounded loaded-only discovery followed by the authored largest-first rectangle partition. */
public final class ScreenRectangleGroups {
 private ScreenRectangleGroups(){}
 public interface Access<T> {int x(T tile);int y(T tile);int z(T tile);boolean loaded(int x,int y,int z);T at(int x,int y,int z);boolean compatible(T origin,T candidate);}
 public static <T> Result<T> discover(T origin,ScreenJoinGeometry.Basis basis,Access<T> access,boolean eligible){
  if(origin==null||basis==null||access==null)throw new IllegalArgumentException("Screen discovery input");
  List<T> found=new ArrayList<>();found.add(origin);if(!eligible)return new Result<T>(found,Collections.singletonList(Collections.singletonList(origin)),false);
  Set<T> seen=Collections.newSetFromMap(new IdentityHashMap<T,Boolean>());seen.add(origin);boolean overflow=false;
  scan:for(int index=0;index<found.size();index++){T tile=found.get(index);for(int step=0;step<4;step++){int x=access.x(tile)+basis.dx(step),y=access.y(tile)+basis.dy(step),z=access.z(tile)+basis.dz(step);if(!access.loaded(x,y,z))continue;T next=access.at(x,y,z);if(next==null||seen.contains(next)||!access.compatible(origin,next))continue;seen.add(next);found.add(next);if(found.size()>=1024){overflow=true;break scan;}}}
  List<List<T>> groups=new ArrayList<>();if(overflow){for(T tile:found)groups.add(Collections.singletonList(tile));}else{Map<Long,T> cells=new HashMap<>();for(T tile:found)cells.put(basis.cell(access.x(tile),access.y(tile),access.z(tile)),tile);for(JoinedRectangles.Rect rect:JoinedRectangles.partition(cells.keySet())){List<T> group=new ArrayList<>();for(int y=rect.y;y<rect.y+rect.height;y++)for(int x=rect.x;x<rect.x+rect.width;x++)group.add(cells.get(JoinedRectangles.cell(x,y)));groups.add(group);}}
  return new Result<T>(found,groups,overflow);
 }
 public static final class Result<T> {
  public final List<T> discovered;public final List<List<T>> rectangles;public final boolean overflow;
  private Result(List<T> found,List<List<T>> groups,boolean overflow){discovered=Collections.unmodifiableList(new ArrayList<T>(found));List<List<T>> copy=new ArrayList<>();for(List<T> group:groups)copy.add(Collections.unmodifiableList(new ArrayList<T>(group)));rectangles=Collections.unmodifiableList(copy);this.overflow=overflow;}
  public List<T> members(T tile){for(List<T> group:rectangles)for(T member:group)if(member==tile)return group;return Collections.singletonList(tile);}
 }
}
