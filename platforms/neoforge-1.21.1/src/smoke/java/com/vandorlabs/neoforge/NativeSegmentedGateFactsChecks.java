package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.neoforge.segmented.NativeSegmentedGateModels;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Actual released Forge vertices replayed against prepared native server geometry. */
public final class NativeSegmentedGateFactsChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));need(hash.equals("2c2a5246aeb2894ea2237f75f287f0a162047fd615ba2f60b8a54325d2e6d605"),"actual Source model reference changed");
        int records=0,quads=0,vertices=0;double[] point=new double[3];Set<String> ids=new HashSet<>();
        for(String line:new String(bytes,StandardCharsets.UTF_8).split("\\R")){
            if(line.isBlank())continue;JsonObject record=JsonParser.parseString(line).getAsJsonObject();String id=record.get("id").getAsString(),name=id.substring(id.lastIndexOf('/')+1);int kind=name.startsWith("picket_")?0:name.startsWith("industrial_")?1:2,mask=name.endsWith("_single")?0:name.endsWith("_left")?2:name.endsWith("_middle")?3:1,yaw=record.get("yaw").getAsInt();
            SegmentedGateShape.Model model=NativeSegmentedGateModels.model(kind,mask);need(model==NativeSegmentedGateModels.model(kind,mask),"prepared model identity");ids.add(id);records++;
            for(JsonElement raw:record.getAsJsonArray("quads")){
                JsonObject quad=raw.getAsJsonObject();SegmentedGateShape.Element element=model.element(quad.get("element").getAsInt());JsonArray data=quad.getAsJsonArray("vertices");need(data.size()==28,"Source vertex stride");
                for(int vertex=0;vertex<4;vertex++){
                    float x=Float.intBitsToFloat(data.get(vertex*7).getAsInt()),y=Float.intBitsToFloat(data.get(vertex*7+1).getAsInt()),z=Float.intBitsToFloat(data.get(vertex*7+2).getAsInt());boolean found=false;
                    for(int corner=0;corner<8;corner++){element.corner(corner,point,0);double cx=point[0],cz=point[2],rx=yaw==90?1-cz:yaw==180?1-cx:yaw==270?cz:cx,rz=yaw==90?cx:yaw==180?1-cz:yaw==270?1-cx:cz;found|=Math.abs(rx-x)<1e-6&&Math.abs(point[1]-y)<1e-6&&Math.abs(rz-z)<1e-6;}
                    need(found,"native prepared corner differs from actual Forge "+id+" yaw="+yaw);vertices++;
                }quads++;
            }
        }
        for(int kind=0;kind<3;kind++)for(int mask=0;mask<16;mask++)for(int peer=0;peer<16;peer++)for(boolean above:new boolean[]{false,true})need(NativeSegmentedGateModels.connectors(kind,mask,peer,above)==NativeSegmentedGateModels.connectors(kind,mask,peer,above),"fixed native connector identity");
        need(ids.size()==12&&records==48&&quads==3040&&vertices==12160,"Source corpus coverage");System.out.println("Native Segmented gate facts PASS records="+records+" quads="+quads+" vertices="+vertices+" checks="+checks);
    }
}
