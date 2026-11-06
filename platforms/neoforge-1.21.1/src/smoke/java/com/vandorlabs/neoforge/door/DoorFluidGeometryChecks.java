package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import com.vandorlabs.persistence.SpaceDoorData;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Raw current Forge plane/solid/ordered run/face replay, including world-coordinate rounding. */
public final class DoorFluidGeometryChecks {
    private static MessageDigest hash;private static long cells,faces;private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void bits(long value){for(int shift=56;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
    private static void number(double value){bits(Double.doubleToRawLongBits(value));}
    private static void box(AABB b){number(b.minX);number(b.minY);number(b.minZ);number(b.maxX);number(b.maxY);number(b.maxZ);}
    private static String hex(byte[] values){StringBuilder out=new StringBuilder();for(byte b:values)out.append(String.format("%02x",b&255));return out.toString();}
    public static void main(String[] args)throws Exception{
        int cases=0;try(var lines=Files.lines(Paths.get(args[0]))){for(String line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();boolean sliding=r.get("sliding").getAsBoolean(),framed=r.get("framed").getAsBoolean(),hinges=r.get("hinges").getAsBoolean(),upper=r.get("upper").getAsBoolean();int depth=r.get("depth").getAsInt();var defaults=NativeDoorSettings.fresh(false,sliding);var settings=new NativeDoorSettings(new SpaceDoorData(2,1,framed,0,depth==0,sliding,hinges,0,true),-1,-1,false,depth,1,0,127,defaults.frames(),defaults.glass());
            var value=NativeDoorFluidGeometry.build(settings,Direction.from3DDataValue(r.get("face").getAsInt()),r.get(upper?"topOpen":"bottomOpen").getAsBoolean(),r.get("left").getAsBoolean(),r.get("paired").getAsBoolean(),r.get("x").getAsInt(),r.get("y").getAsInt()+(upper?1:0),r.get("z").getAsInt());
            hash=MessageDigest.getInstance("SHA-256");var p=value.plane();for(double n:new double[]{p.x(),0,p.z(),p.near(),p.far()})number(n);var volume=value.volume();need(!volume.facesMaterialized(),"building contacts must leave face graph lazy");bits(volume.solids().size());for(var b:volume.solids())box(b);bits(volume.cells().size());for(var cell:volume.cells()){bits(cell.side());box(cell.box());cells++;}bits(volume.faces().size());for(var face:volume.faces()){bits(face.side());bits(face.axis());for(var v:face.vertices()){number(v.x);number(v.y);number(v.z);}faces++;}String actual=hex(hash.digest());need(actual.equals(r.get("hash").getAsString()),"actual Forge fluid geometry mismatch case="+cases+" profile="+r+" actual="+actual);cases++;
        }}need(cases==12288,"complete coordinate/facing/half/open/profile corpus");System.out.println("Native ordinary Door fluid geometry PASS cases="+cases+" checks="+checks+" cells="+cells+" faces="+faces+" exact current plane/cells/faces/world rounding; no world references or new Cartesian builder");
    }
}
