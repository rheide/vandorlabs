package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.NativePanelLayout;
import com.vandorlabs.surface.WindowOutline;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Independent target-record comparison with actual legacy aperture and glass mapping output. */
public final class NativePanelApertureChecks {
    private static long checks,poses;
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents,int x){super(TextureAtlas.LOCATION_BLOCKS,contents,32,16,x,0);}}
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int integer(JsonObject record,String field){return record.get(field).getAsInt();}
    private static void compare(JsonArray source,List<BakedQuad> nativeQuads,Direction facing,double near,String key){
        need(source.size()%32==0&&nativeQuads.size()==source.size()/16,key+" complete doubled quad count "+source.size()+"/"+nativeQuads.size());int turns=facing==Direction.WEST?1:facing==Direction.SOUTH?2:facing==Direction.EAST?3:0;
        for(int q=0;q<source.size()/32;q++){
            int[] data=nativeQuads.get(q*2).getVertices(),back=nativeQuads.get(q*2+1).getVertices();for(int v=0;v<4;v++){
                int p=q*32+v*8,at=v*8;double x=source.get(p).getAsFloat()/16D,y=source.get(p+1).getAsFloat()/16D,z=(source.get(p+2).getAsFloat()+near-6)/16D;
                double expectedX=turns==1?z:turns==2?1-x:turns==3?1-z:x,expectedZ=turns==1?1-x:turns==2?1-z:turns==3?x:z;
                need(Math.abs(Float.intBitsToFloat(data[at])-expectedX)<1e-6&&Math.abs(Float.intBitsToFloat(data[at+1])-y)<1e-6&&Math.abs(Float.intBitsToFloat(data[at+2])-expectedZ)<1e-6,key+" XYZ q"+q+" v"+v);
                for(int axis=0;axis<2;axis++)need(Math.abs(Float.intBitsToFloat(data[at+4+axis])-source.get(p+3+axis).getAsFloat())<3e-6,key+" atlas UV q"+q+" v"+v+" axis"+axis+" expected"+source.get(p+3+axis)+" actual"+Float.intBitsToFloat(data[at+4+axis]));
                int a=(byte)(int)(source.get(p+5).getAsFloat()*127),b=(byte)(int)(source.get(p+6).getAsFloat()*127),c=(byte)(int)(source.get(p+7).getAsFloat()*127),nx=turns==1?c:turns==2?-a:turns==3?-c:a,nz=turns==1?-a:turns==2?-c:turns==3?a:c;
                need(data[at+7]==((nx&255)|((b&255)<<8)|((nz&255)<<16)),key+" packed normal");for(int n=0;n<8;n++)need(data[((4-v)&3)*8+n]==back[at+n],key+" reverse payload");
            }
            need(!nativeQuads.get(q*2).isShade()&&!nativeQuads.get(q*2).hasAmbientOcclusion(),key+" legacy unshaded material");
        }poses++;
    }
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]);var wallContents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","aperture-reference"),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);var metalContents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","aperture-metal"),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);
        try{
            var wall=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(wallContents,0),1,1));var metal=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(metalContents,16),1,1));int records=0;
            try(var lines=Files.lines(input)){for(var line:(Iterable<String>)lines::iterator){JsonObject record=JsonParser.parseString(line).getAsJsonObject();int columns=integer(record,"columns"),rows=integer(record,"rows"),column=integer(record,"column"),row=integer(record,"row"),border=integer(record,"border"),mode=integer(record,"mode"),flips=integer(record,"flips");var slice=new WindowOutline(columns,rows,integer(record,"shape"),border).slice(column,row);var layout=new NativePanelLayout(column,row,columns,rows,(flips&1)!=0,(flips&2)!=0);
                for(Direction facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(double near:new double[]{0,6,12}){
                    String key="record"+records+" "+facing+" near"+near+" mode"+mode;compare(record.getAsJsonArray("body"),NativePanelQuads.aperture(facing,near,slice,wall,metal,metal,mode,layout,column>0,column<columns-1,row>0,row<rows-1),facing,near,key+" body");compare(record.getAsJsonArray("pane"),NativePanelQuads.pane(facing,near,slice,wall,mode,layout,(2+border)/16D,(4+border)/16D,1),facing,near,key+" pane");
                }records++;
            }}
            need(records==3456,"complete actual legacy reference profiles");System.out.println("[vandorlabs][panel-aperture] CPU PASS legacyRecords="+records+" nativePoses="+poses+" checks="+checks+" raw geometry/normal/winding and atlasUV tolerance3e-6; no client/world");
        }finally{wallContents.close();metalContents.close();}
    }
}
