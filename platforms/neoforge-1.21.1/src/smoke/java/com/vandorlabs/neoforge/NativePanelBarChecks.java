package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.PanelCornerGeometry;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Actual center-owner source records, independently composed by the Forge renderer paths. */
public final class NativePanelBarChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents,int w,int h,int x){super(TextureAtlas.LOCATION_BLOCKS,contents,w,h,x,0);}}
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static TextureAtlasSprite sprite(String name,int canvas,int aw,int ah,int x){var contents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",name),new FrameSize(canvas,canvas),new NativeImage(canvas,canvas,false),ResourceMetadata.EMPTY);CONTENTS.add(contents);return new Sprite(contents,aw,ah,x);}
    private static int integer(JsonObject object,String key){return object.get(key).getAsInt();}
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        try{
            var wall=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bar_wall",16,48,16,0),1,1));var plain=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bar_interior",16,48,16,16),1,1));var exterior=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bar_exterior",16,48,16,32),1,1));List<ProgrammableModel.MaterialSnapshot> edges=new ArrayList<>();for(int edge=0;edge<4;edge++)edges.add(new ProgrammableModel.MaterialSnapshot(sprite("bar_rail"+edge,64,256,64,edge*64),1,1));var frame=new NativePanelQuads.Material(edges.getFirst(),edges);
            Map<Double,NativePanelQuads.Material> glass=new HashMap<>();for(double aspect:new double[]{.5,1,2}){int w=aspect==2?32:16,h=aspect==.5?32:16,canvas=Math.max(w,h);glass.put(aspect,new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bar_glass"+glass.size(),canvas,96,32,0),w/(float)canvas,h/(float)canvas)));}
            int records=0,poses=0;try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){JsonObject record=JsonParser.parseString(line).getAsJsonObject();int depth=integer(record,"depth"),columns=integer(record,"columns"),rows=integer(record,"rows"),column=integer(record,"column"),row=integer(record,"row"),shape=integer(record,"shape"),profile=integer(record,"paneProfile"),mode=integer(record,"mode");double barNear=record.get("barNear").getAsDouble(),aspect=record.get("aspect").getAsDouble();boolean left=record.get("left").getAsBoolean(),porthole=record.get("porthole").getAsBoolean();var metal=record.get("frame").getAsBoolean()?frame:plain;var uv=new NativePanelLayout(column,row,columns,rows,false,false);var aperture=porthole?new NativePortholeLayout(uv,shape,0,column>0,column<columns-1,row>0,row<rows-1):null;
                for(Direction facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    var geometry=NativeFlatWallGeometry.build(facing,depth,new PanelCornerGeometry.FlatCorner(barNear,barNear,false,true,left,!left));var owner=NativeFlatBarOwner.from(geometry);need(owner!=null&&owner.leftTail()==left&&owner.barNear()==barNear,"exact T owner from immutable geometry");
                    var body=NativePanelQuads.barBody(owner,geometry,aperture,wall,metal,exterior);NativePanelApertureChecks.compare(record.getAsJsonArray("body"),body,facing,6,"Trecord"+records+" "+facing+" body");poses++;
                    if(porthole){var pane=NativePanelQuads.barPane(owner,aperture,glass.get(aspect),mode,profile==0?NativePanelLayout.UNIT:uv,profile==0?0:2D/16,profile==0?0:4D/16,1,0);NativePanelApertureChecks.compare(record.getAsJsonArray("pane"),pane,facing,6,"Trecord"+records+" "+facing+" pane");poses++;}
                }records++;
            }}
            need(records==2916,"complete actual T profiles");var straight=NativeFlatWallGeometry.straight(Direction.NORTH,0);need(NativeFlatBarOwner.from(straight)==null,"straight panel remains ordinary path");for(var join:List.of(new NativeFlatWallGeometry.Join(6D,null,false,null,true,false),new NativeFlatWallGeometry.Join(6D,6D,false,true,false,false),new NativeFlatWallGeometry.Join(6D,6D,false,true,true,true),new NativeFlatWallGeometry.Join(0D,12D,false,true,true,false),new NativeFlatWallGeometry.Join(Double.NaN,Double.NaN,false,true,true,false))){var invalid=new NativeFlatWallGeometry.Geometry(straight.boxes(),straight.bounds(),straight.facing(),straight.depth(),join);need(NativeFlatBarOwner.from(invalid)==null,"L/cross/unaligned/nonfinite geometry is not a T owner");}
            System.out.println("[vandorlabs][panel-T-owner] CPU PASS legacyRecords="+records+" nativePoses="+poses+" checks="+NativePanelApertureChecks.checksCount()+" exact center material, raw body UV, cap closure and independent transformed panes; no world");
        }finally{for(var content:CONTENTS)content.close();}
    }
}
