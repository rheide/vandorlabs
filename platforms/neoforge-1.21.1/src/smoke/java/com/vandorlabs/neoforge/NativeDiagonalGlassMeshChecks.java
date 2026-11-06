package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** Actual Forge opaque outline/cap and transparent solid-body output comparison. */
public final class NativeDiagonalGlassMeshChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents c,int x){this(c,64,16,x,0);}Sprite(SpriteContents c,int aw,int ah,int x,int y){super(TextureAtlas.LOCATION_BLOCKS,c,aw,ah,x,y);}}
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static NativePanelQuads.Material material(String id,int x){var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",id),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(c,x),1,1));}
    private static ProgrammableModel.MaterialSnapshot edgeSprite(String id,int width,int height,int x,int y){var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",id),new FrameSize(width,height),new NativeImage(width,height,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new ProgrammableModel.MaterialSnapshot(new Sprite(c,1024,128,x,y),1,1);}
    private static NativePanelQuads.Material edges(String name,int role){List<ProgrammableModel.MaterialSnapshot> values=new ArrayList<>();for(int side=0;side<4;side++)values.add(edgeSprite(name+(side==0?"":"_edge_"+side),64,64,128+role*256+side*64,64));return new NativePanelQuads.Material(values.getFirst(),values);}
    private static void edgeChecks(Path input)throws Exception{
        var wideTexture=edgeSprite("glass_edge_wide",32,32,0,0);var portraitTexture=edgeSprite("glass_edge_portrait",32,32,0,0);var wide=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(wideTexture.sprite(),1,.5F));var portrait=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(portraitTexture.sprite(),.5F,1));var plainInner=new NativePanelQuads.Material(edgeSprite("glass_edge_inner_plain",16,16,32,0));var plainOuter=new NativePanelQuads.Material(edgeSprite("glass_edge_outer_plain",16,16,64,0));var originalFront=new NativePanelQuads.Material(edgeSprite("housing/full_original",16,16,96,0),List.of(),true);var rail=edges("glass_reference_rail",0);var inner=edges("glass_reference_inner",1);var outer=edges("glass_reference_outer",2);int records=0;
        try(var lines=Files.lines(input)){for(var line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();int mode=get(r,"mode"),corner=get(r,"corner"),mask=get(r,"mask"),finishes=get(r,"finishes");boolean inverted=r.get("inverted").getAsBoolean();var pane=corner==0?wide:portrait;var join=new NativeDiagonalWallGeometry.Join(true,corner==0?false:true,true,corner!=0,null,null);double lower=corner==0?Double.NaN:.25,upper=corner==0?Double.NaN:.75;
            for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){var pose=new NativeDiagonalWallGeometry.Pose(face,inverted,mode,3,join,lower,upper,false,false,NativeDiagonalWallGeometry.Clip.NONE,NativePanelLayout.UNIT);NativePanelApertureChecks.compare(r.getAsJsonArray("frame"),NativePanelQuads.diagonalGlassFrame(pose,pane,finishes==3?originalFront:finishes==2?plainOuter:rail,finishes==1?plainInner:inner,finishes==1?plainOuter:outer,mask),face,6,"directional glass "+records+" "+face);}records++;
        }}if(records!=96)throw new AssertionError("Incomplete directional frame reference "+records);System.out.println("[vandorlabs][diagonal-glass-edges] CPU PASS records="+records+" poses="+(records*4)+" actual four authored edge UV selections, mixed opaque caps padded wide/portrait panes and retained original frame classification");
    }
    private static int get(JsonObject r,String key){return r.get(key).getAsInt();}
    public static void main(String[] args)throws Exception{
        try{var glass=material("frame_pane",0);var rail=material("frame_rail",16);var authored=material("reference/frames/authored",16);var inner=material("frame_inner",32);var outer=material("frame_outer",48);var surfaces=new HashMap<Integer,ProgrammableModel.MaterialSnapshot>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)surfaces.put(i,glass.surface());surfaces.put(1,inner.surface());surfaces.put(2,outer.surface());
            int authoredChoice=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var frame=ModernFinishCatalog.ENTRIES.get(i).frame();if(frame!=null&&!frame.border()&&frame.authoredRail()){authoredChoice=i;break;}}if(authoredChoice<0)throw new AssertionError("Actual renamed original frame provenance missing");surfaces.put(authoredChoice,authored.surface());
            var bake=new NativeFlatWallModel.Bake(Map.copyOf(surfaces),texture->texture.texture().getPath().endsWith("double_frame_metal")?rail.surface().sprite():inner.surface().sprite());
            int records=0,poses=0;Boolean[][] corners={{null,null},{true,null},{false,null},{true,true},{true,false},{false,true}};
            try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();int mode=get(r,"mode"),ci=get(r,"corner"),end=get(r,"end"),mask=get(r,"mask"),layout=get(r,"layout"),flip=get(r,"flip");boolean inverted=r.get("inverted").getAsBoolean();var join=new NativeDiagonalWallGeometry.Join(corners[ci][0],corners[ci][1],(ci&1)!=0,(ci&2)!=0,null,null);var group=new NativePanelLayout(1,1,3,2,(flip&1)!=0,(flip&2)!=0);double lower=end==0?Double.NaN:.25,upper=end==0?Double.NaN:.75;
                for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    var pose=new NativeDiagonalWallGeometry.Pose(face,inverted,mode,get(r,"fill"),join,lower,upper,false,false,NativeDiagonalWallGeometry.Clip.NONE,NativePanelLayout.UNIT);
                    var geometry=NativeDiagonalWallGeometry.build(pose);var base=new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new com.vandorlabs.persistence.StairState(8,0));
                    var frames=new com.vandorlabs.persistence.FrameSettings(r.get("authored").getAsBoolean()?authoredChoice:-1,1,2,0,-1,-1,false,false,false,false);
                    var paneSettings=new com.vandorlabs.persistence.PanelGlassSettings(0,0,layout,false,3,true,true);var wallSettings=new com.vandorlabs.persistence.WallSettings(mode!=0,mode==2,get(r,"fill"),0,false,0);
                    var snapshot=new NativeWallEntity.WallAppearance(base,wallSettings,frames,paneSettings,geometry,NativePortholeLayout.unit(0),PanelPlayback.DEFAULT,false,NativePanelKind.GLASS,new NativeDiagonalGlassLayout(group,mask,mode==2?2:1));
                    var body=bake.glassPane(snapshot,false);var frame=bake.glassFrame(snapshot);
                    if(body!=bake.glassPane(snapshot,false)||frame!=bake.bodyProjection(snapshot))throw new AssertionError("Production glass frame/pane cache routing");
                    NativePanelApertureChecks.compare(r.getAsJsonArray("body"),body,face,6,"glass"+records+" "+face+" body");NativePanelApertureChecks.compare(r.getAsJsonArray("frame"),frame,face,6,"glass"+records+" "+face+" frame");poses+=2;
                    if(records==0&&face==Direction.NORTH){var faded=NativePanelQuads.diagonalGlassBody(pose,glass,layout,group,mask,.25F,0);for(var quad:faded)for(int v=0;v<4;v++)if(quad.getVertices()[v*8+3]!=0x3FFFFFFF)throw new AssertionError("Custom pane alpha must be retained in immutable baked records");}
                }records++;
            }}if(records!=2304)throw new AssertionError("Incomplete diagonal glass reference "+records);System.out.println("[vandorlabs][diagonal-glass] CPU PASS records="+records+" nativePoses="+poses+" checks="+NativePanelApertureChecks.checksCount()+" actual Forge geometry/normal/winding/atlasUV, opaque rails/caps and pane alpha; no client/world");
            edgeChecks(Path.of(args[0]+".edges.jsonl"));
        }finally{for(var c:CONTENTS)c.close();}
    }
}
