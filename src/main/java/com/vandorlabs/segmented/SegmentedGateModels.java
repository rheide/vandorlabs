package com.vandorlabs.segmented;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed canonical physics facts. No client model classes, world references or per-query JSON parsing. */
public final class SegmentedGateModels {
    private static final int MAX_MODEL_BYTES=65536;
    private static final SegmentedGateShape.Model[][] MODELS=new SegmentedGateShape.Model[3][4];
    private static final List<SegmentedGateShape.Connector>[][][][] CONNECTORS=new List[3][4][4][2];
    static {
        for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)MODELS[kind][part]=read(kind,part);
        for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++)for(int peer=0;peer<4;peer++)for(int side=0;side<2;side++)
            CONNECTORS[kind][part][peer][side]=SegmentedGateShape.connectors(MODELS[kind][part],MODELS[kind][peer],side==1,side==1?8:4);
    }
    private static int part(int mask){if((mask&~15)!=0)throw new IllegalArgumentException("Gate mask");switch(mask&3){case 1:return 3;case 2:return 1;case 3:return 2;default:return 0;}}
    private static void kind(int kind){if(kind<0||kind>2)throw new IllegalArgumentException("Gate kind");}
    public static SegmentedGateShape.Model model(int kind,int mask){kind(kind);return MODELS[kind][part(mask)];}
    /** Caller must first validate the prepared neighbouring planes/mechanism with SegmentedGateJoin. */
    public static List<SegmentedGateShape.Connector> connectors(int kind,int mask,int peerMask,boolean above){
        kind(kind);int part=part(mask),peer=part(peerMask);return (mask&(above?8:4))==0?Collections.emptyList():CONNECTORS[kind][part][peer][above?1:0];
    }
    private static float[] vector(JsonObject object,String name){JsonArray values=object.getAsJsonArray(name);if(values==null||values.size()!=3)throw new IllegalArgumentException("Gate vector "+name);return new float[]{values.get(0).getAsFloat(),values.get(1).getAsFloat(),values.get(2).getAsFloat()};}
    private static SegmentedGateShape.Model read(int kind,int part){
        String[] styles={"picket","industrial","lattice"},parts={"single","left","middle","right"};String path="/assets/vandorlabs/models/block/segmented_gates/"+styles[kind]+"_"+parts[part]+".json";
        try{
            JsonObject root=elements(path,0);JsonArray elements=root.getAsJsonArray("elements");if(elements==null||elements.size()<1||elements.size()>128)throw new IOException("Gate element capacity "+path);
            SegmentedGateShape.Element[] prepared=new SegmentedGateShape.Element[elements.size()];String[] faces={"down","up","north","south","west","east"};
            for(int index=0;index<prepared.length;index++){
                JsonObject element=elements.get(index).getAsJsonObject(),faceMap=element.getAsJsonObject("faces");int mask=0;if(faceMap==null)throw new IOException("Missing gate faces");for(int face=0;face<6;face++)if(faceMap.has(faces[face]))mask|=1<<face;
                JsonObject rotation=element.has("rotation")?element.getAsJsonObject("rotation"):null;int axis=-1;float angle=0;float[] origin=null;boolean rescale=false;
                if(rotation!=null){String name=rotation.get("axis").getAsString();axis="x".equals(name)?0:"y".equals(name)?1:"z".equals(name)?2:-1;if(axis<0)throw new IOException("Invalid gate axis");angle=rotation.get("angle").getAsFloat();origin=vector(rotation,"origin");rescale=rotation.has("rescale")&&rotation.get("rescale").getAsBoolean();}
                prepared[index]=new SegmentedGateShape.Element(vector(element,"from"),vector(element,"to"),axis,angle,origin,rescale,mask);
            }
            return new SegmentedGateShape.Model(kind,prepared);
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot prepare canonical gate geometry "+path,failure);}
    }
    private static JsonObject elements(String path,int depth)throws IOException{
        if(depth>=16)throw new IOException("Gate parent depth");
        try(InputStream stream=SegmentedGateModels.class.getResourceAsStream(path)){
            if(stream==null)throw new IOException("Missing canonical gate model "+path);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int count;
            while((count=stream.read(chunk))!=-1){if(bytes.size()+count>MAX_MODEL_BYTES)throw new IOException("Oversized canonical gate model "+path);bytes.write(chunk,0,count);}
            JsonObject root=new JsonParser().parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).getAsJsonObject();if(root.has("elements"))return root;
            if(!root.has("parent"))throw new IOException("Missing gate elements");String parent=root.get("parent").getAsString();if(!parent.matches("vandorlabs:[a-z0-9_/]+"))throw new IOException("Invalid canonical gate parent");
            return elements("/assets/vandorlabs/models/"+parent.substring("vandorlabs:".length())+".json",depth+1);
        }
    }
    private SegmentedGateModels(){}
}
