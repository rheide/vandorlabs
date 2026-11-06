package com.vandorlabs.neoforge.shape;

import com.vandorlabs.persistence.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Small explicit contact/group contracts, using actual registered native states. */
public final class NativeDiagonalGlassChecks {
 private static int checks;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Diagonal glass projection: "+why);}
 private record Cell(BlockState state,WallSettings walls,PanelGlassSettings glass,PanelPlayback playback){}
 private static final class Access implements NativeDiagonalLayouts.Access<Cell>,NativeDiagonalWallTopology.Access {
  final Map<BlockPos,Cell> cells=new HashMap<>();boolean closed;int reads;
  public boolean loaded(BlockPos p){return !closed;}
  private void check(BlockPos p){need(loaded(p),"unloaded read");reads++;}
  public Cell member(BlockPos p){check(p);return cells.get(p);}
  public BlockState state(BlockPos p){check(p);return cells.containsKey(p)?cells.get(p).state:Blocks.AIR.defaultBlockState();}
  public BlockState localState(Cell c){return c.state;}
  public WallSettings settings(Cell c){return c.walls;}
  public WallSettings settings(BlockPos p){check(p);return cells.containsKey(p)?cells.get(p).walls:new WallSettings(false,false,0,0,false,0);}
  public int mainTexture(Cell c){return 0;}
  public int textureLayout(Cell c){return 0;}
  public boolean solid(BlockPos p,Cell c){return false;}
  public PanelGlassSettings glass(Cell c){return c.glass;}
  public PanelPlayback playback(Cell c){return c.playback;}
  public boolean fullBlock(BlockPos p,BlockState s){check(p);return false;}
 }
 private static Cell cell(NativeDiagonalWallBlock block,Direction face,boolean inv,int mode,int fill,int tint,boolean join){return new Cell(block.defaultBlockState().setValue(NativePanelBlock.FACING,face).setValue(NativeDiagonalWallBlock.INVERTED,inv),new WallSettings(mode!=0,mode==2,fill,0,false,0),new PanelGlassSettings(0,7,2,true,tint,false,join),PanelPlayback.DEFAULT);}
 public static void run(NativeDiagonalWallBlock block){
  BlockPos origin=new BlockPos(-7,81,-11);
  for(Direction face:Direction.Plane.HORIZONTAL)for(boolean inv:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
   var access=new Access();var root=cell(block,face,inv,mode,0,0,true);var right=face.getClockWise();access.cells.put(origin,root);access.cells.put(origin.relative(right),root);access.cells.put(origin.relative(right.getOpposite()),root);
   var group=NativeDiagonalLayouts.capture(access,origin,root,NativePanelGroups.Mode.GLASS);var layout=group.layouts().get(origin.asLong());need(group.members().size()==3&&layout.columns()==3&&layout.rows()==1,"three-cell glass artwork group independent from main artwork");
   var descriptor=NativeDiagonalGlassLayout.capture(access,origin,root.state,layout);need(descriptor.group()==layout&&descriptor.hideMask()==3&&descriptor.rowAxis()==(mode==2?2:1),"same-plane side caps, exact row axis");
   access.cells.put(origin.relative(right),cell(block,face,inv,mode,0,1,true));group=NativeDiagonalLayouts.capture(access,origin,root,NativePanelGroups.Mode.GLASS);need(group.members().size()==2,"different tint separates artwork");need(NativeDiagonalGlassLayout.capture(access,origin,root.state,layout).hideMask()==3,"different tint still hides physical contact caps");
   access.cells.put(origin.relative(right),cell(block,face,inv,mode,1,0,true));need(NativeDiagonalGlassLayout.capture(access,origin,root.state,layout).hideMask()==1,"different fill leaves right cap visible");
   access.cells.clear();access.cells.put(origin,root);for(var d:new Direction[]{face,face.getOpposite()})access.cells.put(origin.relative(d),cell(block,right,inv,mode,0,0,true));need(NativeDiagonalGlassLayout.capture(access,origin,root.state,NativePanelLayout.UNIT).hideMask()==(mode==2?0:12),"perpendicular contacts excluded for half-height mode");
   access.closed=true;int before=access.reads;try{NativeDiagonalGlassLayout.capture(access,origin,root.state,layout);throw new AssertionError("closed origin accepted");}catch(IllegalArgumentException expected){}need(access.reads==before,"closed contact query performs no reads");
  }
  try{new NativeDiagonalGlassLayout(NativePanelLayout.UNIT,64,1);throw new AssertionError("invalid mask accepted");}catch(IllegalArgumentException expected){}
  System.out.println("[vandorlabs][diagonal-glass-projection] dedicated PASS checks="+checks+" 24 states/modes, artwork/contact separation and loaded guards; no client rendering claim");
 }
}
