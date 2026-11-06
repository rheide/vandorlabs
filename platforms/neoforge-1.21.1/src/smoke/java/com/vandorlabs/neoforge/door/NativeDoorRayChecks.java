package com.vandorlabs.neoforge.door;
import com.google.gson.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorControlPanel;
import java.nio.file.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
public final class NativeDoorRayChecks {
    static boolean b(JsonObject r,String k){return r.get(k).getAsBoolean();}
    static Vec3 v(JsonObject r,String k){JsonArray a=r.getAsJsonArray(k);return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    public static void main(String[] args)throws Exception{
        long count=0;var base=NativeDoorSettings.fresh(false,true);
        try(var lines=Files.lines(Path.of(args[0]))){for(String line:(Iterable<String>)lines::iterator){var r=JsonParser.parseString(line).getAsJsonObject();int depth=r.get("depth").getAsInt(),mode=r.get("mode").getAsInt();
            var settings=new NativeDoorSettings(new SpaceDoorData(2,1,b(r,"framed"),0,depth==0,b(r,"sliding"),b(r,"hinges"),0,mode!=0),-1,-1,false,depth,mode,b(r,"security")?1:0,127,base.frames(),base.glass());
            var hit=NativeDoorGeometry.trace(settings,true,Direction.from3DDataValue(r.get("face").getAsInt()),b(r,"open"),b(r,"left"),b(r,"paired"),SpaceDoorControlPanel.Side.valueOf(r.get("side").getAsString()),b(r,"upper"),b(r,"missing"),v(r,"start"),v(r,"end"));
            if(r.get("hit").isJsonNull()){if(hit!=null)throw new AssertionError("Unexpected hit record="+count+" "+line);}else{Vec3 expected=v(r,"hit");if(hit==null||hit.face!=r.get("hitFace").getAsInt()||Double.doubleToLongBits(hit.x)!=Double.doubleToLongBits(expected.x)||Double.doubleToLongBits(hit.y)!=Double.doubleToLongBits(expected.y)||Double.doubleToLongBits(hit.z)!=Double.doubleToLongBits(expected.z))throw new AssertionError("Hit mismatch record="+count+" got="+(hit==null?null:hit.x+","+hit.y+","+hit.z+" face="+hit.face)+" "+line);}
            count++;
        }}if(count!=419328)throw new AssertionError("Incomplete Forge reference "+count);System.out.println("[vandorlabs][door-rays] CPU PASS actual Forge records="+count+" exact hit bits/faces including inside, ties, near-parallel and missing lower; no registry activation");
    }
}
