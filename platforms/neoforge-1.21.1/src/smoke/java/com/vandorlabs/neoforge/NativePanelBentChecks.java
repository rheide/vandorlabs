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

/** Actual current center bend and immediate arm outputs, not a duplicate shape equation. */
public final class NativePanelBentChecks {
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents,int x){super(TextureAtlas.LOCATION_BLOCKS,contents,1024,128,x,0);}}
    private static TextureAtlasSprite sprite(String name,int canvas,int x){var contents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",name),new FrameSize(canvas,canvas),new NativeImage(canvas,canvas,false),ResourceMetadata.EMPTY);CONTENTS.add(contents);return new Sprite(contents,x);}
    private static int integer(JsonObject record,String field){return record.get(field).getAsInt();}
    private static Double nullable(JsonObject record,String field){return !record.has(field)||record.get(field).isJsonNull()?null:record.get(field).getAsDouble();}
    public static void main(String[] args)throws Exception{
        try{
            var wall=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bent_wall",16,0),1,1));var plain=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bent_interior",16,32),1,1));var outer=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bent_exterior",16,64),1,1));List<ProgrammableModel.MaterialSnapshot> edges=new ArrayList<>();for(int edge=0;edge<4;edge++)edges.add(new ProgrammableModel.MaterialSnapshot(sprite("box_rail"+edge,64,256+edge*64),1,1));var frame=new NativePanelQuads.Material(edges.getFirst(),edges);Map<Double,NativePanelQuads.Material> glass=new HashMap<>();for(double aspect:new double[]{.5,1,2}){int w=aspect==2?32:16,h=aspect==.5?32:16,canvas=Math.max(w,h);glass.put(aspect,new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite("bent_glass"+glass.size(),canvas,96),w/(float)canvas,h/(float)canvas)));}
            int records=0,poses=0;var mainLayout=new NativePanelLayout(1,1,3,2,false,false);var paneLayout=new NativePanelLayout(0,1,2,3,false,false);
            try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){JsonObject record=JsonParser.parseString(line).getAsJsonObject();int depth=integer(record,"depth"),tails=integer(record,"tails"),shape=integer(record,"shape"),mode=integer(record,"mode");Double front=nullable(record,"front"),back=nullable(record,"back");var interior=record.get("frame").getAsBoolean()?frame:plain;var aperture=new NativePortholeLayout(paneLayout,shape,0,false,true,true,true);
                for(Direction facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    var corner=new PanelCornerGeometry.FlatCorner(front,back,front==null?null:Boolean.FALSE,back==null?null:Boolean.TRUE,(tails&1)!=0,(tails&2)!=0);var geometry=NativeFlatWallGeometry.build(facing,depth,corner).withLayout(mainLayout);if(NativeFlatBarOwner.from(geometry)!=null)throw new AssertionError("Non-T reference accidentally contains T");
                    NativePanelApertureChecks.compare(record.getAsJsonArray("body"),NativePanelQuads.bentBody(geometry,aperture,wall,interior,outer,mode),facing,6,"bent"+records+" "+facing+" body");
                    NativePanelApertureChecks.compare(record.getAsJsonArray("pane"),NativePanelQuads.bentPane(geometry,aperture,glass.get(record.get("aspect").getAsDouble()),mode,paneLayout,2D/16,4D/16,1,0),facing,6,"bent"+records+" "+facing+" pane");poses+=2;
                }records++;
            }}
            if(records!=5832)throw new AssertionError("Incomplete L/cross reference "+records);System.out.println("[vandorlabs][panel-bent] CPU PASS legacyRecords="+records+" nativePoses="+poses+" checks="+NativePanelApertureChecks.checksCount()+" current center X deformation, real arm full-face UV, caps and independent panes");
        }finally{for(var contents:CONTENTS)contents.close();}
    }
}
