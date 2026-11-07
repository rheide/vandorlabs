package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** New geometry and format-independent emission replay both independent released renderer corpora. */
public final class CanopyDrawingChecks {
    private record Basis(String kind,int width,boolean centered,NativeCanopySettings settings,CanopyEmission.Sprite sprite,CanopyDrawingGeometry geometry){}
    private static long checks,vertices,quads;
    private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static Map<String,Basis> inputs(Path file)throws Exception {
        Map<String,Basis> values=new HashMap<>();
        try(var reader=Files.newBufferedReader(file)){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();
            var atlas=row.getAsJsonArray("atlas");float[] edge=new float[4];for(int i=0;i<4;i++)edge[i]=Float.intBitsToFloat(atlas.get(i).getAsInt());
            var settings=NativeCanopySettings.read(CanopySettingsChecks.tags(row.getAsJsonObject("settings")));String kind=row.get("id").getAsString();int width=row.get("width").getAsInt();
            String key=row.get("key").getAsString();String[] parts=key.split(":");int mask=parts.length==4?Integer.parseInt(parts[1]):0,roof=parts.length==4?Integer.parseInt(parts[2]):0;
            double aspect=key.endsWith(":4")?2:key.endsWith(":5")?.5:1;
            List<CanopyFace> faces=NativeCanopyModels.builtin().base(kind,width,mask,roof,row.get("centered").getAsBoolean(),settings.data(),aspect,choice->"vandorlabs:blocks/"+ModernFinishCatalog.option(choice).id());
            Basis basis=new Basis(kind,width,row.get("centered").getAsBoolean(),settings,new CanopyEmission.Sprite(edge[0],edge[1],edge[2],edge[3]),new CanopyDrawingGeometry(faces,kind,width,settings.data()));
            need(values.put(row.get("key").getAsString(),basis)==null,"unique Source base input");
        }}return values;
    }
    private static List<List<CanopyEmission.Prepared>> layers(Basis basis,int facing,double progress) {
        var settings=basis.settings.data();List<CanopyEmission.Prepared> solid=new ArrayList<>(),glass=new ArrayList<>();
        for(CanopyFace face:basis.geometry.posed(progress,basis.centered)){
            var prepared=new CanopyEmission.Prepared(face,facing,CanopyEmission.opacity(face,settings.material,settings.tint));
            (CanopyEmission.translucent(face,settings.material,settings.tint)?glass:solid).add(prepared);
        }
        return List.of(solid,glass);
    }
    private static void materials(JsonArray expected,List<CanopyFace> actual){
        need(expected.size()==actual.size(),"Source posed material count");
        for(int i=0;i<actual.size();i++){var e=expected.get(i).getAsJsonObject();var f=actual.get(i);need(e.get("material").getAsString().equals(f.material)&&e.get("base").getAsString().equals(f.baseMaterial)&&e.get("group").getAsString().equals(f.group),"Source posed material identity");need(e.get("glazing").getAsBoolean()==f.glazing&&e.get("tiled").getAsBoolean()==f.tiled&&e.get("double").getAsBoolean()==f.doubleSided&&e.get("points").getAsInt()==f.points(),"Source posed material flags");}
    }
    private static final class DynamicSink implements CanopyEmission.Sink {
        final ByteBuffer data;DynamicSink(int vertices){data=ByteBuffer.allocate(vertices*28).order(ByteOrder.LITTLE_ENDIAN);}
        public void quad(int direction){}
        public void vertex(float x,float y,float z,float u,float v,int color,float nx,float ny,float nz){data.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v).putInt(color).put((byte)(int)(nx*127)).put((byte)(int)(ny*127)).put((byte)(int)(nz*127)).put((byte)0);}
    }
    private static final class ChunkSink implements CanopyEmission.Sink {
        final List<int[]> records=new ArrayList<>();final List<Integer> directions=new ArrayList<>();int[] words;int at;
        public void quad(int direction){words=new int[28];at=0;records.add(words);directions.add(direction);}
        public void vertex(float x,float y,float z,float u,float v,int color,float nx,float ny,float nz){words[at++]=Float.floatToRawIntBits(x);words[at++]=Float.floatToRawIntBits(y);words[at++]=Float.floatToRawIntBits(z);words[at++]=color;words[at++]=Float.floatToRawIntBits(u);words[at++]=Float.floatToRawIntBits(v);words[at++]=0;}
    }
    private static int dynamic(Path input,Path reference)throws Exception {
        Map<String,Basis> inputs=inputs(input);need(inputs.size()==66,"Source moving base input coverage");int profiles=0;
        try(var reader=Files.newBufferedReader(reference)){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String key=row.get("id").getAsString()+":"+row.get("profile").getAsInt();Basis basis=Objects.requireNonNull(inputs.get(key));
            double progress=row.get("progress").getAsDouble();boolean opening=basis.kind.equals("canopy")||basis.kind.equals("long_canopy")||com.vandorlabs.persistence.CanopySettingsData.supports(basis.kind)&&basis.settings.data().openable;
            materials(row.getAsJsonArray("materials"),basis.geometry.posed(opening?progress:0,basis.centered));
            var layers=layers(basis,row.get("facing").getAsInt(),opening?progress:0);
            layers.get(1).sort(Comparator.comparingDouble((CanopyEmission.Prepared face)->face.center.squareDistanceTo(new CanopyPoint(2.5,3,-4))).reversed());
            for(int layer=0;layer<2;layer++){var expected=row.getAsJsonArray("layers").get(layer).getAsJsonObject();var faces=layers.get(layer);need(faces.size()==expected.get("faces").getAsInt(),"Source dynamic face count "+key);int count=faces.stream().mapToInt(CanopyEmission.Prepared::vertexCount).sum();need(count==expected.get("vertices").getAsInt(),"Source dynamic vertex count");DynamicSink sink=new DynamicSink(count);for(var face:faces)CanopyEmission.emit(face,basis.sprite,false,sink);byte[] wanted=Base64.getDecoder().decode(expected.get("bytes").getAsString());int mismatch=Arrays.mismatch(wanted,sink.data.array());need(mismatch<0,"Source final TESR byte mismatch "+key+" facing="+row.get("facing")+" pose="+progress+" layer="+layer+" at="+mismatch);vertices+=count;}
            profiles++;
        }}return profiles;
    }
    private static int fixed(Path input,Path reference)throws Exception {
        Map<String,Basis> inputs=inputs(input);need(inputs.size()==486,"Source fixed base input coverage");int profiles=0;
        try(var reader=Files.newBufferedReader(reference)){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String key="regular_canopy_glass:"+row.get("mask").getAsInt()+":"+row.get("roof").getAsInt()+":"+row.get("profile").getAsInt();Basis basis=Objects.requireNonNull(inputs.get(key));var layers=layers(basis,row.get("facing").getAsInt(),0);
            for(int layer=0;layer<2;layer++){var expected=row.getAsJsonArray("layers").get(layer).getAsJsonArray();ChunkSink sink=new ChunkSink();for(var face:layers.get(layer))CanopyEmission.emit(face,basis.sprite,true,sink);need(expected.size()==sink.records.size(),"Source actual chunk quad count "+key);for(int q=0;q<expected.size();q++){var quad=expected.get(q).getAsJsonObject();need(quad.get("face").getAsInt()==sink.directions.get(q),"Source actual chunk quad orientation "+key);need(quad.get("tint").getAsInt()==-1&&!quad.get("diffuse").getAsBoolean(),"Source chunk tint/diffuse");var words=quad.getAsJsonArray("words");need(words.size()==28,"released Source block format");for(int i=0;i<28;i++)need(words.get(i).getAsInt()==sink.records.get(q)[i],"Source actual chunk word "+key+" layer="+layer+" quad="+q+" at="+i);quads++;}}
            profiles++;
        }}return profiles;
    }
    public static void main(String[] args)throws Exception {
        int dynamic=dynamic(Path.of(args[0]),Path.of(args[1])),fixed=fixed(Path.of(args[2]),Path.of(args[3]));need(dynamic==1320&&fixed==1944,"Source drawing coverage");
        System.out.println("[vandorlabs][canopy-drawing] PASS SourceTESR="+dynamic+" SourceChunk="+fixed+" exactVertices="+vertices+" exactQuads="+quads+" fields="+checks+" nativeAuthoredBases");
    }
}
