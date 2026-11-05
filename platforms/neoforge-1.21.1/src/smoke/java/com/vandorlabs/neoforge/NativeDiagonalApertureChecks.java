package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Independent actual Forge vertex/atlas comparison for the sloped aperture adapter. */
public final class NativeDiagonalApertureChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents c,int x){super(TextureAtlas.LOCATION_BLOCKS,c,32,16,x,0);}}
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static NativePanelQuads.Material material(String id,int x){var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",id),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(c,x),1,1));}
    private static int get(JsonObject r,String key){return r.get(key).getAsInt();}
    public static void main(String[] args)throws Exception{
        try{var wall=material("diagonal_aperture",0);var metal=material("diagonal_inner",16);int records=0,poses=0;
            try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();int profile=get(r,"profile"),flips=get(r,"flips"),mode=get(r,"mode");var group=new NativePanelLayout(0,0,2,3,(flips&1)!=0,(flips&2)!=0);var descriptor=new NativePortholeLayout(group,get(r,"shape"),0,false,true,false,true);var opening=NativePanelSlice.get(descriptor);var corner=profile==2?new PanelCornerGeometry.Corner(true,false,true,false):null;var projection=new DiagonalPortholeProjection(get(r,"slope"),get(r,"inverted")!=0,profile==1?4:Double.NaN,profile==1?12:Double.NaN,profile==2?new double[]{0,1,0,16,15,16}:null,corner);
                for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    NativePanelApertureChecks.compare(r.getAsJsonArray("body"),NativePanelQuads.diagonalAperture(face,projection,opening,wall,metal,metal,mode,group,false,true,false,true,4),face,6,"diagonal"+records+" "+face+" body");
                    NativePanelApertureChecks.compare(r.getAsJsonArray("pane"),NativePanelQuads.diagonalPane(face,projection,opening,wall,mode,group,2D/16,4D/16,1,0),face,6,"diagonal"+records+" "+face+" pane");poses+=2;
                }records++;
            }}if(records!=864)throw new AssertionError("Incomplete sloped reference "+records);System.out.println("[vandorlabs][diagonal-aperture] CPU PASS records="+records+" nativePoses="+poses+" checks="+NativePanelApertureChecks.checksCount()+" actual Forge geometry/normal/winding/atlasUV; no client/world");
        }finally{for(var c:CONTENTS)c.close();}
    }
}
