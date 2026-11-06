package com.vandorlabs.neoforge.segmented;

import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

/** Complete emitted 1.12 geometry; rays include edges, inside starts, headers and far-world cancellation. */
public final class SegmentedGeometryChecks {
    public static void main(String[] args)throws Exception {
        int profiles=0,rays=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){
            var row=JsonParser.parseString(line).getAsJsonObject();int height=i(row,"height"),width=i(row,"width"),column=i(row,"column"),part=i(row,"row"),direction=i(row,"direction"),position=i(row,"position");
            var origin=row.getAsJsonArray("origin");var root=new BlockPos(origin.get(0).getAsInt(),origin.get(1).getAsInt(),origin.get(2).getAsInt());var face=Direction.from3DDataValue(i(row,"face"));double progress=row.get("progress").getAsDouble();
            var snapshot=NativeSegmentedGeometry.snapshot(root,face,position,part,height,column,width,progress,direction,row.get("straight").getAsBoolean());
            equal(row.getAsJsonArray("matrix"),snapshot.matrix(),"matrix "+profiles);box(row.getAsJsonArray("bounds"),snapshot.bounds(),"bounds "+profiles);
            if(row.has("header"))box(row.getAsJsonArray("header"),snapshot.header(),"header "+profiles);else if(snapshot.header()!=null)throw new AssertionError("Unexpected header "+profiles);
            List<AABB> collision=new ArrayList<>();if(progress==0)collision.add(NativeSegmentedGeometry.closedPanel(face,position).move(root.getX(),root.getY()-part,root.getZ()));if(snapshot.header()!=null)collision.add(snapshot.header());
            var expectedBoxes=row.getAsJsonArray("collisions");if(expectedBoxes.size()!=collision.size())throw new AssertionError("Collision count "+profiles);for(int n=0;n<collision.size();n++)box(expectedBoxes.get(n).getAsJsonArray(),collision.get(n),"collision "+profiles);
            for(var element:row.getAsJsonArray("rays")){var ray=element.getAsJsonObject();var a=ray.getAsJsonArray("a");var b=ray.getAsJsonArray("b");var hit=snapshot.trace(d(a,0),d(a,1),d(a,2),d(b,0),d(b,1),d(b,2));
                if(ray.has("face")){if(hit==null||hit.face!=i(ray,"face"))throw new AssertionError("Ray face profile="+profiles+" ray="+rays+" expected="+ray+" actual="+(hit==null?null:hit.face));equal(ray.getAsJsonArray("hit"),new double[]{hit.x,hit.y,hit.z},"ray "+rays);}else if(hit!=null)throw new AssertionError("Unexpected ray "+rays);rays++;
            }profiles++;
        }}System.out.println("Native Segmented geometry PASS actual-source profiles="+profiles+" rays="+rays+" exact matrices bounds header/collision and hit bits");
    }
    private static int i(JsonObject row,String key){return row.get(key).getAsInt();}
    private static double d(JsonArray row,int index){return row.get(index).getAsDouble();}
    private static void box(JsonArray expected,AABB actual,String context){if(actual==null)throw new AssertionError("Missing "+context);equal(expected,new double[]{actual.minX,actual.minY,actual.minZ,actual.maxX,actual.maxY,actual.maxZ},context);}
    private static void equal(JsonArray expected,double[] actual,String context){if(expected.size()!=actual.length)throw new AssertionError(context+" length");for(int n=0;n<actual.length;n++)if(Double.doubleToLongBits(d(expected,n))!=Double.doubleToLongBits(actual[n]))throw new AssertionError(context+" field="+n+" expected="+d(expected,n)+" actual="+actual[n]);}
}
