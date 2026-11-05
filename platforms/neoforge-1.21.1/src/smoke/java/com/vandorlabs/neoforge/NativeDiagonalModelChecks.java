package com.vandorlabs.neoforge;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.PanelCornerGeometry;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
/** CPU comparison against actual configured Forge diagonal draws and material mapping. */
public final class NativeDiagonalModelChecks {
 static volatile Object consumed;
 static java.security.MessageDigest digest;
 static void put(int n){for(int i=24;i>=0;i-=8)digest.update((byte)(n>>>i));}
 static long checks,quads;static int frameMask;static String key;
 static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError(key+" "+message);}
 static final class Sprite extends TextureAtlasSprite {
  final float u0,u1,v0,v1;final int index;
  Sprite(SpriteContents c,JsonArray bounds,float usedU,float usedV,int index){super(TextureAtlas.LOCATION_BLOCKS,c,256,128,0,0);this.index=index;u0=bounds.get(0).getAsFloat();u1=u0+(bounds.get(1).getAsFloat()-u0)/usedU;v0=bounds.get(2).getAsFloat();v1=v0+(bounds.get(3).getAsFloat()-v0)/usedV;}
  @Override public float getU0(){return u0;}@Override public float getU1(){return u1;}@Override public float getV0(){return v0;}@Override public float getV1(){return v1;}
 }
 static Double nullable(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsDouble();}
 static Boolean option(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsBoolean();}
 static float value(JsonArray bits,int p){return Float.intBitsToFloat(bits.get(p).getAsInt());}
 static void compare(JsonArray source,List<BakedQuad> target,Direction facing,boolean frame){
  need(source.size()%32==0&&target.size()==source.size()/16,"quad count expected="+source.size()/16+" actual="+target.size());int turns=facing==Direction.WEST?1:facing==Direction.SOUTH?2:facing==Direction.EAST?3:0;
  for(int q=0;q<source.size()/32;q++){var front=target.get(q*2);int[] a=front.getVertices(),b=target.get(q*2+1).getVertices();double averageU=0,averageV=0;
   for(int v=0;v<4;v++){int p=q*32+v*8,at=v*8;double x=value(source,p)/16D,y=value(source,p+1)/16D,z=value(source,p+2)/16D;float[] expected={(float)(turns==1?z:turns==2?1-x:turns==3?1-z:x),(float)y,(float)(turns==1?1-x:turns==2?1-z:turns==3?x:z)};for(int axis=0;axis<3;axis++)need(a[at+axis]==Float.floatToRawIntBits(expected[axis]),"position q"+q+" v"+v+" axis"+axis+" expected="+expected[axis]+" actual="+Float.intBitsToFloat(a[at+axis]));
    for(int axis=0;axis<2;axis++)need(Math.abs(Float.intBitsToFloat(a[at+4+axis])-value(source,p+3+axis))<3e-6,"atlas UV q"+q+" v"+v+" axis"+axis+" expected="+value(source,p+3+axis)+" actual="+Float.intBitsToFloat(a[at+4+axis]));
    int nx=(byte)(int)(value(source,p+5)*127),ny=(byte)(int)(value(source,p+6)*127),nz=(byte)(int)(value(source,p+7)*127),rx=turns==1?nz:turns==2?-nx:turns==3?-nz:nx,rz=turns==1?-nx:turns==2?-nz:turns==3?nx:nz;need(a[at+7]==((rx&255)|((ny&255)<<8)|((rz&255)<<16)),"packed normal");for(int n=0;n<8;n++)need(a[((4-v)&3)*8+n]==b[at+n],"reverse payload");need(a[at+3]==-1,"white color");averageU+=value(source,p+3)/4;averageV+=value(source,p+4)/4;
   }
   int material=averageV<.4?-1:frame?Math.max(0,Math.min(3,(int)(averageU*4))):4;need(((Sprite)front.getSprite()).index==material,"material slot q"+q+" expected="+material+" actual="+((Sprite)front.getSprite()).index);if(frame&&material>=0)frameMask|=1<<material;need(!front.isShade()&&!front.hasAmbientOcclusion(),"legacy light policy");quads++;
  }
 }
 public static void main(String[] args)throws Exception{
  digest=java.security.MessageDigest.getInstance("SHA-256");List<SpriteContents> contents=new ArrayList<>();Map<Integer,NativePanelQuads.Material[]> materials=new HashMap<>();Map<Integer,NativeFlatWallModel.Bake> bakes=new HashMap<>();int records=0;NativeFlatWallModel.Bake measured=null;NativeWallEntity.WallAppearance snapshot=null;int frameIndex=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var f=ModernFinishCatalog.ENTRIES.get(i).frame();if(f!=null&&f.border()&&f.rail()!=null){frameIndex=i;break;}}need(frameIndex>=0,"canonical frame available");
  try(var lines=Files.lines(Paths.get(System.getProperty("vandorlabs.diagonalModelReference")))){for(String line:(Iterable<String>)lines::iterator){JsonObject r=JsonParser.parseString(line).getAsJsonObject();key="record"+records+" mode"+r.get("mode")+" fill"+r.get("fill")+" profile"+r.get("profile")+" join"+r.get("joinChoice");int aspect=r.get("aspect").getAsInt(),mode=r.get("mode").getAsInt(),flip=r.get("flip").getAsInt();boolean frame=r.get("frame").getAsBoolean();int materialKey=aspect*2+(frame?1:0);var chosen=materials.get(materialKey);
   if(chosen==null){var atlas=r.getAsJsonArray("sprites");List<ProgrammableModel.MaterialSnapshot> edges=new ArrayList<>();for(int i=0;i<4;i++){var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal-edge"+i),new FrameSize(64,64),new NativeImage(64,64,false),ResourceMetadata.EMPTY);contents.add(c);edges.add(new ProgrammableModel.MaterialSnapshot(new Sprite(c,atlas.get(i+1).getAsJsonArray(),1,1,i),1,1));}
    var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal-main"+aspect),new FrameSize(32,32),new NativeImage(32,32,false),ResourceMetadata.EMPTY);contents.add(c);float usedU=aspect==2?.5F:1,usedV=aspect==1?.5F:1;var wall=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(c,atlas.get(0).getAsJsonArray(),usedU,usedV,-1),usedU,usedV));var plain=new ProgrammableModel.MaterialSnapshot(new Sprite(c,atlas.get(4).getAsJsonArray(),1,1,4),1,1);chosen=new NativePanelQuads.Material[]{wall,frame?new NativePanelQuads.Material(edges.getFirst(),edges):new NativePanelQuads.Material(plain)};materials.put(materialKey,chosen);
   }
   PanelCornerGeometry.Corner corner=null;if(r.has("corner")){var c=r.getAsJsonObject("corner");corner=new PanelCornerGeometry.Corner(option(c,"frontRight"),option(c,"backRight"),c.get("straightLeft").getAsBoolean(),c.get("straightRight").getAsBoolean());corner.frontFlat=nullable(c,"frontFlat");corner.backFlat=nullable(c,"backFlat");}
   double lower=nullable(r,"lower")==null?Double.NaN:16*nullable(r,"lower"),upper=nullable(r,"upper")==null?Double.NaN:16*nullable(r,"upper");var raw=r.getAsJsonArray("clip");double[] clip=new double[raw.size()];for(int i=0;i<clip.length;i++)clip[i]=16*raw.get(i).getAsDouble();boolean inverted=r.get("inverted").getAsBoolean();Direction facing=Direction.byName(r.get("facing").getAsString());var layout=new NativePanelLayout(1,1,3,2,(flip&1)!=0,(flip&2)!=0);
   var source=r.getAsJsonArray("vertices");put(records);put(source.size());for(var bits:source)put(bits.getAsInt());
   var bake=bakes.get(materialKey);if(bake==null){var selected=chosen;var surfaces=new HashMap<Integer,ProgrammableModel.MaterialSnapshot>();surfaces.put(0,chosen[0].surface());surfaces.put(frameIndex,chosen[1].surface());bake=new NativeFlatWallModel.Bake(Map.copyOf(surfaces),material->{String id=material.texture().toString();if(frame&&id.contains("frame-rails/")){int edge=id.endsWith("_edge_1")?1:id.endsWith("_edge_2")?2:id.endsWith("_edge_3")?3:0;return selected[1].edges().get(edge).sprite();}return selected[1].surface().sprite();});bakes.put(materialKey,bake);}
   var join=corner==null?null:new NativeDiagonalWallGeometry.Join(corner.frontRight,corner.backRight,corner.straightLeft,corner.straightRight,corner.frontFlat,corner.backFlat);for(int i=0;i<clip.length;i++)clip[i]/=16;
   var pose=new NativeDiagonalWallGeometry.Pose(facing,inverted,mode,r.get("fill").getAsInt(),join,lower/16,upper/16,r.get("hideLower").getAsBoolean(),r.get("hideUpper").getAsBoolean(),new NativeDiagonalWallGeometry.Clip(clip),layout);
   var geometry=NativeDiagonalWallGeometry.build(pose);int interior=frame?frameIndex:-1,textureLayout=r.get("textureLayout").getAsInt();
   var nativeQuads=bake.body(geometry,0,interior,textureLayout);compare(r.getAsJsonArray("vertices"),nativeQuads,facing,frame);need(nativeQuads==bake.body(geometry,0,interior,textureLayout),"shared body model cache hit");
   if(records==0){measured=bake;var base=new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,textureLayout,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new com.vandorlabs.persistence.StairState(8,0));snapshot=new NativeWallEntity.WallAppearance(base,new com.vandorlabs.persistence.WallSettings(mode==1,mode==2,r.get("fill").getAsInt(),0,false,0),new com.vandorlabs.persistence.FrameSettings(-1,interior,-2,0,-1,-1,false,false,false,false),com.vandorlabs.persistence.PanelGlassSettings.fresh(),geometry,NativePortholeLayout.unit(0),PanelPlayback.DEFAULT);need(measured.bodyProjection(snapshot)==nativeQuads,"fallback projection shares exact body mesh");}
   records++;
  }}finally{for(var c:contents)c.close();}
  for(int i=0;i<20000;i++)consumed=measured.bodyProjection(snapshot);var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<100000;i++)consumed=measured.bodyProjection(snapshot);long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes<=1024,"warm fallback projection allocation="+bytes);
  System.out.println("Native diagonal fallback projection PASS warmBytesPerCall="+(bytes/100000D));
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));need(hash.toString().equals("b3e2bf711c6399ff289cbd3516daf3c2f7ede8d541e80be5c0fab2c4d230a6ee"),"frozen actual Forge vertex identity "+hash);
  need(records==1824&&frameMask==15,"complete reference and all four frame slots");System.out.println("Native diagonal emitted-model PASS records="+records+" quads="+quads+" checks="+checks+" exactFloatXYZ/packedNormals/reversePayload/materialSlots; UV tolerance3e-6; no world/client");
 }
}
