package com.vandorlabs.neoforge.seating;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** One pair of authored four-position levers. The same pivot and angle drive hit tests and rendering. */
public final class NativeCockpitThrottle {
    static final int LEFT=NativeChairArmrest.LEFT, RIGHT=NativeChairArmrest.RIGHT;
    private static final AABB GRIP=new AABB(-.075,-.025,-.078,.075,.335,.078);
    private record Part(Vec3 pivot,List<Double> angles) {}
    private static final List<Part> PARTS=parts("cockpit",List.of("throttle_left","throttle_right"));
    private static final List<Part> SINGLE=parts("cockpit_single",List.of("throttle_left"));

    private static Part part(int side,int count){return (count==1?SINGLE:PARTS).get(side);}

    public static Vec3 pivot(int side,int count){return part(side,count).pivot();}
    public static double angle(int side,int step,int count){return part(side,count).angles().get(step);}
    record Hit(int index,BlockHitResult ray) {}

    static Hit pick(NativeChairEntity owner,Direction facing,BlockPos clicked,Vec3 start,Vec3 end){
        var root=Vec3.atLowerCornerOf(owner.getBlockPos());
        var inverse=switch(facing){case EAST->Direction.WEST;case WEST->Direction.EAST;default->facing;};
        var localStart=NativeAdvancedChairGeometry.turn(start.subtract(root),inverse);
        var localEnd=NativeAdvancedChairGeometry.turn(end.subtract(root),inverse);
        Hit nearest=null;
        for(int side=0;side<owner.throttleCount();side++){
            Vec3 pivot=pivot(side,owner.throttleCount());
            float angle=(float)Math.toRadians(angle(side,owner.throttleStep(side),owner.throttleCount()));
            var result=GRIP.clip(localStart.subtract(pivot).xRot(angle),localEnd.subtract(pivot).xRot(angle));
            if(result.isEmpty())continue;
            var point=NativeAdvancedChairGeometry.turn(result.get().xRot(-angle).add(pivot),facing).add(root);
            if(!BlockPos.containing(point).equals(clicked))continue;
            if(nearest==null||point.distanceToSqr(start)<nearest.ray().getLocation().distanceToSqr(start))
                nearest=new Hit(side==0?LEFT:RIGHT,new BlockHitResult(point,Direction.UP,clicked,false));
        }
        return nearest;
    }

    private static List<Part> parts(String design,List<String> ids){
        try(var stream=NativeCockpitThrottle.class.getResourceAsStream("/assets/vandorlabs/cockpits/"+design+"/rig.json")){
            if(stream==null)throw new IllegalStateException("Missing authored Cockpit throttle rig");
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("parts");
            var parts=new java.util.ArrayList<Part>(ids.size());
            for(String id:ids){
                for(var entry:data){
                    var part=entry.getAsJsonObject();
                    if(!part.get("id").getAsString().equals(id))continue;
                    var rawPivot=part.getAsJsonArray("pivot");
                    var rawAngles=part.getAsJsonArray("values");
                    var angles=new java.util.ArrayList<Double>(4);
                    for(var raw:rawAngles)angles.add(raw.getAsDouble());
                    if(angles.size()!=4)throw new IllegalStateException("Unexpected Cockpit throttle detents");
                    parts.add(new Part(new Vec3(rawPivot.get(0).getAsDouble(),rawPivot.get(1).getAsDouble(),
                        rawPivot.get(2).getAsDouble()),List.copyOf(angles)));
                    break;
                }
            }
            if(parts.size()!=ids.size())throw new IllegalStateException("Missing Cockpit throttle pivots");
            return List.copyOf(parts);
        }catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }

    private NativeCockpitThrottle(){}
}
