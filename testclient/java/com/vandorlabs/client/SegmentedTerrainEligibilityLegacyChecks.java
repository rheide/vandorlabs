package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;
/** Actual canBake across catalog namespaces, all shared poses and a distinct bottom-row style. */
public final class SegmentedTerrainEligibilityLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);SegmentedDoorTile[] rows=new SegmentedDoorTile[32];BlockPos origin=new BlockPos(0,95,0);for(int i=0;i<rows.length;i++){rows[i]=new SegmentedDoorTile();rows[i].setWorld(world);rows[i].setPos(origin.down(i));rows[i].height=32;rows[i].topOffset=i;world.tiles.put(rows[i].getPos(),rows[i]);}TreeSet<Integer> textures=new TreeSet<>();textures.add(-2);textures.add(-1);textures.add(Integer.MAX_VALUE);for(int i=0;i<ScreenHousingTextures.IDS.length;i++){textures.add(i);textures.add(ScreenHousingTextures.choiceAt(i));}textures.add(0x20000000);textures.add(0x40000000);
  File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0;try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(int texture:textures){emit(out,rows,texture,0,0,false,0,false);profiles++;}int frame=ScreenHousingTextures.choiceAt(ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1"));for(int direction=0;direction<4;direction++)for(int position=0;position<3;position++)for(boolean straight:new boolean[]{false,true})for(int broken=0;broken<3;broken++)for(int texture:new int[]{-1,frame,0x40000000}){emit(out,rows,texture,direction,position,straight,broken,false);profiles++;}emit(out,rows,-1,0,0,false,0,true);profiles++;}
  SegmentedReferenceGolden.check(file,"a396e16d7a4459c489682a73be3e8b301b21e5e03b340ff73a1e4f408f0ed66f");
  System.out.println("Segmented actual terrain eligibility PASS profiles="+profiles+" catalog namespaces/shared pose/all32row styles/lower-row texture scope");
 }
 private static void emit(PrintWriter out,SegmentedDoorTile[] rows,int texture,int direction,int position,boolean straight,int broken,boolean lowerCustom){for(SegmentedDoorTile tile:rows){tile.settings=SegmentedDoorSettings.DEFAULT;tile.texture=texture;}rows[0].settings=new SegmentedDoorSettings(direction,straight,1,0,false,1,false,SegmentedDoorSettings.DEFAULT.glass,position);if(broken!=0)rows[31].settings=new SegmentedDoorSettings(0,false,broken==2?0:1,0,broken==1,1,false,SegmentedDoorSettings.DEFAULT.glass);if(lowerCustom)rows[31].texture=0x40000000;JsonObject row=new JsonObject();row.addProperty("texture",texture);row.addProperty("direction",direction);row.addProperty("position",position);row.addProperty("straight",straight);row.addProperty("brokenBottom",broken);row.addProperty("lowerCustom",lowerCustom);row.addProperty("eligible",rows[0].canBake());out.println(new Gson().toJson(row));}
}
