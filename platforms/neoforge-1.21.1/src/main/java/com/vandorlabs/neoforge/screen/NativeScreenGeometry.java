package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.render.InputSurfaceLayout;
import net.minecraft.world.phys.AABB;
import java.util.*;
/** Immutable block geometry. Build on settings/state changes and retain with the host projection. */
public record NativeScreenGeometry(AABB bounds,List<AABB> collision) {
 public NativeScreenGeometry {Objects.requireNonNull(bounds);collision=List.copyOf(collision);}
 public static NativeScreenGeometry of(ScreenFamily family,ScreenFamily.State state,NativeScreenSettings settings){return of(family,state,settings.scalar().smallInput,settings.scalar().wallPosition,settings.ceilingMounted(),settings.ceilingPosition());}
 public static NativeScreenGeometry of(ScreenFamily family,ScreenFamily.State state,boolean small,int wall,boolean ceiling,int ceilingPosition){
  Objects.requireNonNull(family);Objects.requireNonNull(state);if(wall < -1||wall>2||ceilingPosition < -1||ceilingPosition>2||family.horizontal&&(state.facing<2)||state.keyboard&&!family.keyboard||state.upper&&!(family.keyboard||family==ScreenFamily.HALF_DIAGONAL)||state.inverted&&family!=ScreenFamily.DIAGONAL)throw new IllegalArgumentException("Invalid screen geometry state");
  AABB bounds=new AABB(0,0,0,1,1,1);List<AABB> collision=new ArrayList<>();
  if(family==ScreenFamily.HALF_CONSOLE){double offset=ceiling?InputSurfaceLayout.ceilingStart(16,ceilingPosition<0?1:ceilingPosition)/16:0;bounds=rotate(new AABB(0,0,offset,1,.5,1+offset),state.facing);}
  else if(family.keyboard){boolean full=family==ScreenFamily.INPUT;InputSurfaceLayout.Box box=ceiling?InputSurfaceLayout.ceilingInput(full,small,ceilingPosition<0?(full?1:2):ceilingPosition).housing:full?InputSurfaceLayout.fullInput(state.keyboard,state.upper,small).housing:InputSurfaceLayout.halfInput(state.keyboard,state.upper,wall<0?(state.upper?2:0):wall,small).housing;bounds=rotate(new AABB(box.x0/16,box.y0/16,box.z0/16,box.x1/16,box.y1/16,box.z1/16),state.facing);}
  else if(family==ScreenFamily.HALF_DIAGONAL)bounds=rotate(new AABB(0,state.upper?.5:0,.5,1,state.upper?1:.5,1),state.facing);
  if(family==ScreenFamily.DIAGONAL||family==ScreenFamily.HALF_DIAGONAL){int steps=family==ScreenFamily.DIAGONAL?16:8;boolean upper=family==ScreenFamily.DIAGONAL?state.inverted:state.upper;for(int i=0;i<steps;i++){double height=(i+1)/16D,z=(i+(steps==8?8:0))/16D;collision.add(rotate(new AABB(0,upper?1-height:0,z,1,upper?1:height,z+1D/16),state.facing));}}
  else collision.add(bounds);
  return new NativeScreenGeometry(bounds,collision);
 }
 private static AABB rotate(AABB b,int facing){return switch(facing){case 5->new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);case 3->new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);case 4->new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);default->b;};}
}
