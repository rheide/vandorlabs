package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyCollisionIndex;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
import net.minecraft.util.math.AxisAlignedBB;

/** Actual released Ramp boxes and AABB.shrink against frozen released Canopy extrema. */
public final class CanopyRampSweepLegacyOracle {
    public static void main(String[] args)throws Exception {
        LinkedHashSet<AxisAlignedBB> unique=new LinkedHashSet<>();int parts=0,inversions=0;
        int[][] shifts={{0,0,0},{2,-1,-2},{-2,0,1},{0,2,0}};
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(args[1])),StandardCharsets.UTF_8))){
            for(String line;(line=in.readLine())!=null;){JsonObject row=new JsonParser().parse(line).getAsJsonObject();for(JsonElement value:row.getAsJsonArray("states")){JsonObject state=value.getAsJsonObject();NBTTagCompound owner=JsonToNBT.getTagFromJson(state.get("saved").getAsString());for(JsonElement raw:state.getAsJsonArray("parts")){parts++;JsonObject part=raw.getAsJsonObject();JsonArray position=part.getAsJsonArray("position");for(JsonElement box:part.getAsJsonArray("boxes")){JsonArray words=box.getAsJsonArray();for(int[] shift:shifts){double[] v=new double[6];for(int i=0;i<6;i++)v[i]=words.get(i).getAsDouble()+position.get(i%3).getAsInt()-owner.getInteger(i%3==0?"x":i%3==1?"y":"z")+(i%3==0?-17:i%3==1?84:35)+shift[i%3];unique.add(new AxisAlignedBB(v[0],v[1],v[2],v[3],v[4],v[5]));}}}}}
        }
        List<AxisAlignedBB> queries=new ArrayList<>();JsonArray encoded=new JsonArray();
        for(AxisAlignedBB raw:unique){if(raw.maxX-raw.minX<2e-7||raw.maxY-raw.minY<2e-7||raw.maxZ-raw.minZ<2e-7)inversions++;AxisAlignedBB q=raw.shrink(1e-7);queries.add(q);JsonArray words=new JsonArray();for(double v:new double[]{q.minX,q.minY,q.minZ,q.maxX,q.maxY,q.maxZ})words.add(Long.toString(Double.doubleToRawLongBits(v)));encoded.add(words);}
        long checks=0,hits=0;int profiles=0;Gson gson=new Gson();
        try(PrintWriter out=new PrintWriter(args[2],"UTF-8");BufferedReader in=Files.newBufferedReader(Paths.get(args[0]),StandardCharsets.UTF_8)){
            JsonObject header=new JsonObject();header.add("queries",encoded);out.println(gson.toJson(header));
            for(String line;(line=in.readLine())!=null;){JsonObject row=new JsonParser().parse(line).getAsJsonObject();List<AxisAlignedBB> boxes=new ArrayList<>();for(JsonElement raw:row.getAsJsonArray("boxes")){JsonArray words=raw.getAsJsonArray();double[] v=new double[6];for(int i=0;i<6;i++)v[i]=Double.longBitsToDouble(Long.parseLong(words.get(i).getAsString()));boxes.add(new AxisAlignedBB(v[0],v[1],v[2],v[3],v[4],v[5]));}CanopyCollisionIndex index=new CanopyCollisionIndex(boxes);BitSet results=new BitSet(queries.size());for(int i=0;i<queries.size();i++){if(index.intersects(queries.get(i))){results.set(i);hits++;}checks++;}out.println(Base64.getEncoder().encodeToString(results.toByteArray()));profiles++;}
        }
        CanopySweepLegacyOracle.check(new File(args[2]),"8f64d4018cbcd68003fd27c49ed5eb63acba452c0d4c9fde085d046027ab57b2");
        if(profiles!=4928||queries.size()!=5739||parts!=9322||inversions!=0)throw new AssertionError("Actual Ramp/Canopy sweep coverage");
        System.out.println("Actual Ramp/Canopy sweep predicates PASS profiles="+profiles+" parts="+parts+" queries="+queries.size()+" checks="+checks+" hits="+hits+" shrinkInversions="+inversions);
    }
}
