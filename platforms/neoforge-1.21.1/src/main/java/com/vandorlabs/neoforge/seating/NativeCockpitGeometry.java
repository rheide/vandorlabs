package com.vandorlabs.neoforge.seating;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Parked cushion and collision from the release Cockpit assembly resource. */
final class NativeCockpitGeometry {
    static final int CELLS=8;
    private record Source(Vec3 seat,List<AABB> boxes,List<AABB> bank,AABB button) {}
    private static final int STEPS=32;
    private static final Source SOURCE=load();
    private static final VoxelShape[][][] SHAPES=shapes();

    static Vec3 cushion(Direction facing) { return NativeAdvancedChairGeometry.turn(SOURCE.seat(),facing); }
    static BlockPos offset(int part,Direction facing) {
        Vec3 center=new Vec3(.5+(part>>2),part&1,.5-((part>>1)&1));
        return BlockPos.containing(NativeAdvancedChairGeometry.turn(center,facing));
    }
    static VoxelShape shape(int part,Direction facing){return shape(part,facing,0);}
    static VoxelShape shape(int part,Direction facing,double progress){
        return SHAPES[facing.get2DDataValue()][part][(int)Math.round(STEPS*progress)];
    }

    static BlockHitResult button(BlockPos root,Direction facing,BlockPos clicked,Vec3 start,Vec3 end){
        var button=turn(SOURCE.button(),facing).move(root).inflate(.008);
        var hit=button.clip(start,end);
        if(hit.isEmpty()||!BlockPos.containing(hit.get()).equals(clicked))return null;
        return new BlockHitResult(hit.get(),Direction.UP,clicked,false);
    }

    static boolean clearLift(ServerLevel level,BlockPos root,Direction facing,double current,boolean retracting){
        double from=-NativeCockpitLift.TRAVEL*current,end=retracting?-NativeCockpitLift.TRAVEL:0;
        for(var box:SOURCE.bank()){
            var a=turn(box.move(0,from,0),facing).move(root);
            var b=turn(box.move(0,end,0),facing).move(root);
            var swept=a.minmax(b);
            // The lower end stows inside the existing housing and never claims the supporting block.
            if(swept.minY<root.getY())swept=new AABB(swept.minX,root.getY(),swept.minZ,swept.maxX,swept.maxY,swept.maxZ);
            if(!level.getEntitiesOfClass(LivingEntity.class,swept,entity->entity.isAlive()
                &&(!(entity instanceof Player player)||!player.isSpectator())).isEmpty())return false;
        }
        return true;
    }

    private static VoxelShape[][][] shapes(){
        var shapes=new VoxelShape[4][CELLS][STEPS+1];
        for(var facing:Direction.Plane.HORIZONTAL){
            for(int part=0;part<CELLS;part++){
                var cell=offset(part,facing);
                var bounds=new AABB(cell);
                VoxelShape fixed=Shapes.empty();
                for(var box:SOURCE.boxes())fixed=include(fixed,turn(box,facing),bounds,cell);
                for(int step=0;step<=STEPS;step++){
                    VoxelShape shape=fixed;
                    for(var box:SOURCE.bank()){
                        var moved=box.move(0,-NativeCockpitLift.TRAVEL*step/STEPS,0);
                        shape=include(shape,turn(moved,facing),bounds,cell);
                    }
                    shapes[facing.get2DDataValue()][part][step]=shape.optimize();
                }
            }
        }
        return shapes;
    }

    private static VoxelShape include(VoxelShape existing,AABB box,AABB bounds,BlockPos cell){
        return box.intersects(bounds)?Shapes.or(existing,Shapes.create(box.intersect(bounds)
            .move(-cell.getX(),-cell.getY(),-cell.getZ()))):existing;
    }

    private static AABB turn(AABB box,Direction facing){
        var min=NativeAdvancedChairGeometry.turn(new Vec3(box.minX,box.minY,box.minZ),facing);
        var max=NativeAdvancedChairGeometry.turn(new Vec3(box.maxX,box.maxY,box.maxZ),facing);
        return new AABB(min,max);
    }

    private static Source load(){
        String path="/assets/vandorlabs/cockpits/cockpit/assembly.json";
        try(var stream=NativeCockpitGeometry.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored Cockpit geometry");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Set<BlockPos> cells=new HashSet<>();
            for(var entry:json.getAsJsonArray("reserved_cells"))
                cells.add(BlockPos.containing(vector(entry.getAsJsonArray())));
            for(int part=0;part<CELLS;part++)
                if(!cells.remove(offset(part,Direction.NORTH)))throw new IllegalStateException("Changed Cockpit footprint");
            if(!cells.isEmpty())throw new IllegalStateException("Unmapped Cockpit reservation");
            var result=new ArrayList<AABB>();
            var bank=new ArrayList<AABB>();
            AABB button=null;
            for(var entry:json.getAsJsonArray("collision_boxes")){
                var box=entry.getAsJsonObject();
                var bounds=new AABB(vector(box.getAsJsonArray("min")),vector(box.getAsJsonArray("max")));
                String part=box.get("part").getAsString();
                if(part.equals("screen_bank"))bank.add(bounds);
                else result.add(bounds);
                if(part.equals("screen_lift_button"))button=bounds;
            }
            if(bank.isEmpty()||button==null)throw new IllegalStateException("Missing Cockpit screen-lift geometry");
            return new Source(vector(json.getAsJsonArray("seat_position_blocks")),List.copyOf(result),List.copyOf(bank),button);
        }catch(java.io.IOException failure){
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Vec3 vector(JsonArray value){
        return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble());
    }

    private NativeCockpitGeometry(){}
}
