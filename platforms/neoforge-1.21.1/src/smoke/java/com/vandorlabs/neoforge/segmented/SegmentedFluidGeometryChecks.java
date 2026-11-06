package com.vandorlabs.neoforge.segmented;

import com.google.gson.*;
import com.vandorlabs.neoforge.door.NativeDoorOpeningFluids;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** Raw actual-source plane/solid/ordered run/face comparisons plus all reversible opening decisions. */
public final class SegmentedFluidGeometryChecks {
    private static MessageDigest hash;private static int checks;private static long cells,faces;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void bits(long value){for(int shift=56;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
    private static void number(double value){bits(Double.doubleToRawLongBits(value));}
    private static void box(AABB b){number(b.minX);number(b.minY);number(b.minZ);number(b.maxX);number(b.maxY);number(b.maxZ);}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
    public static void main(String[] args)throws Exception{
        int profiles=0;try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();var state=NativeSegmentedFluidGeometry.build(Direction.from3DDataValue(row.get("face").getAsInt()),row.get("direction").getAsInt(),row.get("depth").getAsInt(),row.get("height").getAsInt(),row.get("row").getAsInt(),row.get("column").getAsInt(),row.get("width").getAsInt(),row.get("closed").getAsBoolean(),new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt()));hash=MessageDigest.getInstance("SHA-256");var p=state.plane();for(double value:new double[]{p.x(),0,p.z(),p.near(),p.far()})number(value);var volume=state.volume();need(!volume.facesMaterialized(),"contact build must not materialize faces");bits(volume.solids().size());for(var value:volume.solids())box(value);bits(volume.cells().size());for(var value:volume.cells()){bits(value.side());box(value.box());cells++;}bits(volume.faces().size());for(var value:volume.faces()){bits(value.side());bits(value.axis());for(var v:value.vertices()){number(v.x);number(v.y);number(v.z);}faces++;}String actual=hex(hash.digest());need(actual.equals(row.get("hash").getAsString()),"actual Segmented fluid volume mismatch profile="+row+" actual="+actual);profiles++;}}
        need(profiles==17856&&cells==124160&&faces==725120,"complete source role/rounding volume corpus");int openings=0;try(var lines=Files.lines(Path.of(args[1]))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();int first=row.get("first").getAsInt(),second=row.get("second").getAsInt(),mask=row.get("mask").getAsInt();var face=Direction.from3DDataValue(row.get("face").getAsInt());int ambient=((mask&(1<<face.getOpposite().get3DDataValue()))!=0?1:0)|((mask&(1<<face.get3DDataValue()))!=0?2:0);int result=NativeDoorOpeningFluids.sources(row.get("open").getAsBoolean(),first==3?0:first,second==3?0:second,ambient);for(int side=0;side<2;side++){int own=side==0?first:second,other=side==0?second:first,source=(result>>(side*2))&3,selected=own>0&&own<3?own:source==1?other:0;int kind=selected!=0?selected:source==2?1:0,amount=selected==1?777:selected==2?333:source==2?1000:0;need(kind==row.get("side"+side).getAsInt()&&amount==row.get("amount"+side).getAsInt()&&selected==row.get("tag"+side).getAsInt(),"actual Segmented opening fill/tag/amount mismatch "+row+" side="+side);}openings++;}}
        need(openings==28672,"complete source root/local/open/ambient/opaque corpus");System.out.println("Native Segmented fluid geometry PASS profiles="+profiles+" opening="+openings+" checks="+checks+" cells="+cells+" faces="+faces+" exact source rounding/root modes/own planes; reused compact runs and lazy faces");
    }
}
