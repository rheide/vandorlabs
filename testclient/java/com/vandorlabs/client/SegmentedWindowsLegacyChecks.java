package com.vandorlabs.client;
import com.vandorlabs.segmented.*;
import com.vandorlabs.persistence.SegmentedWindowLayout;
import net.minecraft.util.math.BlockPos;
/** Actual legacy joined-window traversal against the world-free renderer adapter. */
public final class SegmentedWindowsLegacyChecks {
 public static void main(String[] args){net.minecraft.init.Bootstrap.register();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);int checks=0;
  for(int mask=0;mask<4096;mask++){world.clear();SegmentedDoorWindows.invalidate(world);final int bits=mask;
   for(int y=0;y<4;y++)for(int x=0;x<3;x++){SegmentedDoorTile tile=new SegmentedDoorTile();BlockPos p=new BlockPos(x,80+y,0);tile.setWorld(world);tile.setPos(p);tile.height=4;tile.columns=3;tile.column=x;tile.topOffset=3-y;tile.settings=new SegmentedDoorSettings(0,false,1,0,(mask&(1<<(y*3+x)))!=0,x==2?1:0,true,SegmentedDoorSettings.DEFAULT.glass);world.tiles.put(p,tile);}
   SegmentedWindowLayout[] layouts=SegmentedWindowLayout.derive(3,4,new SegmentedWindowLayout.Cells(){public boolean joined(int x,int y){return(bits&(1<<(y*3+x)))!=0;}public int shape(int x,int y){return x==2?1:0;}public int facing(int x,int y){return 0;}});
   for(int y=0;y<4;y++)for(int x=0;x<3;x++){SegmentedDoorWindows.Layout source=SegmentedDoorWindows.layout((SegmentedDoorTile)world.tiles.get(new BlockPos(x,80+y,0)));SegmentedWindowLayout target=layouts[y*3+x];if(source.column!=target.column||source.row!=target.row||source.width!=target.width||source.height!=target.height)throw new AssertionError("actual window mismatch mask="+mask+" x="+x+" y="+y);checks++;}
  }System.out.println("Actual Segmented Door windows PASS checks="+checks+" joined holes corner-only shape boundaries bottom-up coordinates");
 }
}
