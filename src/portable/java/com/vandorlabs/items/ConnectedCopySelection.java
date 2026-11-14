package com.vandorlabs.items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Discover the complete original matching region before any platform-specific apply callback. */
public final class ConnectedCopySelection {
 public static final int MAX_CELLS=4096;
 public interface Access<P> {
  /** Returns the logical owner only when the physical cell and owner are loaded, editable and match. */
  P matchingOwner(P cell);
  P offset(P cell,int x,int y,int z);
  /** Optional directed edge filter; the legacy physical matching region admits all neighbours. */
  default boolean connects(P from,P next){return true;}
 }
 public static final class Result<P> {
  public final List<P> owners;public final int cells;public final boolean exceeded;
  private Result(Set<P> owners,int cells,boolean exceeded){this.owners=Collections.unmodifiableList(new ArrayList<P>(owners));this.cells=cells;this.exceeded=exceeded;}
 }
 public static <P> Result<P> discover(P clicked,P origin,Access<P> access){
  Set<P> visited=new HashSet<P>(),owners=new LinkedHashSet<P>();ArrayDeque<P> pending=new ArrayDeque<P>();pending.add(clicked);pending.add(origin);int cells=0;
  while(!pending.isEmpty()){
   P cell=pending.removeFirst();if(!visited.add(cell))continue;P owner=access.matchingOwner(cell);if(owner==null)continue;
   if(++cells>MAX_CELLS)return new Result<P>(Collections.<P>emptySet(),cells,true);
   owners.add(owner);
   // Face, edge and corner contacts all connect. Multi-cell objects count physical cells.
   for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0){P next=access.offset(cell,x,y,z);if(access.connects(cell,next))pending.add(next);}
  }
  return new Result<P>(owners,cells,false);
 }
 private ConnectedCopySelection(){}
}
