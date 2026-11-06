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
public final class NativeJoinedDiagonalPortholeChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents c,int x){super(TextureAtlas.LOCATION_BLOCKS,c,32,16,x,0);}}
    private static final List<SpriteContents> CONTENTS=new ArrayList<>();
    private static NativePanelQuads.Material material(String id,int x){var c=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs",id),new FrameSize(16,16),new NativeImage(16,16,false),ResourceMetadata.EMPTY);CONTENTS.add(c);return new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(new Sprite(c,x),1,1));}
    private static int get(JsonObject r,String key){return r.get(key).getAsInt();}
    private static double number(JsonObject r,String key){return !r.has(key)||r.get(key).isJsonNull()?Double.NaN:r.get(key).getAsDouble();}
    private static Boolean optional(JsonObject r,String key){return r.has(key)?r.get(key).getAsBoolean():null;}
    private static Double flat(JsonObject r,String key){return r.has(key)?r.get(key).getAsDouble():null;}
    public static void main(String[] args)throws Exception{
        try{var wall=material("joined_diagonal",0);var metal=material("joined_inner",16);var surfaces=new HashMap<Integer,ProgrammableModel.MaterialSnapshot>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)surfaces.put(i,wall.surface());
            var bake=new NativeFlatWallModel.Bake(Map.copyOf(surfaces),ignored->metal.surface().sprite());
            var base=new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new com.vandorlabs.persistence.StairState(8,0));
            var frames=new com.vandorlabs.persistence.FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false);
            int records=0,poses=0,solids=0,arms=0;
            try(var lines=Files.lines(Path.of(args[0]))){for(var line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();int mode=get(r,"mode"),layout=get(r,"layout");boolean inverted=r.get("inverted").getAsBoolean(),solid=r.get("solid").getAsBoolean();NativeDiagonalWallGeometry.Join join=null;
                if(r.has("corner")&&!r.get("corner").isJsonNull()){var c=r.getAsJsonObject("corner");join=new NativeDiagonalWallGeometry.Join(optional(c,"frontRight"),optional(c,"backRight"),c.get("straightLeft").getAsBoolean(),c.get("straightRight").getAsBoolean(),flat(c,"frontFlat"),flat(c,"backFlat"));arms++;}
                double[] bounds=new double[r.getAsJsonArray("clip").size()],clip=bounds.clone();for(int i=0;i<bounds.length;i++){bounds[i]=r.getAsJsonArray("clip").get(i).getAsDouble();clip[i]=bounds[i]/16;}
                double lower=number(r,"lower"),upper=number(r,"upper");var opening=NativePanelSlice.get(NativePortholeLayout.unit(get(r,"shape")));boolean left=r.get("left").getAsBoolean(),right=r.get("right").getAsBoolean(),down=r.get("lowerContact").getAsBoolean(),up=r.get("upperContact").getAsBoolean();
                for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                    var pose=new NativeDiagonalWallGeometry.Pose(face,inverted,mode,get(r,"fill"),join,lower,upper,r.get("hideLower").getAsBoolean(),r.get("hideUpper").getAsBoolean(),new NativeDiagonalWallGeometry.Clip(clip),NativePanelLayout.UNIT);var geometry=NativeDiagonalWallGeometry.build(pose);
                    var unit=NativePortholeLayout.unit(get(r,"shape"));var ap=new NativePortholeLayout(unit.pane(),unit.shape(),0,left,right,down,up,solid);
                    var walls=new com.vandorlabs.persistence.WallSettings(mode!=0,mode==2,get(r,"fill"),0,true,get(r,"shape"));
                    var glass=new com.vandorlabs.persistence.PanelGlassSettings(2,0,layout,false,3,true,true);
                    var settings=new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,layout,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,base.stairs());
                    var snapshot=new NativeWallEntity.WallAppearance(settings,walls,frames,glass,geometry,ap,PanelPlayback.DEFAULT,false,NativePanelKind.PORTHOLE);
                    var body=bake.aperture(snapshot,geometry);
                    if(body!=bake.aperture(snapshot,geometry)||body!=bake.bodyProjection(snapshot))throw new AssertionError("Production aperture/fallback cache routing");
                    NativePanelApertureChecks.compare(r.getAsJsonArray("body"),body,face,6,"joined"+records+" "+face+" body");
                    var pane=bake.pane(snapshot,geometry,false);NativePanelApertureChecks.compare(r.getAsJsonArray("pane"),pane,face,6,"joined"+records+" "+face+" pane");poses+=2;
                }records++;if(solid)solids++;
            }}if(records!=1728||arms==0||solids==0)throw new AssertionError("Incomplete complete-path reference "+records+" arms="+arms+" solid="+solids);System.out.println("[vandorlabs][joined-diagonal-porthole] CPU PASS records="+records+" nativePoses="+poses+" solid="+solids+" corner="+arms+" checks="+NativePanelApertureChecks.checksCount()+" actual complete Forge drawConfiguredWall body/pane; no client/world");
        }finally{for(var c:CONTENTS)c.close();}
    }
}
