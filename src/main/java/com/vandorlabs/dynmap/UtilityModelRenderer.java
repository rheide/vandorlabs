package com.vandorlabs.dynmap;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;

/** Native programmer/coupler/track faces, preserving authored UVs through per-cell clipping. */
public final class UtilityModelRenderer extends CustomRenderer {
    private static final JsonObject DATA=load();
    private static JsonObject load(){
        try(InputStream stream=UtilityModelRenderer.class.getResourceAsStream("/assets/vandorlabs/data/dynmap_utility_models.json")){
            if(stream==null)throw new IOException("Missing utility map geometry");return new JsonParser().parse(new InputStreamReader(stream,"UTF-8")).getAsJsonObject();
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"TrackStyle"};}
    @Override protected int getMaximumTextureCount(){return DATA.getAsJsonArray("textures").size();}
    private static String property(DynmapBlockState state,String name,String fallback,String... values){for(String value:values)if(state.isStateMatch(name,value))return value;return fallback;}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        DynmapBlockState state=context.getBlockType();String id=state.blockName.substring(state.blockName.indexOf(':')+1);
        if(id.equals("compact_track")||id.equals("armoured_track")||id.equals("bogie_track")){Object style=context.getBlockTileEntityField("TrackStyle");if(style instanceof Number){int choice=((Number)style).intValue();if(choice>=0&&choice<3)id=new String[]{"compact_track","armoured_track","bogie_track"}[choice];}}
        String facing=property(state,"facing","north","north","east","south","west","up","down"),key=id+"/"+facing;
        if(id.equals("data_chip_programmer"))key+="/"+property(state,"part","0","0","1","2","3");
        else if(id.equals("square_coupler")||id.equals("vehicle_coupler_slab")){key+="/"+property(state,"locked","false","false","true");if(id.equals("vehicle_coupler_slab"))key+="/"+property(state,"half","bottom","bottom","top");}
        JsonArray faces=DATA.getAsJsonObject("states").getAsJsonArray(key);if(faces==null)return new RenderPatch[0];
        List<RenderPatch> result=new ArrayList<>();
        for(JsonElement element:faces){JsonArray face=element.getAsJsonArray();int texture=face.get(0).getAsInt();List<MapSurface.Vertex> polygon=new ArrayList<>();
            for(int i=1;i<face.size();i++){JsonArray v=face.get(i).getAsJsonArray();polygon.add(new MapSurface.Vertex(new Vec3d(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble()),v.get(3).getAsDouble(),v.get(4).getAsDouble()));}
            MapSurface.emit(context,result,polygon,texture);
        }
        return result.toArray(new RenderPatch[0]);
    }
}
