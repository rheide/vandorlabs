package com.vandorlabs.items;
import java.util.*;

/** Region bounds and multi-cell ownership are checked independently of Minecraft adapters. */
public final class ConnectedCopySelectionTest {
 private static final class P {final int x,y,z;P(int x,int y,int z){this.x=x;this.y=y;this.z=z;}@Override public boolean equals(Object other){if(!(other instanceof P))return false;P p=(P)other;return x==p.x&&y==p.y&&z==p.z;}@Override public int hashCode(){return (x*31+y)*31+z;}}
 private static final class World implements ConnectedCopySelection.Access<P>{final Map<P,P> cells=new HashMap<P,P>();final Set<P> reads=new HashSet<P>();public P matchingOwner(P p){if(!reads.add(p))throw new AssertionError("cell visited twice");return cells.get(p);}public P offset(P p,int x,int y,int z){return new P(p.x+x,p.y+y,p.z+z);}}
 private static void need(boolean yes){if(!yes)throw new AssertionError("connected selection contract");}
 public static void main(String[] args){
  P origin=new P(0,0,0),corner=new P(1,1,1),edge=new P(2,2,1),distant=new P(20,0,0);World world=new World();for(P p:Arrays.asList(origin,corner,edge,distant))world.cells.put(p,p);selectionCount(world,origin,3);
  world=new World();P upper=new P(0,1,0),peer=new P(0,2,0);world.cells.put(origin,origin);world.cells.put(upper,origin);world.cells.put(peer,peer);ConnectedCopySelection.Result<P> result=ConnectedCopySelection.discover(upper,origin,world);need(result.cells==3&&result.owners.size()==2&&result.owners.get(0).equals(origin)&&result.owners.get(1).equals(peer));
  world=new World();for(int x=0;x<4096;x++){P p=new P(x,0,0);world.cells.put(p,p);}result=ConnectedCopySelection.discover(origin,origin,world);need(!result.exceeded&&result.cells==4096&&result.owners.size()==4096);world.reads.clear();P extra=new P(4096,0,0);world.cells.put(extra,extra);result=ConnectedCopySelection.discover(origin,origin,world);need(result.exceeded&&result.cells==4097&&result.owners.isEmpty());
  Set<String> removed=new HashSet<String>();CopiedDirectionality.strip(removed::add);need(removed.size()==8&&removed.contains("stair_roll")&&removed.contains("placement_wall_position"));need(CopiedDirectionality.ignoreProperty("half",false,false)&&CopiedDirectionality.ignoreProperty("arbitrary",true,false)&&!CopiedDirectionality.ignoreProperty("powered",false,false));
  System.out.println("Connected copy selection PASS corner/edge/physical-cell-budget/owner-dedup/stable-order/directionality");
 }
 private static void selectionCount(World world,P origin,int count){ConnectedCopySelection.Result<P> selected=ConnectedCopySelection.discover(origin,origin,world);need(!selected.exceeded&&selected.cells==count&&selected.owners.size()==count);}
}
