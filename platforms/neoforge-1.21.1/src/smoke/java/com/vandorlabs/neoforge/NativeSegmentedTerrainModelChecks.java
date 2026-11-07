package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import com.vandorlabs.neoforge.segmented.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
/** Native baked-quad consumer of the same actual Forge terrain records. */
public final class NativeSegmentedTerrainModelChecks {
 private static final class Sprite extends TextureAtlasSprite {Sprite(SpriteContents c,int x,int y){super(TextureAtlas.LOCATION_BLOCKS,c,256,256,x,y);}}
 private static final List<SpriteContents> CONTENTS=new ArrayList<>();
 private static Sprite sprite(String name,int x,int y,int w,int h){var c=new SpriteContents(ResourceLocation.parse("vandorlabs:oracle/"+name),new FrameSize(w,h),new NativeImage(w,h,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new Sprite(c,x,y);}
 private static BakedModel model(TextureAtlasSprite sprite,boolean header){return (BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(),new Class[]{BakedModel.class},(proxy,method,args)->{switch(method.getName()){
  case "getQuads":return header&&args[1]==null?List.of(new BakedQuad(new int[32],-1,Direction.UP,sprite,true)):List.of();
  case "useAmbientOcclusion":return method.getReturnType()==boolean.class?true:TriState.DEFAULT;
  case "getParticleIcon":return sprite;
  case "hashCode":return System.identityHashCode(proxy);case "equals":return proxy==args[0];case "toString":return "Terrain fixture model";
  default:return method.getReturnType()==boolean.class?false:null;
 }});}
 public static void main(String[] args)throws Exception{
  var panel=sprite("panel",32,64,32,32);var edge=sprite("edge",128,64,16,16);var door=sprite("door",64,112,16,32);
  java.util.function.Function<Material,TextureAtlasSprite> textures=m->m.texture().getPath().equals("segmented-artwork/panel")?panel:m.texture().getPath().startsWith("door-artwork/")?door:edge;
  Map<Integer,ProgrammableModel.MaterialSnapshot> materials=new HashMap<>();materials.put(0,new ProgrammableModel.MaterialSnapshot(edge,1,1));materials.put(84,new ProgrammableModel.MaterialSnapshot(door,1,1));var wall=new NativeFlatWallModel.Bake(materials,textures);var shared=new NativeSegmentedRenderer.Bake(wall,textures);
  var bake=new NativeSegmentedTerrainModel.Bake(Map.of(ModelResourceLocation.standalone(NativeSegmentedTerrainModel.HEADER),model(edge,true)),shared,null);Field field=NativeSegmentedTerrainModel.Bake.class.getDeclaredField("canonical");field.setAccessible(true);field.setBoolean(bake,true);var target=NativeSegmentedTerrainModel.wrap(model(panel,false),bake);int profiles=0,quads=0;
  for(String line:Files.readAllLines(Path.of(args[0]))){var row=JsonParser.parseString(line).getAsJsonObject();boolean top=row.get("top").getAsBoolean(),bottom=row.get("bottom").getAsBoolean(),body=row.get("baked").getAsBoolean();var snapshot=new NativeSegmentedTerrainSnapshot(Direction.from2DDataValue(row.get("facing").getAsInt()),row.get("texture").getAsInt(),top,body,top,bottom,profiles+1);var data=ModelData.builder().with(NativeSegmentedTerrainSnapshot.DATA,snapshot).build();var actual=target.getQuads(null,null,RandomSource.create(0),data,null);var expected=row.getAsJsonArray("quads");need(actual.size()==expected.size(),"quad count profile="+profiles);for(int q=0;q<actual.size();q++){var a=actual.get(q);var e=expected.get(q).getAsJsonObject();need(a.getDirection().get3DDataValue()==e.get("face").getAsInt()&&a.isShade()==e.get("shade").getAsBoolean(),"direction/shade");var values=e.getAsJsonArray("vertices");int[] raw=a.getVertices();for(int i=0;i<raw.length;i++)need(raw[i]==values.get(i).getAsInt(),"actual packed source profile="+profiles+" quad="+q+" word="+i+" expected="+values.get(i)+" actual="+raw[i]);quads++;}need((target.useAmbientOcclusion(null,data,null)!=TriState.FALSE)==row.get("ao").getAsBoolean(),"source AO policy");profiles++;}
  for(int direction=0;direction<4;direction++)for(int position=0;position<3;position++){var settings=new NativeSegmentedDoorSettings(direction,false,1,0,false,1,false,NativeSegmentedDoorSettings.DEFAULT.glass,position);var root=NativeSegmentedTerrainSnapshot.unstaged(Direction.NORTH,-1,0,32,settings);need(root.header()==(direction==0&&position==0),"unstaged header ignores stale listed TOP=true");need(!NativeSegmentedTerrainSnapshot.unstaged(Direction.NORTH,-1,31,32,settings).header(),"lower member never gets root header");}
  List<BakedQuad> phase0=null;int phaseVertices=0;for(int row=0;row<4;row++){var snapshot=new NativeSegmentedTerrainSnapshot(Direction.NORTH,84,false,true,false,false,1,1,row);var data=ModelData.builder().with(NativeSegmentedTerrainSnapshot.DATA,snapshot).build();var actual=target.getQuads(null,null,RandomSource.create(0),data,null);if(row==0)phase0=actual;if(row==2)need(actual==phase0,"periodic phase shares immutable terrain quads");for(var q:actual){if(q.getDirection().getAxis()!=Direction.Axis.Z)continue;int[] raw=q.getVertices();for(int v=0;v<4;v++){int at=v*8;double x=Float.intBitsToFloat(raw[at]),y=Float.intBitsToFloat(raw[at+1]),z=Float.intBitsToFloat(raw[at+2]),u=(Float.intBitsToFloat(raw[at+4])-door.getU0())/(door.getU1()-door.getU0()),w=(Float.intBitsToFloat(raw[at+5])-door.getV0())/(door.getV1()-door.getV0()),eu=1+x,ev=(row+1-y)*.5;eu-=Math.floor(eu);ev-=Math.floor(ev);need(Math.min(Math.abs(u-eu),Math.min(Math.abs(u-eu-1),Math.abs(u-eu+1)))<2e-5&&Math.min(Math.abs(w-ev),Math.min(Math.abs(w-ev-1),Math.abs(w-ev+1)))<2e-5,"terrain row/column Tile phase");need(Math.abs(Math.abs(z-.5)-1D/16)<1e-6,"terrain2pixel body");phaseVertices++;}}}need(phaseVertices>=32,"complete phase front/back coverage");
  need(profiles==64&&quads==352,"source coverage");need(bake.retainedBytes()<=16*1024*1024,"terrain byte budget");System.out.println("[vandorlabs][segmented-terrain-model] CPU PASS actual64models352quads exact native position/UV/normal/color/caps/header/shade/AO; unstaged TOP=true non-UP/center/lower regressions; no world upload claim");for(var c:CONTENTS)c.close();
 }
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
