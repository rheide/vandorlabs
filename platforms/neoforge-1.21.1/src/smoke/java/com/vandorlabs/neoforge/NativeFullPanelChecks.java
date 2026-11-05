package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.surface.WindowOutline;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Actual Forge local records under the full block renderer's declared translate/scale matrix. */
public final class NativeFullPanelChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents,int x){super(TextureAtlas.LOCATION_BLOCKS,contents,32,16,x,0);}}
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static NativePanelQuads.Material material(String name,int x){var contents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",name),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);CONTENTS.add(contents);return new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(contents,x),1,1));}
    private static JsonArray fullPose(JsonArray source){JsonArray result=source.deepCopy();var matrix=new org.joml.Matrix4f().translate(0,0,-24).scale(1,1,4);var point=new org.joml.Vector3f();for(int at=0;at<source.size();at+=8){matrix.transformPosition(source.get(at).getAsFloat(),source.get(at+1).getAsFloat(),source.get(at+2).getAsFloat(),point);result.set(at,new JsonPrimitive(point.x));result.set(at+1,new JsonPrimitive(point.y));result.set(at+2,new JsonPrimitive(point.z));float x=source.get(at+5).getAsFloat(),y=source.get(at+6).getAsFloat(),z=source.get(at+7).getAsFloat();if(z!=0&&(x!=0||y!=0))throw new AssertionError("Full flat source has an unexpected mixed-Z normal");}return result;}
    private static int get(JsonObject record,String field){return record.get(field).getAsInt();}
    public static void main(String[] args)throws Exception{
        try{
            var wall=material("full_wall",0);var metal=material("full_metal",16);int records=0,poses=0;
            try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){var record=JsonParser.parseString(line).getAsJsonObject();if(get(record,"depthUv")!=16||get(record,"border")!=1)continue;int cols=get(record,"columns"),rows=get(record,"rows"),col=get(record,"column"),row=get(record,"row"),shape=get(record,"shape"),mode=get(record,"mode"),flips=get(record,"flips");var group=new NativePanelLayout(col,row,cols,rows,(flips&1)!=0,(flips&2)!=0);var opening=new NativePanelSlice(WindowOutline.localSlice(cols,rows,shape,1,col,row));var body=fullPose(record.getAsJsonArray("body"));var pane=fullPose(record.getAsJsonArray("pane"));
                for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    NativePanelApertureChecks.compare(body,NativePanelQuads.aperture(face,0,4,opening,wall,metal,metal,mode,group,col>0,col<cols-1,row>0,row<rows-1,16),face,6,"full"+records+" "+face+" body");
                    var target=NativePanelQuads.pane(face,0,4,opening,wall,mode,group,3D/16,5D/16,1,0);NativePanelApertureChecks.compare(pane,target,face,6,"full"+records+" "+face+" pane");poses+=2;
                    for(var quad:target)for(int v=0;v<4;v++){int[] data=quad.getVertices();double x=Float.intBitsToFloat(data[v*8]),z=Float.intBitsToFloat(data[v*8+2]),normal=face==Direction.NORTH?z:face==Direction.SOUTH?1-z:face==Direction.WEST?x:1-x;if(Math.abs(normal-.25)>1e-6&&Math.abs(normal-.75)>1e-6)throw new AssertionError("Full pane not at native quarter depths "+normal);}
                }records++;
            }}
            if(records!=1728)throw new AssertionError("Incomplete full-depth source profiles "+records);System.out.println("[vandorlabs][full-panel] CPU PASS legacyRecords="+records+" nativePoses="+poses+" checks="+NativePanelApertureChecks.checksCount()+" actual Forge UV/normal records, exact declared GL matrix and native quarter-depth panes");
        }finally{for(var contents:CONTENTS)contents.close();}
    }
}
