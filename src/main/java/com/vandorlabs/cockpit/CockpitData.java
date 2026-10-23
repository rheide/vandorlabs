package com.vandorlabs.cockpit;

import com.google.gson.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.io.*;
import java.util.*;

/** Server-safe authored reservation, seating, collision and interactive planes. */
public final class CockpitData {
    public static final String[] IDS={"cockpit","chair_siderests","yoke_screen"};
    private static final Map<String,CockpitData> CACHE=new HashMap<>();
    public final String id;
    public final List<BlockPos> cells=new ArrayList<>();
    public final List<AxisAlignedBB> boxes=new ArrayList<>();
    public final Map<AxisAlignedBB,Integer> moving=new IdentityHashMap<>();
    public final List<Surface> surfaces=new ArrayList<>();
    public final List<String> controls=new ArrayList<>();
    public final Map<String,Vec3d> pivots=new LinkedHashMap<>();
    public final AxisAlignedBB bounds;
    public final Vec3d seat;
    public final double riderY;
    private static JsonObject json(String id,String file){
        String path="/assets/vandorlabs/cockpits/"+id+"/"+file+".json";
        try(InputStream in=CockpitData.class.getResourceAsStream(path)){
            if(in==null)throw new IllegalArgumentException("Missing cockpit metadata "+path);
            return new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static Vec3d vector(JsonArray a){return new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static AxisAlignedBB box(JsonArray a){return new AxisAlignedBB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
    private CockpitData(String id){
        this.id=id.equals("cockpit_single")?"cockpit":id;JsonObject assembly=json(id,"assembly");
        bounds=box(assembly.getAsJsonArray("bounds_blocks"));
        seat=assembly.get("seat_position_blocks").isJsonNull()?null:vector(assembly.getAsJsonArray("seat_position_blocks"));
        riderY=assembly.has("rider_entity_origin_blocks")?vector(assembly.getAsJsonArray("rider_entity_origin_blocks")).y:seat==null?0:seat.y-.4890625;
        for(JsonElement e:assembly.getAsJsonArray("reserved_cells")){Vec3d v=vector(e.getAsJsonArray());cells.add(new BlockPos(v));}
        Set<String> convex=new HashSet<>();
        if(assembly.has("collision_convex_solids"))for(JsonElement e:assembly.getAsJsonArray("collision_convex_solids")){
            JsonObject solid=e.getAsJsonObject();convex.add(solid.get("part").getAsString()+"/"+solid.get("mesh").getAsString());
            for(AxisAlignedBB b:CockpitCollision.slices(solid))addBox(b,solid.get("part").getAsString());
        }
        for(JsonElement e:assembly.getAsJsonArray("collision_boxes")){
            JsonObject b=e.getAsJsonObject();
            if(!convex.contains(b.get("part").getAsString()+"/"+b.get("box").getAsString())){
                Vec3d min=vector(b.getAsJsonArray("min")),max=vector(b.getAsJsonArray("max"));
                addBox(new AxisAlignedBB(min.x,min.y,min.z,max.x,max.y,max.z),b.get("part").getAsString());
            }
        }
        for(JsonElement e:json(id,"surfaces").getAsJsonArray("surfaces"))surfaces.add(new Surface(e.getAsJsonObject()));
        for(JsonElement e:json(id,"rig").getAsJsonArray("parts")){JsonObject part=e.getAsJsonObject();String name=part.get("id").getAsString();if(!part.has("interactive")||part.get("interactive").getAsBoolean()){controls.add(name);pivots.put(name,vector(part.getAsJsonArray("pivot")));}}
        if(cells.isEmpty() || boxes.isEmpty())throw new IllegalArgumentException("Empty cockpit "+id);
    }
    private void addBox(AxisAlignedBB box,String part){boxes.add(box);if(id.equals("cockpit")&&(part.equals("seat_assembly")||part.equals("screen_bank")||part.endsWith("_button")))moving.put(box,part.equals("screen_bank")?2:1);}
    public static synchronized CockpitData get(String id){
        if(!Arrays.asList(IDS).contains(id)&&!id.equals("cockpit_single"))throw new IllegalArgumentException("Unknown cockpit "+id);
        CockpitData found=CACHE.get(id);if(found==null){found=new CockpitData(id);CACHE.put(id,found);}return found;
    }
    /** Lower the upper chair by up to six pixels while its support stays on the floor. */
    public static double chairY(double y,int height){
        double join=7.4/16D,drop=(3-Math.max(0,Math.min(3,height)))/8D;
        return y>=join?y-drop:y*(join-drop)/join;
    }
    public static synchronized CockpitData get(String id,int height){
        CockpitData source=get(id);if(!id.equals("chair_siderests")||height==3)return source;
        int step=Math.max(0,Math.min(3,height));String key=id+"@"+step;
        CockpitData found=CACHE.get(key);if(found==null){found=new CockpitData(source,step);CACHE.put(key,found);}return found;
    }
    private CockpitData(CockpitData source,int height){
        id=source.id;cells.addAll(source.cells);controls.addAll(source.controls);
        bounds=lower(source.bounds,height);seat=new Vec3d(source.seat.x,chairY(source.seat.y,height),source.seat.z);riderY=source.riderY-(3-height)/8D;
        for(AxisAlignedBB b:source.boxes){AxisAlignedBB next=lower(b,height);boxes.add(next);if(source.moving.containsKey(b))moving.put(next,source.moving.get(b));}
        for(Surface surface:source.surfaces)surfaces.add(new Surface(surface,height));
        for(Map.Entry<String,Vec3d> part:source.pivots.entrySet()){Vec3d p=part.getValue();pivots.put(part.getKey(),new Vec3d(p.x,chairY(p.y,height),p.z));}
    }
    private static AxisAlignedBB lower(AxisAlignedBB b,int height){return new AxisAlignedBB(b.minX,chairY(b.minY,height),b.minZ,b.maxX,chairY(b.maxY,height),b.maxZ);}
    /** Rotate about the kit's fixed (0.5,0,0.5) anchor pivot. */
    public static Vec3d rotate(Vec3d p,EnumFacing facing){
        switch(facing){case EAST:return new Vec3d(1-p.z,p.y,p.x);case SOUTH:return new Vec3d(1-p.x,p.y,1-p.z);case WEST:return new Vec3d(p.z,p.y,1-p.x);default:return p;}
    }
    public static Vec3d unrotate(Vec3d p,EnumFacing facing){return rotate(p,facing==EnumFacing.EAST?EnumFacing.WEST:facing==EnumFacing.WEST?EnumFacing.EAST:facing);}
    public static AxisAlignedBB rotate(AxisAlignedBB b,EnumFacing facing){
        Vec3d a=rotate(new Vec3d(b.minX,b.minY,b.minZ),facing),c=rotate(new Vec3d(b.maxX,b.maxY,b.maxZ),facing);
        return new AxisAlignedBB(a.x,a.y,a.z,c.x,c.y,c.z);
    }
    public BlockPos cell(BlockPos root,BlockPos cell,EnumFacing facing){return root.add(new BlockPos(rotate(new Vec3d(cell).addVector(.5,0,.5),facing)));}
    public List<BlockPos> cells(BlockPos root,EnumFacing facing){List<BlockPos> result=new ArrayList<>();for(BlockPos cell:cells)result.add(cell(root,cell,facing));return result;}
    public static final class Surface {
        public final String id,role;
        public final Vec3d origin,u,v,normal;
        public final double offset;
        Surface(JsonObject e){id=e.get("id").getAsString();role=e.get("role").getAsString();origin=vector(e.getAsJsonArray("origin_blocks"));u=vector(e.getAsJsonArray("u_vector_blocks"));v=vector(e.getAsJsonArray("v_vector_blocks"));normal=vector(e.getAsJsonArray("normal"));offset=e.get("overlay_offset_blocks").getAsDouble();}
        Surface(Surface source,int height){id=source.id;role=source.role;origin=new Vec3d(source.origin.x,chairY(source.origin.y,height),source.origin.z);u=source.u;v=source.v;normal=source.normal;offset=source.offset;}
        public Vec3d point(double x,double y){return origin.add(u.scale(x)).add(v.scale(y)).add(normal.scale(offset));}
        /** Returns normalized artwork coordinates for a front-side ray hit. */
        public double[] hit(Vec3d start,Vec3d end){
            Vec3d delta=end.subtract(start);double dot=delta.dotProduct(normal);if(dot>=-1e-9)return null;
            double t=origin.subtract(start).dotProduct(normal)/dot;if(t<0 || t>1)return null;
            Vec3d p=start.add(delta.scale(t)).subtract(origin);double x=p.dotProduct(u)/u.lengthSquared(),y=p.dotProduct(v)/v.lengthSquared();
            return x>=0 && x<=1 && y>=0 && y<=1?new double[]{x,y,t}:null;
        }
    }
}
