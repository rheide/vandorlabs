package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.render.RampFaceTexture;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.Direction;

/** Actual Source packed face records before native atlas packing and GPU rendering. */
public final class NativeRampFaceChecks {
    private static void integer(MessageDigest digest,int value){digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));digest.update((byte)(value>>>8));digest.update((byte)value);}
    public static void main(String[] args)throws Exception{
        int cases=0;long vertices=0;
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();var a=row.getAsJsonArray("box");var box=new RampGeometry.Box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());var uv=row.getAsJsonArray("uv");int face=row.get("face").getAsInt(),mode=row.get("mode").getAsInt(),tint=row.get("tint").getAsInt();float shade=face==1?1:face==0?.5F:face>=4?.6F:.8F;int color=0xff000000|((int)((tint>>16&255)/255F*shade*255F)&255)|(((int)((tint>>8&255)/255F*shade*255F)&255)<<8)|(((int)((tint&255)/255F*shade*255F)&255)<<16);var direction=Direction.from3DDataValue(face);int normal=(direction.getStepX()*127&255)|((direction.getStepY()*127&255)<<8)|((direction.getStepZ()*127&255)<<16);var digest=MessageDigest.getInstance("SHA-256");int[] count={0};
            RampFaceTexture.emit(box,face,row.get("aspect").getAsDouble(),uv.get(0).getAsDouble(),uv.get(1).getAsDouble(),uv.get(2).getAsDouble(),uv.get(3).getAsDouble(),mode,(x,y,z,u,v)->{for(int value:new int[]{Float.floatToRawIntBits((float)x),Float.floatToRawIntBits((float)y),Float.floatToRawIntBits((float)z),color,Float.floatToRawIntBits((float)(u*16)/16F),Float.floatToRawIntBits((float)(v*16)/16F),192<<16|80,normal})integer(digest,value);count[0]++;});
            String hash=HexFormat.of().formatHex(digest.digest());if(count[0]!=row.get("vertices").getAsInt()||!hash.equals(row.get("sha256").getAsString()))throw new AssertionError("Released Ramp face case="+cases+" count="+count[0]+" hash="+hash+" input="+row);cases++;vertices+=count[0];
        }}
        if(cases!=2304||vertices!=44288)throw new AssertionError("Ramp face coverage "+cases+"/"+vertices);System.out.println("Native Ramp faces PASS actualSource="+cases+" vertices="+vertices+" packedPositionColorUVLightNormal/negativeNativeRepeat/tile/aspectFit; GPU binding separate");
    }
}
