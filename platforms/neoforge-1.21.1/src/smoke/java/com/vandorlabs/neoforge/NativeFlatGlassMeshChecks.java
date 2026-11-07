package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.render.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Physical native records against actual released pane/frame/outline words; native reverse companions are checked separately. */
public final class NativeFlatGlassMeshChecks {
 private static final List<SpriteContents> CONTENTS=new ArrayList<>();private static int checks;
 private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents c,int x){this(c,512,512,x,64);}Sprite(SpriteContents c,int aw,int ah,int x,int y){super(TextureAtlas.LOCATION_BLOCKS,c,aw,ah,x,y);}}
 private static NativePanelQuads.Material material(String name,int slot){int h=slot==3?64:32;var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",name),new FrameSize(32,h),new NativeImage(32,h,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(c,32+slot*96),1,1));}
 private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Flat glass native "+text);}
 private static void compare(JsonObject row,List<BakedQuad> nativeQuads,boolean normalized,boolean white){var old=row.getAsJsonArray("vertices");need(nativeQuads.size()*16==old.size(),"paired physical vertex count "+row.get("kind")+" "+nativeQuads.size()+"/"+old.size());for(int q=0;q<nativeQuads.size();q+=2){int[] data=nativeQuads.get(q).getVertices(),reverse=nativeQuads.get(q+1).getVertices();for(int v=0;v<4;v++)for(int component:new int[]{0,1,2,3,4,5,7}){int expected=old.get((q/2*4+v)*8+component).getAsInt();if(component<3&&!normalized)expected=Float.floatToRawIntBits(Float.intBitsToFloat(expected)/16F);if(component==3&&white)expected=(expected&0xff000000)|0xffffff;need(data[v*8+component]==expected,"word kind="+row.get("kind")+" mask="+row.get("mask")+" front="+row.get("front")+" layout="+row.get("layout")+" q="+q+" v="+v+" c="+component+" actual="+Integer.toHexString(data[v*8+component])+" expected="+Integer.toHexString(expected));}for(int v=0;v<4;v++)for(int c:new int[]{0,1,2,3,4,5})need(data[v*8+c]==reverse[((4-v)&3)*8+c],"reverse companion preserves payload");}}
 public static void main(String[] args)throws Exception {Map<Integer,JsonObject> borders=new HashMap<>();for(String line:Files.readAllLines(Path.of(args[1]))){var row=JsonParser.parseString(line).getAsJsonObject();borders.put(row.get("choice").getAsInt(),row);}var glass=material("blocks/programmable_glass/low/soft_cyan",0);var rail=material("blocks/space_doors/medium/double_frame_metal",2);var inner=material("blocks/programmable_glass/metal_side",1);var custom=material("replacement",3);int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("kind").getAsString();int mask=row.get("mask").getAsInt(),front=row.get("front").getAsInt(),layout=row.get("layout").getAsInt();List<BakedQuad> quads;boolean normalized=false,white=false;
  switch(kind){case "pane"->{float opacity=GlassRenderPolicy.opacity(front,mask);quads=NativePanelQuads.flatGlassBuiltin(Direction.NORTH,0,new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(CONTENTS.getFirst(),32,32,0,0),1,1)),opacity);int first=layout==0?0:2;quads=quads.subList(first,first+2);normalized=true;white=true;}
   case "material-pane"->quads=NativePanelQuads.flatGlassReplacement(Direction.NORTH,0,custom,layout,new NativePanelLayout(mask,front,3,2,false,false),1,.5);
   case "selected-frame"->{var border=front<0?null:borders.get(front);need(front<0||border!=null,"actual source border choice");double[] widths=front<0?new double[]{1D/16,1D/16,1D/16,1D/16}:new com.google.gson.Gson().fromJson(border.getAsJsonArray("borders"),double[].class);int flags=(~mask)&15;for(int corner=0;corner<4;corner++){int side=corner&1,vertical=corner>>1;if((mask&(1<<side))!=0&&(mask&(1<<(2+vertical)))!=0&&(mask&(1<<(4+corner)))==0)flags|=16<<corner;}quads=NativePanelQuads.flatGlassSelectedFrame(Direction.NORTH,0,flags,front>=0,front>=0&&border.get("border").getAsBoolean(),widths,front<0?1:border.get("aspect").getAsDouble(),front<0?rail:custom,custom,custom,front<0?null:custom);}
   case "corner-pane","corner-frame"->{int a=mask/7,b=mask%7;var corner=new PanelCornerGeometry.FlatCorner(a==0?null:(double)((a-1)%3*6),b==0?null:(double)((b-1)%3*6),a==0?null:a>=4,b==0?null:b>=4,(layout&1)!=0,(layout&2)!=0);quads=kind.equals("corner-pane")?NativePanelQuads.flatGlassCornerPane(Direction.NORTH,front,corner,glass,0,NativePanelLayout.UNIT,1,1,false):NativePanelQuads.flatGlassCornerFrame(Direction.NORTH,front,corner,glass,rail,inner,custom);}
   default->throw new AssertionError("Unknown source role "+kind);
  }compare(row,quads,normalized,white);profiles++;}}
  finally{for(var contents:CONTENTS)contents.close();}need(profiles==2210,"actual profile coverage");System.out.println("[vandorlabs][flat-glass-mesh] PASS profiles="+profiles+" words="+checks+" sourcePhysicalFaces/nativeReverseCompanions/pane/mappedUV/allMasks/bentOutline");
 }
}
