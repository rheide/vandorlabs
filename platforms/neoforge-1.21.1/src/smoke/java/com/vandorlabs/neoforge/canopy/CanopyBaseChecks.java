package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyFace;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.nio.file.*;
import java.util.List;

/** Native authored base assembly is compared before the independently tested final drawing pipeline. */
public final class CanopyBaseChecks {
    private static long checks;
    private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static void faces(JsonArray expected,List<CanopyFace> actual,String key){
        need(expected.size()==actual.size(),"Source base face count "+key+" expected="+expected.size()+" actual="+actual.size());
        for(int i=0;i<actual.size();i++){var e=expected.get(i).getAsJsonObject();var f=actual.get(i);need(e.get("material").getAsString().equals(f.material)&&e.get("base").getAsString().equals(f.baseMaterial)&&e.get("group").getAsString().equals(f.group),"Source base material "+key);need(e.get("double").getAsBoolean()==f.doubleSided&&e.get("glazing").getAsBoolean()==f.glazing&&e.get("tiled").getAsBoolean()==f.tiled,"Source base flags "+key);var points=e.getAsJsonArray("points");need(points.size()==f.points()*5,"Source base point count");for(int p=0;p<f.points();p++){var v=f.vertex(p);double[] data={v.x,v.y,v.z,f.u(p),f.v(p)};for(int a=0;a<5;a++)need(points.get(p*5+a).getAsLong()==Double.doubleToRawLongBits(data[a]),"Source base double "+key+" face="+i+" point="+p+" axis="+a);}}
    }
    public static void main(String[] args)throws Exception {
        NativeCanopyModels models=NativeCanopyModels.builtin();need(models.modelCount()==41,"Native authored/generated kit count");int profiles=0;
        for(String input:args)try(var reader=Files.newBufferedReader(Path.of(input))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String key=row.get("key").getAsString(),kind=row.get("id").getAsString();String[] parts=key.split(":");int mask=parts.length==4?Integer.parseInt(parts[1]):0,roof=parts.length==4?Integer.parseInt(parts[2]):0;var settings=NativeCanopySettings.read(CanopySettingsChecks.tags(row.getAsJsonObject("settings")));int width=row.get("width").getAsInt();var base=models.base(kind,width,mask,roof,row.get("centered").getAsBoolean(),settings.data(),key.endsWith(":4")?2:key.endsWith(":5")?.5:1,choice->"vandorlabs:blocks/"+ModernFinishCatalog.option(choice).id());faces(row.getAsJsonArray("faces"),base,key);profiles++;}}
        need(profiles==552,"Frozen Source base coverage");System.out.println("[vandorlabs][canopy-base] PASS SourceBases="+profiles+" exactFields="+checks+" authored39/generated2/rails/roof/finishUV");
    }
}
