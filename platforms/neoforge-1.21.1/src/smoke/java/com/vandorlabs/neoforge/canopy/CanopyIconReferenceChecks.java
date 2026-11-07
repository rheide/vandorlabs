package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.render.ItemIconBounds;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Independent actual released Icon emissions and GUI fit, before atlas placement or GPU drawing. */
public final class CanopyIconReferenceChecks {
    private static void integer(MessageDigest digest,int value){digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));digest.update((byte)(value>>>8));digest.update((byte)value);}
    private static String sourceName(String material){return material.contains(":")?material:"vandorlabs:blocks/canopy/"+material;}
    private static final class Packed implements CanopyItemEmission.Sink {
        final MessageDigest digest;final ItemIconBounds bounds=new ItemIconBounds(25,225,0,1,1,1,0);final int[] data=new int[28];String name;int direction,normal,vertices,count;
        Packed()throws Exception{digest=MessageDigest.getInstance("SHA-256");}
        public void quad(int direction,int normal){flush();this.direction=direction;this.normal=normal;}
        public void vertex(float x,float y,float z,float u,float v,int color){int at=vertices++*7;data[at]=Float.floatToRawIntBits(x);data[at+1]=Float.floatToRawIntBits(y);data[at+2]=Float.floatToRawIntBits(z);data[at+3]=color;data[at+4]=Float.floatToRawIntBits(u);data[at+5]=Float.floatToRawIntBits(v);data[at+6]=normal;bounds.include(x,y,z);}
        void flush(){if(vertices==0)return;if(vertices!=4)throw new AssertionError("Item record");digest.update((name+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));integer(digest,direction);for(int value:data)integer(digest,value);vertices=0;count++;}
        String finish(){flush();return HexFormat.of().formatHex(digest.digest());}
    }
    public static void main(String[] args)throws Exception{
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        int count=0;long quads=0;
        for(var element:rows){var row=element.getAsJsonObject();String kind=row.get("id").getAsString(),text=row.get("input").getAsString();var settings=NativeCanopySettings.read(text.isEmpty()?new CompoundTag():TagParser.parseTag(text));
            var faces=CanopyItemGeometry.faces(NativeCanopyModels.builtin()::model,kind,settings.data(),row.get("aspect").getAsDouble(),choice->"vandorlabs:blocks/"+ModernFinishCatalog.option(choice).id());var packed=new Packed();
            for(var face:faces){packed.flush();packed.name=sourceName(face.material);CanopyItemEmission.emit(face,CanopyEmission.opacity(face,settings.data().material,settings.data().tint),new CanopyEmission.Sprite(0,1,0,1),packed);}
            String hash=packed.finish();if(!hash.equals(row.get("sha256").getAsString())||packed.count!=row.get("quads").getAsInt())throw new AssertionError("Source Icon emission "+kind+" profile="+row.get("profile")+" aspect="+row.get("aspect")+" quads="+packed.count+" expected="+row.get("quads")+" sha="+hash+" expectedSha="+row.get("sha256"));
            if(!sourceName(faces.getFirst().material).equals(row.get("particle").getAsString()))throw new AssertionError("Source particle "+kind);float[] fit=packed.bounds.fit();var gui=row.getAsJsonObject("transforms").getAsJsonArray("GUI");for(int i=0;i<9;i++)if(Float.floatToRawIntBits(fit[i])!=gui.get(i).getAsInt())throw new AssertionError("Source GUI fit "+kind+" profile="+row.get("profile")+" field="+i+" actual="+fit[i]+" expected="+Float.intBitsToFloat(gui.get(i).getAsInt()));count++;quads+=packed.count;
        }
        if(count!=176)throw new AssertionError("Source Icon coverage="+count);System.out.println("Canopy Icon reference PASS Source="+count+" quads="+quads+" raw geometry/triangles/alpha/packed normals/UVs/particle/GUI fit; actual item binding and hands pending");
    }
}
