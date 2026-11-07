package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.persistence.CanopySettingsData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;

/** Builtin authored geometry is common/server-safe; atlas and texture-pack state stay in the client bake. */
public final class NativeCanopyModels implements CanopyShapes.Models {
    public record Dimensions(int width,int length,int height){}
    private static final NativeCanopyModels BUILTIN=load();
    private final Map<String,List<CanopyFace>> models;
    private final Map<String,Dimensions> dimensions;
    private final List<CanopyFace> centeredVisor;
    private NativeCanopyModels(Map<String,List<CanopyFace>> models,Map<String,Dimensions> dimensions) {
        this.models=Collections.unmodifiableMap(new LinkedHashMap<>(models));this.dimensions=Map.copyOf(dimensions);
        centeredVisor=CanopyShapes.shift(model("space_visor_2x1"),.5,0);
    }
    public static NativeCanopyModels builtin(){return BUILTIN;}
    public List<CanopyFace> model(String name){List<CanopyFace> result=models.get(name);if(result==null)throw new IllegalArgumentException("Unknown authored canopy "+name);return result;}
    public Dimensions dimensions(String kind){Dimensions value=dimensions.get(kind.equals("regular_canopy_glass")?"regular_1x1_00":kind);if(value==null)throw new IllegalArgumentException("Unknown canopy dimensions "+kind);return value;}
    public int modelCount(){return models.size();}
    public List<CanopyFace> geometry(String kind,int width,int mask,int roof,boolean centered,CanopySettingsData settings) {
        if(CanopySettingsData.supports(kind))return CanopyShapes.front(this,width,settings,centered);
        if(kind.equals("canopy")||kind.equals("long_canopy")){int depth=dimensions(kind).length();return CanopyShapes.opening(this,width,depth,Math.min(depth==3?3:2,settings.angle),(mask&8)!=0);}
        if(kind.equals("regular_canopy_glass"))return CanopyShapes.fixed(this,mask,roof);
        if(kind.startsWith("angled_")){mask&=11;return model(mask==0?kind:String.format(Locale.ROOT,"%s_%02x",kind,mask));}
        return centered&&kind.equals("space_visor_2x1")?centeredVisor:model(kind);
    }
    public List<CanopyFace> base(String kind,int width,int mask,int roof,boolean centered,CanopySettingsData settings,double aspect,IntFunction<String> material) {
        return CanopyShapes.finish(geometry(kind,width,mask,roof,centered,settings),settings,aspect,material);
    }
    private static NativeCanopyModels load() {
        try(InputStream in=NativeCanopyModels.class.getResourceAsStream("/assets/vandorlabs/data/canopy_meshes.json")) {
            if(in==null)throw new IllegalStateException("Missing native authored canopy data");
            JsonArray records=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String,List<CanopyFace>> models=new LinkedHashMap<>();Map<String,Dimensions> dimensions=new HashMap<>();
            for(JsonElement raw:records){JsonObject record=raw.getAsJsonObject();List<CanopyFace> faces=new ArrayList<>();double top=0;
                for(JsonElement faceRaw:record.getAsJsonArray("faces")){JsonObject face=faceRaw.getAsJsonObject();JsonArray v=face.getAsJsonArray("v"),texture=face.getAsJsonArray("uv");CanopyPoint[] points=new CanopyPoint[v.size()];double[][] uv=new double[v.size()][2];
                    for(int i=0;i<points.length;i++){JsonArray p=v.get(i).getAsJsonArray(),u=texture.get(i).getAsJsonArray();points[i]=new CanopyPoint(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());uv[i][0]=u.get(0).getAsDouble();uv[i][1]=u.get(1).getAsDouble();top=Math.max(top,points[i].y);}
                    String material=face.get("material").getAsString();faces.add(new CanopyFace(points,uv,material,face.has("base_material")?face.get("base_material").getAsString():material,face.get("group").getAsString(),face.get("double").getAsBoolean(),false,false));
                }
                String id=record.get("id").getAsString();JsonArray footprint=record.getAsJsonArray("footprint");models.put(id,List.copyOf(faces));dimensions.put(id,new Dimensions(Math.max(1,(int)Math.ceil(footprint.get(0).getAsDouble())),Math.max(1,(int)Math.ceil(footprint.get(1).getAsDouble())),Math.max(1,(int)Math.ceil(top))));
            }
            CanopyShapes.Models source=name->Objects.requireNonNull(models.get(name),"Missing raw canopy model "+name);
            dimensions.put("long_canopy",new Dimensions(1,3,1));models.put("long_canopy",CanopyShapes.opening(source,1,3,1,false));
            dimensions.put("large_front_canopy_glass",new Dimensions(5,1,5));models.put("large_front_canopy_glass",CanopyShapes.front(source,5,CanopySettingsData.fresh(),false));
            for(String id:new String[]{"space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}){Dimensions size=dimensions.get(id);models.put(id,CanopyShapes.visorRails(models.get(id),size.width(),size.height()));}
            if(models.size()!=41)throw new IllegalStateException("Native canopy model coverage "+models.size());
            return new NativeCanopyModels(models,dimensions);
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
}
