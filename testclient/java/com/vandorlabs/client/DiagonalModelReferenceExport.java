package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
/** Actual current diagonal mesh and material mapping, captured before native model activation. */
public final class DiagonalModelReferenceExport {
 static java.security.MessageDigest digest;static long vertices;static final String GOLDEN="b3e2bf711c6399ff289cbd3516daf3c2f7ede8d541e80be5c0fab2c4d230a6ee";
 static TextureAtlasSprite sprite(String id,int w,int h,int x,int y){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(w);s.setIconHeight(h);s.initSprite(256,128,x,y,false);return s;}
 static void put(int n){for(int i=24;i>=0;i-=8)digest.update((byte)(n>>>i));}
 static JsonArray bounds(TextureAtlasSprite s){JsonArray b=new JsonArray();b.add(s.getMinU());b.add(s.getMaxU());b.add(s.getMinV());b.add(s.getMaxV());return b;}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();digest=java.security.MessageDigest.getInstance("SHA-256");Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);
  Field registry=FrameEdgeTextures.class.getDeclaredField("EDGES");registry.setAccessible(true);Map edges=(Map)registry.get(null);Class<?> edge=Class.forName("com.vandorlabs.client.FrameEdgeTextures$Edge");Constructor<?> ec=edge.getDeclaredConstructor(String.class,String.class,int.class,double.class);ec.setAccessible(true);Object choices=Array.newInstance(edge,4);TextureAtlasSprite[] rail=new TextureAtlasSprite[4];for(int i=0;i<4;i++){rail[i]=(TextureAtlasSprite)ec.newInstance("diagonal_reference_rail"+(i==0?"":"_edge_"+i),"reference",i,1D);rail[i].setIconWidth(64);rail[i].setIconHeight(64);rail[i].initSprite(256,128,i*64,64,false);Array.set(choices,i,rail[i]);}edges.put("diagonal_reference_rail",choices);
  JsonArray poses;try(java.io.Reader r=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.diagonalReference","build/test-artifacts/diagonal-forge-reference.json")))){poses=new JsonParser().parse(r).getAsJsonObject().getAsJsonArray("cases");}
  Path output=Paths.get(System.getProperty("vandorlabs.diagonalModelExport","build/test-artifacts/diagonal-model-forge.jsonl"));Files.createDirectories(output.toAbsolutePath().getParent());int records=0;Gson gson=new Gson();
  try(java.io.PrintWriter writer=new java.io.PrintWriter(Files.newBufferedWriter(output))){for(JsonElement element:poses){JsonObject row=element.getAsJsonObject();if(row.get("inset").getAsBoolean())continue;int index=records++,aspect=(index/3)%3,layout=index%3,flip=(index/9)%4;boolean frame=(index&1)!=0;
   TextureAtlasSprite wall=sprite("diagonal_reference_wall",aspect==2?16:32,aspect==1?16:32,0,0),metal=frame?rail[0]:sprite("diagonal_reference_metal",64,64,192,64);
   int mode=row.get("mode").getAsInt(),fill=row.get("fill").getAsInt();boolean inverted=row.get("inverted").getAsBoolean();
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockProgrammableWall block=new BlockProgrammableWall("diagonal_wall",BlockProgrammableWall.Shape.DIAGONAL),flat=new BlockProgrammableWall("programmable_wall",BlockProgrammableWall.Shape.PLAIN);net.minecraft.util.EnumFacing facing=net.minecraft.util.EnumFacing.byName(row.get("facing").getAsString());
   DiagonalGeometryLegacyChecks.install(world,net.minecraft.util.math.BlockPos.ORIGIN,block,facing,inverted,mode,fill,row.get("joinChoice").getAsInt());DiagonalGeometryLegacyChecks.profile(world,block,flat,facing,inverted,mode,row.get("profile").getAsInt());
   StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawConfiguredWall(capture,(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)world.getTileEntity(net.minecraft.util.math.BlockPos.ORIGIN),world.getBlockState(net.minecraft.util.math.BlockPos.ORIGIN),wall,metal,null);
   StaticSurfaceMesh mesh=capture.finish();GlassSurfaceGroups.Layout group=new GlassSurfaceGroups.Layout(1,1,3,2,(flip&1)!=0,(flip&2)!=0);if(layout!=0)mesh=GlassSurfaceMapping.map(mesh,wall,group,false,0,0,layout);
   float[] values=(float[])data.get(mesh);JsonArray bits=new JsonArray();put(index);put(values.length);for(float value:values){int b=Float.floatToRawIntBits(value);bits.add(b);put(b);}vertices+=values.length/8;
   JsonObject record=new JsonObject();for(Map.Entry<String,JsonElement> entry:row.entrySet())record.add(entry.getKey(),entry.getValue());record.remove("boxes");record.remove("faces");record.remove("selection");record.addProperty("aspect",aspect);record.addProperty("textureLayout",layout);record.addProperty("flip",flip);record.addProperty("frame",frame);JsonArray sprites=new JsonArray();sprites.add(bounds(wall));for(TextureAtlasSprite s:rail)sprites.add(bounds(s));record.add("sprites",sprites);record.add("vertices",bits);writer.println(gson.toJson(record));
  }}finally{edges.remove("diagonal_reference_rail");}
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("Actual diagonal emission changed "+hash);System.out.println("Actual diagonal emitted-model reference PASS records="+records+" vertices="+vertices+" golden="+hash);
 }
}
