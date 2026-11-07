package com.vandorlabs.neoforge.glass;

import com.google.gson.*;
import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.surface.JoinedRectangles;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Actual Source separate rectangle/contour/authored decisions over loaded irregular patches. */
public final class GlassGroupChecks {
 private static int checks;
 private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Glass groups "+text);}
 private static void layout(JsonArray expected,NativePanelLayout l){need(expected.get(0).getAsInt()==l.column()&&expected.get(1).getAsInt()==l.row()&&expected.get(2).getAsInt()==l.columns()&&expected.get(3).getAsInt()==l.rows()&&expected.get(4).getAsBoolean()==l.flipU()&&expected.get(5).getAsBoolean()==l.flipV(),"rectangle");}
 public static void main(String[] args)throws Exception {int profiles=0,members=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();int mask=row.get("mask").getAsInt(),variant=row.get("variant").getAsInt(),depth=row.get("depth").getAsInt();boolean rotated=row.get("rotated").getAsBoolean();Map<Long,NativeGlassGroups.Cell> cells=new HashMap<>();for(int i=0;i<6;i++)if((mask&(1<<i))!=0){boolean peer=i==1;var tag=new CompoundTag();tag.putInt("GlassShade",peer&&variant==1?8:0);tag.putInt("GlassTint",peer&&variant==2?2:1);tag.putInt("GlassMaterial",peer&&variant==3?0:-1);tag.putInt("GlassTextureLayout",peer&&variant==4?0:1);tag.putBoolean("GlassFramed",!(peer&&variant==5));tag.putBoolean("GlassJoin",!(peer&&variant==7));if(peer&&variant==6)tag.putInt("FrameInterior",0);if(i==0&&variant==11)tag.putBoolean("FrameTile",true);var settings=NativeGlassSettings.read(tag);cells.put(JoinedRectangles.cell(i%3,i/3),new NativeGlassGroups.Cell(settings,peer&&variant==10?!rotated:rotated,peer&&variant==9?(depth+1)%3:depth,settings.glass().material,false));}
  NativeGlassGroups.Cells access=(x,y)->cells.get(JoinedRectangles.cell(x,y));for(var value:row.getAsJsonArray("cells")){var c=value.getAsJsonObject();int cell=c.get("cell").getAsInt(),x=cell%3,y=cell/3;long key=JoinedRectangles.cell(x,y);layout(c.getAsJsonArray("pane"),NativeGlassGroups.capture(access,x,y,false).get(key));layout(c.getAsJsonArray("frame"),NativeGlassGroups.capture(access,x,y,true).get(key));need(c.get("contour").getAsInt()==NativeGlassGroups.contour(access,x,y),"contour");boolean bent=access.at(x,y).rotated()!=rotated&&(access.at(x-1,y)!=null||access.at(x+1,y)!=null);need(c.get("authored").getAsInt()==NativeGlassGroups.authored(access,x,y,bent),"authored profile="+profiles+" cell="+cell+" variant="+variant+" actual="+NativeGlassGroups.authored(access,x,y)+" expected="+c.get("authored"));members++;}profiles++;}}
  var uniform=new NativeGlassGroups.Cell(NativeGlassSettings.read(new CompoundTag()),false,0,-1,false);int[] reads={0};var capped=NativeGlassGroups.capture((x,y)->{reads[0]++;return y==0&&x>=0&&x<NativeGlassGroups.LIMIT?uniform:null;},0,0,false);need(capped.size()==NativeGlassGroups.LIMIT&&capped.values().stream().allMatch(NativePanelLayout.UNIT::equals),"Source 4096-member cap returns unit layouts");need(reads[0]<=4*NativeGlassGroups.LIMIT+1,"bounded preparation reads");
  for(boolean rotated:new boolean[]{false,true})for(boolean opposite:new boolean[]{false,true})for(int columns=1;columns<=32;columns++)for(int column=0;column<columns;column++){
   var tag=new CompoundTag();tag.putInt("GlassMaterial",0);var settings=NativeGlassSettings.read(tag);var pose=new com.vandorlabs.render.FlatGlassPolicy.Pose(rotated,0,1);var original=new NativePanelLayout(column,2,columns,4,true,true);var canonical=rotated?net.minecraft.core.Direction.EAST:net.minecraft.core.Direction.NORTH;
   var visual=new NativeFlatGlassVisual(settings,pose,original,0,0,new NativeFlatGlassVisual.Contact(6D,null,true,null,false,false),opposite?canonical.getOpposite():canonical,6,false);var actual=visual.materialLayout();
   need(actual.column()==(opposite?columns-1-column:column)&&actual.row()==2&&actual.columns()==columns&&actual.rows()==4,"Source bent contact-oriented material column");
   need(opposite?!actual.flipU()&&!actual.flipV():actual==original,"Source mirrored layout resets flip flags only when recreated");
  }
  need(profiles==2304&&members==8064,"Source coverage");System.out.println("[vandorlabs][flat-glass-groups] PASS profiles="+profiles+" members="+members+" fields="+checks);
 }
}
