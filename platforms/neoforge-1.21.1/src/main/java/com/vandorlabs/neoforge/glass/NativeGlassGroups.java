package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.GlassConnections;
import com.vandorlabs.surface.JoinedRectangles;
import java.util.*;

/** Owning-thread group preparation over primitive plane coordinates; results retain no access adapter. */
public final class NativeGlassGroups {
 public static final int LIMIT=4096;
 public record Cell(NativeGlassSettings settings,boolean rotated,int depth,int materialKey,boolean playbackSensitive){public Cell{Objects.requireNonNull(settings);if(depth<0||depth>2)throw new IllegalArgumentException("Glass group plane");}}
 @FunctionalInterface public interface Cells {Cell at(int x,int y);}
 private static boolean pose(Cell a,Cell b){return b!=null&&a.rotated==b.rotated&&a.depth==b.depth;}
 public static boolean compatible(Cell a,Cell b,boolean frame){
  if(a==null||!pose(a,b)||!b.settings.glass().join)return false;var x=a.settings.glass();var y=b.settings.glass();
  if(frame){var f=a.settings.frames();var g=b.settings.frames();return x.framed==y.framed&&f.front==g.front&&f.interior==g.interior&&f.exteriorChoice()==g.exteriorChoice()&&f.tile==g.tile;}
  return a.materialKey==b.materialKey&&x.tint==y.tint&&x.textureLayout()==y.textureLayout()&&(!a.playbackSensitive||a.settings.playback().equals(b.settings.playback()));
 }
 public static Map<Long,NativePanelLayout> capture(Cells cells,int x,int y,boolean frame){
  return capture(cells,x,y,frame,(a,b)->true);
 }
 public static Map<Long,NativePanelLayout> capture(Cells cells,int x,int y,boolean frame,java.util.function.BiPredicate<Long,Long> edge){
  Cell root=Objects.requireNonNull(cells.at(x,y));long start=JoinedRectangles.cell(x,y);Map<Long,Cell> members=new LinkedHashMap<>();members.put(start,root);ArrayDeque<Long> queue=new ArrayDeque<>();queue.add(start);
  if(root.settings.glass().join)while(!queue.isEmpty()&&members.size()<LIMIT){long current=queue.removeFirst();int cx=(int)(current>>32),cy=(int)current;Cell owner=members.get(current);for(int direction=0;direction<4;direction++){int nx=cx+(direction==0?1:direction==1?-1:0),ny=cy+(direction==2?1:direction==3?-1:0);long key=JoinedRectangles.cell(nx,ny);if(members.containsKey(key)||!edge.test(current,key))continue;Cell next=cells.at(nx,ny);if(!compatible(owner,next,frame))continue;members.put(key,next);queue.addLast(key);if(members.size()>=LIMIT)break;}}
  Map<Long,NativePanelLayout> result=new HashMap<>();if(members.size()>=LIMIT){for(long key:members.keySet())result.put(key,NativePanelLayout.UNIT);}else for(var rect:JoinedRectangles.partition(members.keySet()))for(int row=0;row<rect.height;row++)for(int col=0;col<rect.width;col++)result.put(JoinedRectangles.cell(rect.x+col,rect.y+row),new NativePanelLayout(col,row,rect.width,rect.height,false,false));return Map.copyOf(result);
 }
 public static int contour(Cells cells,int x,int y){Cell root=Objects.requireNonNull(cells.at(x,y));if(root.settings.frames().tile||!root.settings.glass().join)return 15;boolean[] joined=new boolean[4];int mask=0;for(int side=0;side<4;side++){joined[side]=compatible(root,cells.at(x+(side==0?-1:side==1?1:0),y+(side==2?-1:side==3?1:0)),true);if(!joined[side])mask|=1<<side;}for(int side=0;side<2;side++)for(int vertical=0;vertical<2;vertical++)if(joined[side]&&joined[2+vertical]&&!compatible(root,cells.at(x+(side==0?-1:1),y+(vertical==0?-1:1)),true))mask|=1<<(4+side+vertical*2);return mask;}
 /** Coplanar ordinary-glass neighbors only; cross-family contacts are supplied separately by the installed host. */
 public static int authored(Cells cells,int x,int y){return authored(cells,x,y,false);}
 public static int authored(Cells cells,int x,int y,boolean bent){Cell root=Objects.requireNonNull(cells.at(x,y));var g=root.settings.glass();var f=root.settings.frames();if(bent||!g.framed||f.front>=0||f.interior>=0||f.exteriorChoice()>=0||f.tile)return 0;if(!g.join)return 15;var c=GlassConnections.calculate((dx,dy)->{Cell next=cells.at(x+dx,y+dy);return pose(root,next)&&next.settings.glass().join;});return(c.left?1:0)|(c.right?2:0)|(c.bottom?4:0)|(c.top?8:0)|(c.innerBottomLeft?16:0)|(c.innerBottomRight?32:0)|(c.innerTopLeft?64:0)|(c.innerTopRight?128:0);}
 private NativeGlassGroups(){}
}
