package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.screen.*;
import org.joml.Matrix4f;

/** Independent whole-renderer Source GL matrices, including the unscaled HalfDiagonal row branch. */
public final class NativeScreenRowsMatrixChecks {
    public static void main(String[] args)throws Exception{
        JsonObject reference;try(var input=new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0])))){byte[] bytes=input.readNBytes(8*1024*1024+1);if(bytes.length>8*1024*1024)throw new AssertionError("Source row reference bound");reference=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}int profiles=0;double error=0;
        for(var item:reference.getAsJsonArray("profiles")){var profile=item.getAsJsonObject();var family=ScreenFamily.fromId(profile.get("id").getAsString());var state=family.decode(profile.get("meta").getAsInt());int slot=profile.get("slot").getAsInt(),height=reference.getAsJsonArray("styles").get(profile.get("style").getAsInt()).getAsJsonObject().get("height").getAsInt();boolean ceiling=profile.get("ceiling").getAsBoolean();int position=profile.get("ceilingPosition").getAsInt();var base=ScreenControlLayout.of(family,state,slot,profile.get("small").getAsBoolean(),profile.get("wall").getAsInt(),ceiling,position,false).surface;var raw=profile.getAsJsonArray("content");double width=base.topRight.x-base.topLeft.x,dy=base.bottomLeft.y-base.topLeft.y,dz=base.bottomLeft.z-base.topLeft.z,length=dy*dy+dz*dz;
            var content=ScreenSurface.inset(base,(raw.get(0).getAsDouble()-base.topLeft.x)/width,(base.topRight.x-raw.get(3).getAsDouble())/width,((raw.get(1).getAsDouble()-base.topLeft.y)*dy+(raw.get(2).getAsDouble()-base.topLeft.z)*dz)/length,((base.bottomLeft.y-raw.get(10).getAsDouble())*dy+(base.bottomLeft.z-raw.get(11).getAsDouble())*dz)/length);
            for(int vertex=0;vertex<4;vertex++){var value=content.vertices[vertex];double[] actual={value.x,value.y,value.z};for(int axis=0;axis<3;axis++)if(Math.abs(actual[axis]-raw.get(vertex*3+axis).getAsDouble())>1e-10)throw new AssertionError("Source overlay content");}
            double[] pose=new double[16];ScreenRenderPose.write(family==ScreenFamily.HALF_DIAGONAL?ScreenFamily.HALF_CONSOLE:family,family==ScreenFamily.HALF_DIAGONAL?new ScreenFamily.State(state.facing,false,false,false):state,family!=ScreenFamily.HALF_DIAGONAL&&ceiling,position,pose);float[] values=new float[16];for(int i=0;i<16;i++)values[i]=(float)pose[i];var transform=new Matrix4f().set(values);var ui=new Matrix4f();NativeScreenRowsRenderer.uiMatrix(content,height,ui);transform.mul(ui);
            for(String key:new String[]{"matrix","textMatrix"}){if(key.equals("textMatrix"))transform.translate(0,0,.04F);transform.get(values);var expected=profile.getAsJsonArray(key);for(int i=0;i<16;i++){double difference=Math.abs(values[i]-Float.intBitsToFloat(expected.get(i).getAsInt()));error=Math.max(error,difference);if(difference>2e-6)throw new AssertionError("Source overlay matrix "+family+" meta="+profile.get("meta")+" slot="+slot+" key="+key+" component="+i+" actual="+values[i]+" expected="+Float.intBitsToFloat(expected.get(i).getAsInt()));}}
            profiles++;
        }
        if(profiles!=1148)throw new AssertionError("Source row pose coverage");System.out.println("Native Screen row affine PASS profiles="+profiles+" maxError="+error+" content/frame/all saved facings/half-diagonal unscaled/text depth");
    }
}
